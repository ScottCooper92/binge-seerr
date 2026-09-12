package io.github.scottcooper92.binge.seerr.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.integration.contracts.v1.MediaId
import com.binge.integration.contracts.v1.MediaType
import com.binge.integration.sdk.AdvancedRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.NotConnectedException
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerDetailsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerDto
import io.github.scottcooper92.binge.seerr.seerr.forRequest
import io.github.scottcooper92.binge.seerr.seerr.preferred
import io.github.scottcooper92.binge.seerr.seerr.seerrMediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** Why the picker cannot open, or why a submit failed, as the screen shows it. */
enum class AdvancedRequestError { NotConnected, NoServers, Rejected, Unreachable, Unknown }

/** One entry in a picker: the server's own id, and the name the user sees. */
data class Choice(
    val id: Int,
    val label: String,
)

/** What the hand-off screen shows: the choices loading, the form, why it cannot open, or done. */
sealed interface AdvancedRequestUiState {
    data object Loading : AdvancedRequestUiState

    data class Ready(
        val servers: List<Choice>,
        val serverId: Int,
        val profiles: List<Choice>,
        val profileId: Int?,
        val rootFolders: List<String>,
        val rootFolder: String?,
        val isLoadingChoices: Boolean,
        val isSubmitting: Boolean,
        val error: AdvancedRequestError?,
    ) : AdvancedRequestUiState {
        val canSubmit: Boolean get() = !isLoadingChoices && !isSubmitting
    }

    data class Failed(
        val error: AdvancedRequestError,
    ) : AdvancedRequestUiState

    data object Submitted : AdvancedRequestUiState
}

/**
 * The advanced-options hand-off for one title: the servers a request of its shape may go to, the
 * chosen server's profiles and folders, and the submit with those overrides. The host never sees
 * any of it — it handed over a media type and a TMDB id, and reads status back when this finishes.
 * The picker opens on what a plain request would have used, so submitting untouched is that.
 */
