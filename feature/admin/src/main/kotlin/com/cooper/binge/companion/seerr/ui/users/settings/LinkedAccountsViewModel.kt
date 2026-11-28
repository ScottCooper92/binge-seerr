package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.PlexPinExpiredException
import com.cooper.binge.companion.seerr.auth.PlexPinFlow
import com.cooper.binge.companion.seerr.auth.QuickConnectExpiredException
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrLinkJellyfinBody
import com.cooper.binge.companion.seerr.seerr.SeerrLinkPlexBody
import com.cooper.binge.companion.seerr.seerr.SeerrLinkQuickConnectBody
import com.cooper.binge.companion.seerr.seerr.SeerrMediaServer
import com.cooper.binge.companion.seerr.seerr.rejectionDetail
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.binge.companion.seerr.telemetry.operationFailed
import com.cooper.binge.companion.seerr.ui.LinkFlow
import com.cooper.binge.companion.seerr.ui.users.UserOrigin
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.NoOpAnalytics
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
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
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        @Assisted private val userId: Int,
        private val analytics: Analytics = NoOpAnalytics,
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
            viewModelScope.launch(dispatcher) {
                attempt { load() }
                    .onSuccess { state.value = it }
                    .onFailure { state.value = LinkedAccountsUiState.Error(it.toSeerrError()) }
            }
        }

        fun linkPlex() =
            startLink(origin = "plex") { api ->
                val pin = plex.start()
                showLink(LinkFlow.Plex(pin.code, pin.authUrl, launchPending = true))
                api.linkPlexAccount(userId, SeerrLinkPlexBody(plex.awaitToken(pin)))
            }

        fun linkQuickConnect() =
            startLink(origin = "media_server") { api ->
                val baseUrl = connection.current().baseUrl
                val session = connection.startQuickConnect(baseUrl).getOrThrow()
                showLink(LinkFlow.QuickConnect(session.code))
                connection.awaitQuickConnect(baseUrl, session).getOrThrow()
                api.linkJellyfinQuickConnect(userId, SeerrLinkQuickConnectBody(session.secret))
            }

        fun linkJellyfin(
            username: String,
            password: String,
        ) = startLink(origin = "media_server") { api -> api.linkJellyfinAccount(userId, SeerrLinkJellyfinBody(username.trim(), password)) }

        fun unlinkPlex() = run(LinkedAccountsEvent.Unlinked, "plex", ::unlinkFailure) { api -> api.unlinkPlexAccount(userId) }

        fun unlinkMediaServer() =
            run(LinkedAccountsEvent.Unlinked, "media_server", ::unlinkFailure) { api -> api.unlinkJellyfinAccount(userId) }

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

        private fun startLink(
            origin: String,
            block: suspend (SeerrApi) -> Unit,
        ) {
            linkJob = run(LinkedAccountsEvent.Linked, origin, block = block)
        }

        /** One server action while the page is idle; success re-reads the page, since the server's record is the truth. */
        private fun run(
            done: LinkedAccountsEvent,
            origin: String,
            failed: (Throwable) -> LinkedAccountsEvent = Throwable::toEvent,
            block: suspend (SeerrApi) -> Unit,
        ): Job? {
            val ready = state.value as? LinkedAccountsUiState.Ready ?: return null
            if (ready.busy) return null
            state.value = ready.copy(busy = true)
            return viewModelScope.launch(dispatcher) {
                val outcome = attempt { block(connection.api()) }
                linkJob = null
                outcome
                    .onSuccess {
                        analytics.event(
                            AnalyticsEvents.ACCOUNT_LINK_CHANGED,
                            mapOf(
                                AnalyticsEvents.PARAM_ACTION to if (done == LinkedAccountsEvent.Linked) "linked" else "unlinked",
                                AnalyticsEvents.PARAM_ORIGIN to origin,
                            ),
                        )
                        // The write went through, so a failed re-read must not leave the page busy or the code sheet up.
                        attempt { load() }
                            .onSuccess { state.value = it }
                            .onFailure { clearBusy() }
                        eventFlow.emit(done)
                    }.onFailure { failure ->
                        analytics.operationFailed("linked_account", failure, connection)
                        clearBusy()
                        eventFlow.emit(failed(failure))
                    }
            }
        }

        /**
         * An unlink the server refused with a reason is [LinkedAccountsEvent.UnlinkRefused]; anything else reads as any other
         * failure. The server answers the primary administrator (user 1) before it checks credentials, so setting them is
         * no way out there.
         */
        private fun unlinkFailure(failure: Throwable): LinkedAccountsEvent =
            failure.rejectionDetail()?.let { LinkedAccountsEvent.UnlinkRefused(it, offerCredentialsStep = userId != PRIMARY_ADMIN_ID) }
                ?: failure.toEvent()

        private fun clearBusy() =
            state.update { current -> (current as? LinkedAccountsUiState.Ready)?.copy(busy = false, link = null) ?: current }

        private fun showLink(link: LinkFlow) =
            state.update { current -> (current as? LinkedAccountsUiState.Ready)?.copy(busy = false, link = link) ?: current }

        private suspend fun load(): LinkedAccountsUiState.Ready {
            val profile = connection.profile()
            val user = connection.api().user(userId)
            val mediaServerOrigin =
                when (profile.mediaServer) {
                    SeerrMediaServer.Jellyfin -> UserOrigin.Jellyfin
                    SeerrMediaServer.Emby -> UserOrigin.Emby
                    SeerrMediaServer.Plex, SeerrMediaServer.NotConfigured, SeerrMediaServer.Unknown -> null
                }
            return LinkedAccountsUiState.Ready(
                plex = LinkedAccount(UserOrigin.Plex, linked = user.plexId != null, linkedAs = user.plexUsername.orNull()),
                mediaServer =
                    mediaServerOrigin?.let {
                        LinkedAccount(it, linked = !user.jellyfinUserId.isNullOrBlank(), linkedAs = user.jellyfinUsername.orNull())
                    },
                canQuickConnect = profile.hasQuickConnect && mediaServerOrigin == UserOrigin.Jellyfin,
                canUnlink = userId != PRIMARY_ADMIN_ID,
            )
        }

        @AssistedFactory
        interface Factory {
            fun create(userId: Int): LinkedAccountsViewModel
        }
    }

private const val PRIMARY_ADMIN_ID = 1

private fun Throwable.toEvent(): LinkedAccountsEvent =
    when (this) {
        is PlexPinExpiredException, is QuickConnectExpiredException -> LinkedAccountsEvent.LinkExpired
        else -> LinkedAccountsEvent.Failed(toSeerrError())
    }

private fun String?.orNull(): String? = this?.takeIf { it.isNotBlank() }
