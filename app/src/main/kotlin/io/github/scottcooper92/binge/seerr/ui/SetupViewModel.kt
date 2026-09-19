package io.github.scottcooper92.binge.seerr.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.InvalidServerUrlException
import io.github.scottcooper92.binge.seerr.auth.NotSeerrServerException
import io.github.scottcooper92.binge.seerr.auth.PlexPinExpiredException
import io.github.scottcooper92.binge.seerr.auth.PlexPinFlow
import io.github.scottcooper92.binge.seerr.auth.QuickConnectExpiredException
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrServerPreview
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrLoginRequest
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.isInsecurePublicUrl
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The form offers the media server's own sign-in first and the admin key last. */
private val MODE_ORDER =
    listOf(
        SeerrSignInMode.Plex,
        SeerrSignInMode.Jellyfin,
        SeerrSignInMode.Emby,
        SeerrSignInMode.QuickConnect,
        SeerrSignInMode.Local,
        SeerrSignInMode.ApiKey,
    )

/**
 * Setup in two steps. The address is read first, unauthenticated, so the form offers only what
 * that server accepts; then one of its sign-ins runs. A sign-in that finishes in another app (Plex
 * in the browser, Quick Connect on Jellyfin) is a [LinkFlow] the screen shows while this polls.
 */