@HiltViewModel(assistedFactory = AdvancedRequestViewModel.Factory::class)
class AdvancedRequestViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @Assisted private val request: AdvancedRequest,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<AdvancedRequestUiState>(AdvancedRequestUiState.Loading)
        val uiState: StateFlow<AdvancedRequestUiState> = _uiState.asStateFlow()

        private val isTv = request.mediaType == MediaType.MEDIA_TYPE_TV
        private var servers: List<SeerrServerDto> = emptyList()

        init {
            viewModelScope.launch { load() }
        }

        private suspend fun load() {
            runCatching { connection.api().servers().forRequest(request.is4k) }
                .onFailure { _uiState.value = AdvancedRequestUiState.Failed(it.toAdvancedRequestError()) }
                .onSuccess { loaded ->
                    servers = loaded
                    val server = loaded.preferred()
                    if (server == null) {
                        _uiState.value = AdvancedRequestUiState.Failed(AdvancedRequestError.NoServers)
                    } else {
                        _uiState.value =
                            AdvancedRequestUiState.Ready(
                                servers = loaded.map { Choice(it.id, it.name) },
                                serverId = server.id,
                                profiles = emptyList(),
                                profileId = server.activeProfileId,
                                rootFolders = emptyList(),
                                rootFolder = server.activeDirectory,
                                isLoadingChoices = true,
                                isSubmitting = false,
                                error = null,
                            )
                        loadChoices(server)
                    }
                }
        }

        fun selectServer(id: Int) {
            val ready = ready() ?: return
            val server = servers.firstOrNull { it.id == id } ?: return
            if (server.id == ready.serverId) return
            _uiState.value =
                ready.copy(
                    serverId = server.id,
                    profiles = emptyList(),
                    profileId = server.activeProfileId,
                    rootFolders = emptyList(),
                    rootFolder = server.activeDirectory,
                    isLoadingChoices = true,
                    error = null,
                )
            viewModelScope.launch { loadChoices(server) }
        }

        fun selectProfile(id: Int) = updateReady { it.copy(profileId = id) }

        fun selectRootFolder(path: String) = updateReady { it.copy(rootFolder = path) }

        fun submit() {
            val ready = ready() ?: return
            if (!ready.canSubmit) return
            _uiState.value = ready.copy(isSubmitting = true, error = null)
            viewModelScope.launch {
                runCatching {
                    val response = connection.api().requestMedia(ready.toBody())
                    // A 409 is a title the server already tracks: nothing to change, and nothing to tell.
                    if (!response.isSuccessful && response.code() != HTTP_CONFLICT) throw HttpException(response)
                }.onSuccess { _uiState.value = AdvancedRequestUiState.Submitted }
                    .onFailure { failure -> updateReady { it.copy(isSubmitting = false, error = failure.toAdvancedRequestError()) } }
            }
        }

        /** Fills the chosen server's choices in, unless the user has moved to another server meanwhile. */
        private suspend fun loadChoices(server: SeerrServerDto) {
            runCatching { connection.api().server(server.id) }
                .onSuccess { details -> updateReady { if (it.serverId == server.id) it.withChoices(details) else it } }
                .onFailure { failure ->
                    updateReady {
                        if (it.serverId ==
                            server.id
                        ) {
                            it.copy(isLoadingChoices = false, error = failure.toAdvancedRequestError())
                        } else {
                            it
                        }
                    }
                }
        }

        private fun AdvancedRequestUiState.Ready.withChoices(details: SeerrServerDetailsDto): AdvancedRequestUiState.Ready =
            copy(
                profiles = details.profiles.map { Choice(it.id, it.name) },
                profileId = profileId?.takeIf { id -> details.profiles.any { it.id == id } } ?: details.profiles.firstOrNull()?.id,
                rootFolders = details.rootFolders.map { it.path },
                rootFolder =
                    rootFolder?.takeIf { path -> details.rootFolders.any { it.path == path } } ?: details.rootFolders.firstOrNull()?.path,
                isLoadingChoices = false,
            )

        private fun AdvancedRequestUiState.Ready.toBody(): SeerrRequestBody =
            SeerrRequestBody(
                mediaType =
                    MediaId
                        .newBuilder()
                        .setMediaType(request.mediaType)
                        .setTmdbId(request.tmdbId)
                        .build()
                        .seerrMediaType(),
                mediaId = request.tmdbId,
                seasons = request.seasonNumbers.takeIf { it.isNotEmpty() },
                is4k = request.is4k,
                serverId = serverId,
                profileId = profileId,
                rootFolder = rootFolder,
            )

        private suspend fun SeerrApi.servers(): List<SeerrServerDto> = if (isTv) sonarrServers() else radarrServers()

        private suspend fun SeerrApi.server(id: Int): SeerrServerDetailsDto = if (isTv) sonarrServer(id) else radarrServer(id)

        private fun ready(): AdvancedRequestUiState.Ready? = _uiState.value as? AdvancedRequestUiState.Ready

        private fun updateReady(transform: (AdvancedRequestUiState.Ready) -> AdvancedRequestUiState.Ready) {
            _uiState.update { state -> (state as? AdvancedRequestUiState.Ready)?.let(transform) ?: state }
        }

        private companion object {
            const val HTTP_CONFLICT = 409
        }

        /** The title is the Intent's, not the graph's, so it is assisted in at creation. */
        @AssistedFactory
        interface Factory {
            fun create(request: AdvancedRequest): AdvancedRequestViewModel
        }
    }

/** No saved server is its own case; a 401/403 is the credentials or the permission; the rest is the server or the network. */
private fun Throwable.toAdvancedRequestError(): AdvancedRequestError =
    when (this) {
        is NotConnectedException -> AdvancedRequestError.NotConnected
        is HttpException ->
            if (code() == HTTP_UNAUTHORIZED ||
                code() == HTTP_FORBIDDEN
            ) {
                AdvancedRequestError.Rejected
            } else {
                AdvancedRequestError.Unknown
            }
        is IOException -> AdvancedRequestError.Unreachable
        else -> AdvancedRequestError.Unknown
    }

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
