package com.cooper.binge.companion.seerr.ui.discover.request

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.request.RequestAccess
import com.cooper.binge.companion.seerr.discover.request.RequestChoices
import com.cooper.binge.companion.seerr.discover.request.RequestDestination
import com.cooper.binge.companion.seerr.discover.request.RequestQuota
import com.cooper.binge.companion.seerr.discover.request.RequestResult
import com.cooper.binge.companion.seerr.discover.request.RequestServer
import com.cooper.binge.companion.seerr.discover.request.RequestSheetRepository
import com.cooper.binge.companion.seerr.discover.request.RequestSubmission
import com.cooper.binge.companion.seerr.discover.request.SeasonChoices
import com.cooper.binge.companion.seerr.discover.request.SeasonSelection
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.serverMessage
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * The title a request sheet is for. [seasons] is empty for a movie, and for a series means the whole show. [standardOpen] and
 * [fourKOpen] say which versions the server holds nothing of, so the sheet never offers one that is already requested.
 * [seasonChoices] is a show's seasons per version, and [partialRequests] is the server's setting for sending only some.
 * [posterUrl] and [meta] ("2023 · 2h 1m") head the sheet.
 */
data class RequestSheetTarget(
    val kind: TitleKind,
    val tmdbId: Int,
    val title: String,
    val seasons: List<Int> = emptyList(),
    val standardOpen: Boolean = true,
    val fourKOpen: Boolean = true,
    val seasonChoices: SeasonChoices? = null,
    val partialRequests: Boolean = true,
    val posterUrl: String? = null,
    val meta: String? = null,
)

/** Why a send failed, in the terms the dialog explains. [detail] is the server's own sentence, where it gave one. */
data class RequestProblem(
    val cause: RequestProblemCause,
    val detail: String?,
)

enum class RequestProblemCause { Quota, NotAllowed, Unreachable, Other }

/** The quota read. [Known] holds null where the server does not meter this kind of title, which is no row at all. */
sealed interface QuotaState {
    data object Loading : QuotaState

    data class Known(
        val quota: RequestQuota?,
    ) : QuotaState

    data object Failed : QuotaState
}

/** The servers the user may choose between, read once and kept. */
sealed interface ServersState {
    data object Loading : ServersState

    data class Ready(
        val servers: List<RequestServer>,
    ) : ServersState

    data object Failed : ServersState
}

/** The chosen server's profiles and folders. While they are not [Ready] the sheet will not send what it cannot show. */
sealed interface ChoicesState {
    data object Loading : ChoicesState

    data class Ready(
        val choices: RequestChoices,
    ) : ChoicesState

    data object Failed : ChoicesState
}

/** Where the request goes when the user opened Advanced options and chose a destination. */
data class DestinationPick(
    val serverId: Int,
    val profileId: Int?,
    val rootFolder: String?,
    val choices: ChoicesState,
) {
    /** The lists the chosen server gave, with the current pick kept where it still offers it, else its first. */
    fun withChoices(loaded: RequestChoices): DestinationPick =
        copy(
            choices = ChoicesState.Ready(loaded),
            profileId = loaded.profiles.firstOrNull { it.id == profileId }?.id ?: loaded.profiles.firstOrNull()?.id ?: profileId,
            rootFolder = loaded.rootFolders.firstOrNull { it == rootFolder } ?: loaded.rootFolders.firstOrNull() ?: rootFolder,
        )
}

/**
 * [problem] is a refusal with a cause and a next step, shown in a dialog with one OK. [failure] is a send that did not get an
 * answer: the footer button turns red for a few seconds ([footerFailed]), and a tap in that time opens [failureDialog] to retry.
 * [advancedExpanded] is whether Advanced options is open; closed, the request is a plain one.
 */
sealed interface RequestSheetUiState {
    data object Loading : RequestSheetUiState

    data class Error(
        val error: SeerrError,
    ) : RequestSheetUiState

    data class Ready(
        val target: RequestSheetTarget,
        val access: RequestAccess,
        val quota: QuotaState = QuotaState.Loading,
        val servers: ServersState = ServersState.Loading,
        val use4k: Boolean = false,
        val advancedExpanded: Boolean = false,
        val pick: DestinationPick? = null,
        val submitting: Boolean = false,
        val problem: RequestProblem? = null,
        val failure: RequestProblem? = null,
        val footerFailed: Boolean = false,
        val failureDialog: Boolean = false,
        val pickedSeasons: Set<Int> = emptySet(),
    ) : RequestSheetUiState {
        val knownQuota: RequestQuota? get() = (quota as? QuotaState.Known)?.quota

        /** A show's seasons of the chosen version, with what the user ticked; null for a film. */
        val seasons: SeasonSelection?
            get() =
                target.seasonChoices?.let {
                    SeasonSelection(it.of(use4k), target.partialRequests, pickedSeasons, limit = knownQuota?.remaining)
                }

        /** The servers of the chosen version: 4K is a property of a server, so the 4K switch narrows this list. */
        val servers4k: List<RequestServer> get() = (servers as? ServersState.Ready)?.servers.orEmpty().filter { it.is4k == use4k }

        /** Advanced options is offered to a user who may choose, unless the servers read and hold none of this version. */
        val showAdvanced: Boolean get() = access.mayChooseDestination && (servers !is ServersState.Ready || servers4k.isNotEmpty())

        val quotaSpent: Boolean get() = knownQuota?.spent == true

        /** The 4K switch moves only while both versions are open to request; with one left, that one is the choice. */
        val canToggle4k: Boolean get() = access.may4k && target.standardOpen && target.fourKOpen && !submitting

        /** Open and not yet showing real lists: sending now would send defaults the user never saw. */
        val advancedBlocking: Boolean
            get() = advancedExpanded && showAdvanced && (servers !is ServersState.Ready || pick?.choices !is ChoicesState.Ready)

        val canSubmit: Boolean get() = !submitting && !quotaSpent && !advancedBlocking && (seasons?.canSubmit ?: true)
    }
}

