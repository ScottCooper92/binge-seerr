package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestChoices
import com.cooper.binge.companion.seerr.discover.request.RequestDestination
import com.cooper.binge.companion.seerr.discover.request.RequestOptions
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.request.RequestServer
import com.cooper.binge.companion.seerr.discover.request.RequestSheetRepository
import com.cooper.binge.companion.seerr.discover.request.RequestSubmission
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.serverMessage
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The title a request sheet is for. [seasons] is empty for a movie, and for a series means the whole show. */
data class RequestSheetTarget(
    val kind: TitleKind,
    val tmdbId: Int,
    val title: String,
    val seasons: List<Int> = emptyList(),
)

/** Why a send failed, in the terms the dialog explains. [detail] is the server's own sentence, where it gave one. */
data class RequestProblem(
    val cause: RequestProblemCause,
    val detail: String?,
)

enum class RequestProblemCause { Quota, NotAllowed, Unreachable, Other }

/** Where the request goes when the user chose a destination. [choices] is null while the server's own lists are being read. */
data class DestinationPick(
    val serverId: Int,
    val profileId: Int?,
    val rootFolder: String?,
    val choices: RequestChoices?,
) {
    /** The lists the chosen server gave, with the current pick kept where it still offers it, else its first. */
    fun withChoices(loaded: RequestChoices): DestinationPick =
        copy(
            choices = loaded,
            profileId = loaded.profiles.firstOrNull { it.id == profileId }?.id ?: loaded.profiles.firstOrNull()?.id ?: profileId,
            rootFolder = loaded.rootFolders.firstOrNull { it == rootFolder } ?: loaded.rootFolders.firstOrNull() ?: rootFolder,
        )
}

sealed interface RequestSheetUiState {
    data object Loading : RequestSheetUiState

    data class Error(
        val error: SeerrError,
    ) : RequestSheetUiState

    data class Ready(
        val target: RequestSheetTarget,
        val options: RequestOptions,
        val use4k: Boolean = false,
        val pick: DestinationPick? = null,
        val submitting: Boolean = false,
        val problem: RequestProblem? = null,
    ) : RequestSheetUiState {
        /** The servers of the chosen version: 4K is a property of a server, so the 4K switch narrows this list. */
        val servers: List<RequestServer> get() = options.servers.filter { it.is4k == use4k }

        val quotaSpent: Boolean get() = options.quota?.spent == true

        /** A pick whose lists have not read yet still submits: the server fills an empty axis with its own default. */
        val canSubmit: Boolean get() = !submitting && !quotaSpent
    }
}

sealed interface RequestSheetEvent {
    data class Done(
        val result: RequestResult,
    ) : RequestSheetEvent
}

@HiltViewModel(assistedFactory = RequestSheetViewModel.Factory::class)
class RequestSheetViewModel
    @AssistedInject
    constructor(
        private val repository: RequestSheetRepository,
        @Assisted private val target: RequestSheetTarget,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(target: RequestSheetTarget): RequestSheetViewModel
        }

        private val state = MutableStateFlow<RequestSheetUiState>(RequestSheetUiState.Loading)
        val uiState: StateFlow<RequestSheetUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<RequestSheetEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<RequestSheetEvent> = eventFlow.asSharedFlow()

        init {
            load()
        }

        fun load() {
            state.value = RequestSheetUiState.Loading
            viewModelScope.launch {
                attempt { repository.options(target.kind) }.fold(
                    onSuccess = { options ->
                        val pick = options.servers.pickFor(use4k = false)
                        state.value = RequestSheetUiState.Ready(target, options, pick = pick)
                        pick?.let { loadChoices(it.serverId) }
                    },
                    onFailure = { state.value = RequestSheetUiState.Error(it.toSeerrError()) },
                )
            }
        }

        fun setUse4k(use4k: Boolean) {
            val current = ready() ?: return
            if (current.use4k == use4k || current.submitting) return
            val pick = current.options.servers.pickFor(use4k)
            state.value = current.copy(use4k = use4k, pick = pick)
            pick?.let { loadChoices(it.serverId) }
        }

        fun selectServer(id: Int) {
            val current = ready() ?: return
            val server = current.servers.firstOrNull { it.id == id } ?: return
            if (current.pick?.serverId == id) return
            state.value = current.copy(pick = DestinationPick(server.id, server.profileId, server.rootFolder, choices = null))
            loadChoices(id)
        }

        fun selectProfile(id: Int) = updatePick { it.copy(profileId = id) }

        fun selectRootFolder(path: String) = updatePick { it.copy(rootFolder = path) }

        fun dismissProblem() = updateReady { it.copy(problem = null) }

        fun submit() {
            val current = ready() ?: return
            if (!current.canSubmit) return
            state.value = current.copy(submitting = true, problem = null)
            val submission =
                RequestSubmission(
                    kind = target.kind,
                    tmdbId = target.tmdbId,
                    seasons = target.seasons,
                    is4k = current.use4k,
                    destination = current.pick?.let { RequestDestination(it.serverId, it.profileId, it.rootFolder) },
                )
            viewModelScope.launch {
                attempt { repository.submit(submission) }.fold(
                    onSuccess = { result -> eventFlow.tryEmit(RequestSheetEvent.Done(result)) },
                    onFailure = { error -> updateReady { it.copy(submitting = false, problem = error.toProblem()) } },
                )
            }
        }

        private fun loadChoices(serverId: Int) {
            viewModelScope.launch {
                // A server whose lists will not read keeps its own defaults, which the server accepts, and the pickers stay shut.
                val loaded = attempt { repository.choices(target.kind, serverId) }.getOrNull() ?: return@launch
                updatePick { pick -> if (pick.serverId == serverId) pick.withChoices(loaded) else pick }
            }
        }

        private fun ready(): RequestSheetUiState.Ready? = state.value as? RequestSheetUiState.Ready

        private fun updateReady(transform: (RequestSheetUiState.Ready) -> RequestSheetUiState.Ready) =
            state.update { (it as? RequestSheetUiState.Ready)?.let(transform) ?: it }

        private fun updatePick(transform: (DestinationPick) -> DestinationPick) =
            updateReady { current -> current.copy(pick = current.pick?.let(transform)) }
    }

/** The preselected server of a version: the server's default, else its first; null where the user has none to choose. */
private fun List<RequestServer>.pickFor(use4k: Boolean): DestinationPick? {
    val ofVersion = filter { it.is4k == use4k }
    val server = ofVersion.firstOrNull { it.isDefault } ?: ofVersion.firstOrNull() ?: return null
    return DestinationPick(server.id, server.profileId, server.rootFolder, choices = null)
}

private fun Throwable.toProblem(): RequestProblem {
    val cause =
        when (toSeerrError()) {
            SeerrError.Quota -> RequestProblemCause.Quota
            SeerrError.Forbidden, SeerrError.Unauthorized -> RequestProblemCause.NotAllowed
            SeerrError.Unreachable, SeerrError.LocalNetworkDenied, SeerrError.NotConnected -> RequestProblemCause.Unreachable
            else -> RequestProblemCause.Other
        }
    return RequestProblem(cause, serverMessage())
}
