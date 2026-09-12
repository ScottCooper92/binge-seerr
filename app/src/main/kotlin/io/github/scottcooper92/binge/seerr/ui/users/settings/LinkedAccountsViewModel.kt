package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.PlexPinExpiredException
import io.github.scottcooper92.binge.seerr.auth.PlexPinFlow
import io.github.scottcooper92.binge.seerr.auth.QuickConnectExpiredException
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrLinkJellyfinBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrLinkPlexBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrLinkQuickConnectBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaServer
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.LinkFlow
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The linked accounts page: the plex.tv account and the media server's, each linked or not. A
 * link that finishes elsewhere (Plex in the browser, Quick Connect on Jellyfin) is the same
 * [LinkFlow] setup shows, but what it ends in is a link to the signed-in user, never a sign-in.
 */
@HiltViewModel(assistedFactory = LinkedAccountsViewModel.Factory::class)
class LinkedAccountsViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        private val plex: PlexPinFlow,
        @Assisted private val userId: Int,
    ) : ViewModel() {
        private val state = MutableStateFlow<LinkedAccountsUiState>(LinkedAccountsUiState.Loading)
        val uiState: StateFlow<LinkedAccountsUiState> = state.asStateFlow()

        private val eventFlow = MutableSharedFlow<LinkedAccountsEvent>(extraBufferCapacity = 1)
        val events: SharedFlow<LinkedAccountsEvent> = eventFlow.asSharedFlow()

        private var linkJob: Job? = null

        init {
            reload()
        }

        fun reload() {
            state.value = LinkedAccountsUiState.Loading
            viewModelScope.launch {
                runCatching { load() }
                    .onSuccess { state.value = it }
                    .onFailure { state.value = LinkedAccountsUiState.Error(it.toSeerrError()) }
            }
        }

        fun linkPlex() =
            startLink { api ->
                val pin = plex.start()
                showLink(LinkFlow.Plex(pin.code, pin.authUrl, launchPending = true))
                api.linkPlexAccount(userId, SeerrLinkPlexBody(plex.awaitToken(pin)))
            }

        fun linkQuickConnect() =
            startLink { api ->
                val baseUrl = connection.current().baseUrl
                val session = connection.startQuickConnect(baseUrl).getOrThrow()
                showLink(LinkFlow.QuickConnect(session.code))
                connection.awaitQuickConnect(baseUrl, session).getOrThrow()
                api.linkJellyfinQuickConnect(userId, SeerrLinkQuickConnectBody(session.secret))
            }

        fun linkJellyfin(
            username: String,
            password: String,
        ) = startLink { api -> api.linkJellyfinAccount(userId, SeerrLinkJellyfinBody(username.trim(), password)) }

        fun unlinkPlex() = run(LinkedAccountsEvent.Unlinked) { api -> api.unlinkPlexAccount(userId) }

        fun unlinkMediaServer() = run(LinkedAccountsEvent.Unlinked) { api -> api.unlinkJellyfinAccount(userId) }

        fun plexLaunched() =
            state.update { current ->
                val ready = current as? LinkedAccountsUiState.Ready ?: return@update current
                val link = ready.link as? LinkFlow.Plex ?: return@update current
                ready.copy(link = link.copy(launchPending = false))
            }

        fun cancelLink() {
            linkJob?.cancel()
            linkJob = null
            state.update { current -> (current as? LinkedAccountsUiState.Ready)?.copy(busy = false, link = null) ?: current }
        }

        private fun startLink(block: suspend (SeerrApi) -> Unit) {
            linkJob = run(LinkedAccountsEvent.Linked, block)
        }

        /** One server action while the page is idle; success re-reads the page, since the server's record is the truth. */
        private fun run(
            done: LinkedAccountsEvent,
            block: suspend (SeerrApi) -> Unit,
        ): Job? {
            val ready = state.value as? LinkedAccountsUiState.Ready ?: return null
            if (ready.busy) return null
            state.value = ready.copy(busy = true)
            return viewModelScope.launch {
                val outcome =
                    try {
                        Result.success(block(connection.api()))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                linkJob = null
                outcome
                    .onSuccess {
                        runCatching { load() }.onSuccess { state.value = it }
                        eventFlow.emit(done)
                    }.onFailure { failure ->
                        state.update { current -> (current as? LinkedAccountsUiState.Ready)?.copy(busy = false, link = null) ?: current }
                        eventFlow.emit(failure.toEvent())
                    }
            }
        }

        private fun showLink(link: LinkFlow) =
            state.update { current -> (current as? LinkedAccountsUiState.Ready)?.copy(busy = false, link = link) ?: current }

        private suspend fun load(): LinkedAccountsUiState.Ready {
            val profile = connection.profile()
            val user = connection.api().user(userId)
            val mediaServerOrigin =
                when (profile.mediaServer) {
                    SeerrMediaServer.Jellyfin -> UserOrigin.Jellyfin
                    SeerrMediaServer.Emby -> UserOrigin.Emby
                    SeerrMediaServer.Plex, SeerrMediaServer.NotConfigured -> null
                }
            return LinkedAccountsUiState.Ready(
                plex = LinkedAccount(UserOrigin.Plex, linked = user.plexId != null, linkedAs = user.plexUsername.orNull()),
                mediaServer =
                    mediaServerOrigin?.let {
                        LinkedAccount(it, linked = !user.jellyfinUserId.isNullOrBlank(), linkedAs = user.jellyfinUsername.orNull())
                    },
                canQuickConnect = profile.hasQuickConnect && mediaServerOrigin == UserOrigin.Jellyfin,
            )
        }

        @AssistedFactory
        interface Factory {
            fun create(userId: Int): LinkedAccountsViewModel
        }
    }

private fun Throwable.toEvent(): LinkedAccountsEvent =
    when (this) {
        is PlexPinExpiredException, is QuickConnectExpiredException -> LinkedAccountsEvent.LinkExpired
        else -> LinkedAccountsEvent.Failed(toSeerrError())
    }

private fun String?.orNull(): String? = this?.takeIf { it.isNotBlank() }