@HiltViewModel
class SetupViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        plex: PlexPinFlow,
        savedState: SavedStateHandle,
        cipher: SecretCipher,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val draft = MutableStateFlow(Draft())

        private val links =
            SetupLinks(
                scope = viewModelScope,
                dispatcher = dispatcher,
                connection = connection,
                plex = plex,
                savedState = savedState,
                cipher = cipher,
                onLink = { link -> draft.update { it.copy(busy = false, link = link) } },
                onFinished = ::finish,
            )

        init {
            restore()
        }

        val uiState: StateFlow<SetupUiState> =
            combine(connection.credentials, draft) { saved, draft ->
                val server = draft.server
                when {
                    // While editing, the connection being edited is not "connected": only new credentials are.
                    saved != null && saved != draft.editing -> SetupUiState.Connected(saved)
                    server == null ->
                        SetupUiState.Address(
                            serverUrl = draft.serverUrl,
                            insecure = draft.serverUrl.isInsecurePublicUrl(),
                            isInspecting = draft.busy,
                            error = draft.error,
                        )
                    else ->
                        SetupUiState.SignIn(
                            server = server,
                            form = draft.form,
                            isConnecting = draft.busy,
                            link = draft.link,
                            error = draft.error,
                            notice = draft.notice,
                        )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SetupUiState.Loading)

        /** Settings' Edit connection: the form on the live server, prefilled and read, with that connection kept until a new one saves. */
        fun beginEdit() {
            if (draft.value.editing != null) return
            viewModelScope.launch(dispatcher) {
                val saved = runCatching { connection.current() }.getOrNull() ?: return@launch
                draft.update { it.copy(editing = saved, serverUrl = saved.baseUrl) }
                inspect()
            }
        }

        fun editAddress(value: String) = draft.update { it.copy(serverUrl = value, error = null) }

        fun inspect() {
            val url = draft.value.serverUrl
            if (url.isBlank() || draft.value.busy) return
            draft.update { it.copy(busy = true, error = null) }
            viewModelScope.launch(dispatcher) {
                connection
                    .inspect(url)
                    .onSuccess { preview ->
                        val server = preview.toSetupServer()
                        draft.update { it.copy(server = server, form = SignInForm(mode = server.modes.first())) }
                    }.onFailure { failure -> draft.update { it.copy(error = failure.toSetupError()) } }
                draft.update { it.copy(busy = false) }
            }
        }

        fun changeServer() {
            cancelLink()
            draft.update { it.copy(server = null, form = SignInForm(), error = null, notice = null) }
        }

        fun editForm(transform: SignInForm.() -> SignInForm) =
            draft.update { it.copy(form = it.form.transform(), error = null, notice = null) }

        /**
         * [forLink] is the television screen's own Connect: a Plex sign-in mints the short PIN
         * typed at plex.tv/link rather than the one the phone embeds in a browser URL. It changes
         * nothing for any other mode.
         */
        fun connect(forLink: Boolean = false) {
            val current = draft.value
            val server = current.server ?: return
            if (!current.form.canSubmit || current.busy || current.link != null) return
            draft.update { it.copy(busy = true, error = null, notice = null) }
            val editing = current.editing != null
            when (current.form.mode) {
                SeerrSignInMode.Plex -> links.startPlex(server, editing, forLink)
                SeerrSignInMode.QuickConnect -> links.startQuickConnect(server, editing)
                else -> signIn(server, current.form)
            }
        }

        fun plexLaunched() =
            draft.update { current ->
                val link = current.link as? LinkFlow.Plex ?: return@update current
                current.copy(link = link.copy(launchPending = false))
            }

        fun cancelLink() {
            links.cancel()
            draft.update { it.copy(busy = false, link = null) }
        }

        fun requestPasswordReset() {
            val current = draft.value
            val server = current.server ?: return
            if (!current.form.canRequestReset || current.busy) return
            draft.update { it.copy(busy = true, error = null, notice = null) }
            viewModelScope.launch(dispatcher) {
                connection
                    .requestPasswordReset(server.baseUrl, current.form.email.trim())
                    .onSuccess { draft.update { it.copy(notice = SetupNotice.ResetEmailSent) } }
                    .onFailure { failure -> draft.update { it.copy(error = failure.toSetupError()) } }
                draft.update { it.copy(busy = false) }
            }
        }

        private fun signIn(
            server: SetupServer,
            form: SignInForm,
        ) {
            viewModelScope.launch(dispatcher) {
                val result =
                    when (form.mode) {
                        SeerrSignInMode.ApiKey -> connection.connect(server.baseUrl, SeerrAuth.ApiKey(form.apiKey.trim()))
                        SeerrSignInMode.Local ->
                            connection.logIn(server.baseUrl, SeerrLoginRequest.Local(form.email.trim(), form.password))
                        SeerrSignInMode.Jellyfin, SeerrSignInMode.Emby ->
                            connection.logIn(server.baseUrl, SeerrLoginRequest.Jellyfin(form.username.trim(), form.password))
                        SeerrSignInMode.Plex, SeerrSignInMode.QuickConnect -> return@launch
                    }
                finish(result.exceptionOrNull())
            }
        }

        /**
         * A link the user left the app to approve outlives the process. On the way back the server
         * is read again and the wait picked up where it was, rather than dropping the user on a
         * blank address step with an approval they have already given.
         */
        private fun restore() {
            val pending = links.pending() ?: return
            draft.update { it.copy(serverUrl = pending.serverUrl, busy = true) }
            viewModelScope.launch(dispatcher) { resume(pending) }
        }

        private suspend fun resume(pending: PendingLink) {
            // Before the server is read, not after: while `editing` is unset the saved credentials
            // read as connected, and the screen would leave for the hub mid-resume.
            if (pending.editing) {
                val editing = runCatching { connection.current() }.getOrNull()
                draft.update { it.copy(editing = editing) }
            }
            val server =
                connection.inspect(pending.serverUrl).map { it.toSetupServer() }.getOrElse { failure ->
                    // The address is kept and the failure shown, but the link is not: a server that
                    // cannot be reached now would otherwise resume into the same failure every launch.
                    links.forget()
                    return finish(failure)
                }
            // `busy` stays true here: `links.resume()` genuinely suspends before `onLink` fires for
            // Plex, and clearing it early would re-enable Connect and let a second flow start.
            draft.update { it.copy(server = server, form = SignInForm(mode = pending.mode)) }
            links.resume(server, pending)
        }

        /** Every attempt ends here: the secret leaves the form once it is stored encrypted, or the failure is shown. */
        private fun finish(failure: Throwable?) {
            if (failure is CancellationException) return
            draft.update { current ->
                if (failure == null) {
                    current.copy(busy = false, link = null, editing = null, form = SignInForm(mode = current.form.mode))
                } else {
                    current.copy(busy = false, link = null, error = failure.toSetupError())
                }
            }
        }

        private data class Draft(
            val serverUrl: String = "",
            val server: SetupServer? = null,
            val form: SignInForm = SignInForm(),
            val busy: Boolean = false,
            val link: LinkFlow? = null,
            val error: SetupError? = null,
            val notice: SetupNotice? = null,
            /** The credentials being edited, which the form must not read as "connected". */
            val editing: SeerrCredentials? = null,
        )

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

private fun SeerrServerPreview.toSetupServer(): SetupServer {
    val settings = profile.settings
    val modes = MODE_ORDER.filter { it in profile.signInModes }
    return SetupServer(
        baseUrl = baseUrl,
        title = settings.applicationTitle?.takeIf { it.isNotBlank() } ?: profile.variant.displayName,
        variant = profile.variant,
        versionLabel = profile.version?.label,
        mediaServerName = settings.jellyfinServerName?.takeIf { it.isNotBlank() },
        modes = modes,
        canResetPassword = settings.emailEnabled && SeerrSignInMode.Local in modes,
        backdropUrl = backdropUrls.firstOrNull(),
    )
}

/** The address's own cases first, then an expired code; a 401 or 403 is the credentials; the rest is the server or the network. */
private fun Throwable.toSetupError(): SetupError =
    when (this) {
        is InvalidServerUrlException -> SetupError.InvalidUrl
        is NotSeerrServerException -> SetupError.NotSeerr
        is PlexPinExpiredException, is QuickConnectExpiredException -> SetupError.LinkExpired
        else ->
            when (toSeerrError()) {
                SeerrError.Unauthorized, SeerrError.Forbidden -> SetupError.Rejected
                SeerrError.Unreachable -> SetupError.Unreachable
                else -> SetupError.Unknown
            }
    }