/** [Done] is any answer that leaves nothing more to do: a request made, one already there, or nothing left to ask for. */
sealed interface RequestSheetEvent {
    data class Done(
        val result: RequestResult,
    ) : RequestSheetEvent
}

private const val FOOTER_FAILURE_MILLIS = 4_000L

/** Which of the sheet's reads to try again. */
enum class RequestRead { Quota, Servers, Choices }

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

        private val store = RequestSheetStore()
        private val reads = RequestSheetReads(repository, target, store, viewModelScope)

        val uiState: StateFlow<RequestSheetUiState> = store.flow

        private val eventFlow = MutableSharedFlow<RequestSheetEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<RequestSheetEvent> = eventFlow.asSharedFlow()

        init {
            load()
        }

        fun load() {
            store.set(RequestSheetUiState.Loading)
            viewModelScope.launch {
                attempt { repository.access(target.kind) }.fold(
                    onSuccess = { access ->
                        val use4k = access.may4k && !target.standardOpen && target.fourKOpen
                        val servers = if (access.mayChooseDestination) ServersState.Loading else ServersState.Ready(emptyList())
                        store.set(RequestSheetUiState.Ready(target, access, servers = servers, use4k = use4k))
                        reads.quota()
                        if (access.mayChooseDestination) reads.servers()
                    },
                    onFailure = { store.set(RequestSheetUiState.Error(it.toSeerrError())) },
                )
            }
        }

        fun retry(read: RequestRead) =
            when (read) {
                RequestRead.Quota -> {
                    store.update { it.copy(quota = QuotaState.Loading) }
                    reads.quota()
                }
                RequestRead.Servers -> {
                    store.update { it.copy(servers = ServersState.Loading) }
                    reads.servers()
                }
                RequestRead.Choices ->
                    store.ready()?.pick?.serverId?.let { serverId ->
                        store.updatePick { it.copy(choices = ChoicesState.Loading) }
                        reads.choices(serverId)
                    }
            }

        fun setUse4k(use4k: Boolean) {
            val current = store.ready() ?: return
            if (current.use4k == use4k || !current.canToggle4k) return
            val pick = (current.servers as? ServersState.Ready)?.servers.orEmpty().pickFor(use4k)
            store.set(current.copy(use4k = use4k, pick = pick, pickedSeasons = emptySet()))
            pick?.let { reads.choices(it.serverId) }
        }

        fun setAdvancedExpanded(expanded: Boolean) = store.update { it.copy(advancedExpanded = expanded) }

        /** Flips one season, or every open one for `null`. */
        fun toggleSeasons(number: Int?) =
            store.update { current ->
                current.seasons?.let { current.copy(pickedSeasons = (number?.let(it::toggle) ?: it.toggleAll()).picked) } ?: current
            }

        fun selectServer(id: Int) {
            val current = store.ready() ?: return
            val server = current.servers4k.firstOrNull { it.id == id } ?: return
            if (current.pick?.serverId == id) return
            store.set(current.copy(pick = DestinationPick(server.id, server.profileId, server.rootFolder, ChoicesState.Loading)))
            reads.choices(id)
        }

        fun selectProfile(id: Int) = store.updatePick { it.copy(profileId = id) }

        fun selectRootFolder(path: String) = store.updatePick { it.copy(rootFolder = path) }

        fun dismissProblem() = store.update { it.copy(problem = null) }

        /** A tap on the footer while it shows the failure opens the dialog that explains it and offers Retry. */
        fun openFailure() = store.update { if (it.failure != null) it.copy(failureDialog = true) else it }

        fun dismissFailure() = store.update { it.copy(failureDialog = false, failure = null, footerFailed = false) }

        private var footerTimer: Job? = null

        fun submit() {
            val current = store.ready() ?: return
            if (!current.canSubmit) return
            footerTimer?.cancel()
            store.set(current.copy(submitting = true, problem = null, failure = null, footerFailed = false, failureDialog = false))
            val destination = current.pick?.takeIf { current.advancedExpanded && current.showAdvanced }
            val submission =
                RequestSubmission(
                    kind = target.kind,
                    tmdbId = target.tmdbId,
                    seasons = current.seasons?.requestSeasons ?: target.seasons,
                    is4k = current.use4k,
                    destination = destination?.let { RequestDestination(it.serverId, it.profileId, it.rootFolder) },
                )
            viewModelScope.launch {
                attempt { repository.submit(submission) }.fold(
                    onSuccess = { result ->
                        eventFlow.tryEmit(RequestSheetEvent.Done(result))
                        // This ViewModel outlives the sheet, so the next opening must not start from the old send.
                        load()
                    },
                    onFailure = { error -> onSendFailed(error.toProblem()) },
                )
            }
        }

        private fun onSendFailed(problem: RequestProblem) {
            val answered = problem.cause == RequestProblemCause.Quota || problem.cause == RequestProblemCause.NotAllowed
            if (answered) {
                store.update { it.copy(submitting = false, problem = problem) }
                return
            }
            store.update { it.copy(submitting = false, failure = problem, footerFailed = true) }
            footerTimer?.cancel()
            footerTimer =
                viewModelScope.launch {
                    delay(FOOTER_FAILURE_MILLIS)
                    store.update { if (it.failure == problem && !it.failureDialog) it.copy(footerFailed = false) else it }
                }
        }
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
