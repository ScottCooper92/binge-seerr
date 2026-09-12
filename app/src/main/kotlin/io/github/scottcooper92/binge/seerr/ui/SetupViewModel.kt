package io.github.scottcooper92.binge.seerr.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.InvalidServerUrlException
import io.github.scottcooper92.binge.seerr.auth.PlexPinExpiredException
import io.github.scottcooper92.binge.seerr.auth.PlexPinFlow
import io.github.scottcooper92.binge.seerr.auth.QuickConnectExpiredException
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrServerPreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrLoginRequest
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.isInsecurePublicUrl
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403

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
        private val plex: PlexPinFlow,
    ) : ViewModel() {
        private val draft = MutableStateFlow(Draft())
        private var linkJob: Job? = null

        val uiState: StateFlow<SetupUiState> =
            combine(connection.credentials, draft) { saved, draft ->
                val server = draft.server
                when {
                    saved != null -> SetupUiState.Connected(saved, isDisconnecting = draft.busy)
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

        fun editAddress(value: String) = draft.update { it.copy(serverUrl = value, error = null) }

        fun inspect() {
            val url = draft.value.serverUrl
            if (url.isBlank() || draft.value.busy) return
            draft.update { it.copy(busy = true, error = null) }
            viewModelScope.launch {
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

        fun editForm(transform: SignInForm.() -> SignInForm) = draft.update { it.copy(form = it.form.transform(), error = null, notice = null) }

        fun connect() {
            val current = draft.value
            val server = current.server ?: return
            if (!current.form.canSubmit || current.busy || current.link != null) return
            draft.update { it.copy(busy = true, error = null, notice = null) }
            when (current.form.mode) {
                SeerrSignInMode.Plex -> startPlex(server)
                SeerrSignInMode.QuickConnect -> startQuickConnect(server)
                else -> signIn(server, current.form)
            }
        }

        fun plexLaunched() =
            draft.update { current ->
                val link = current.link as? LinkFlow.Plex ?: return@update current
                current.copy(link = link.copy(launchPending = false))
            }

        fun cancelLink() {
            linkJob?.cancel()
            linkJob = null
            draft.update { it.copy(busy = false, link = null) }
        }

        fun requestPasswordReset() {
            val current = draft.value
            val server = current.server ?: return
            if (!current.form.canRequestReset || current.busy) return
            draft.update { it.copy(busy = true, error = null, notice = null) }
            viewModelScope.launch {
                connection
                    .requestPasswordReset(server.baseUrl, current.form.email.trim())
                    .onSuccess { draft.update { it.copy(notice = SetupNotice.ResetEmailSent) } }
                    .onFailure { failure -> draft.update { it.copy(error = failure.toSetupError()) } }
                draft.update { it.copy(busy = false) }
            }
        }

        fun disconnect() {
            if (draft.value.busy) return
            draft.update { it.copy(busy = true) }
            viewModelScope.launch {
                connection.disconnect()
                draft.update { it.copy(busy = false) }
            }
        }

        private fun signIn(
            server: SetupServer,
            form: SignInForm,
        ) {
            viewModelScope.launch {
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

        private fun startPlex(server: SetupServer) {
            linkJob =
                viewModelScope.launch {
                    val pin = attempt { plex.start() }.getOrElse { failure -> return@launch finish(failure) }
                    draft.update { it.copy(busy = false, link = LinkFlow.Plex(pin.code, pin.authUrl, launchPending = true)) }
                    val outcome = attempt { connection.logInWithPlex(server.baseUrl, plex.awaitToken(pin)).getOrThrow() }
                    finish(outcome.exceptionOrNull())
                }
        }

        private fun startQuickConnect(server: SetupServer) {
            linkJob =
                viewModelScope.launch {
                    val session = connection.startQuickConnect(server.baseUrl).getOrElse { failure -> return@launch finish(failure) }
                    draft.update { it.copy(busy = false, link = LinkFlow.QuickConnect(session.code)) }
                    finish(connection.finishQuickConnect(server.baseUrl, session).exceptionOrNull())
                }
        }

        /** Every attempt ends here: the secret leaves the form once it is stored encrypted, or the failure is shown. */
        private fun finish(failure: Throwable?) {
            if (failure is CancellationException) return
            linkJob = null
            draft.update { current ->
                if (failure == null) {
                    current.copy(busy = false, link = null, form = SignInForm(mode = current.form.mode))
                } else {
                    current.copy(busy = false, link = null, error = failure.toSetupError())
                }
            }
        }

        /** [runCatching] would swallow the cancellation [cancelLink] sends; this lets it through. */
        private inline fun <T> attempt(block: () -> T): Result<T> =
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }

        private data class Draft(
            val serverUrl: String = "",
            val server: SetupServer? = null,
            val form: SignInForm = SignInForm(),
            val busy: Boolean = false,
            val link: LinkFlow? = null,
            val error: SetupError? = null,
            val notice: SetupNotice? = null,
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

/** A malformed address and an expired code are the form's own cases; a 401 or 403 is the credentials; the rest is the server or the network. */
private fun Throwable.toSetupError(): SetupError =
    when (this) {
        is InvalidServerUrlException -> SetupError.InvalidUrl
        is PlexPinExpiredException, is QuickConnectExpiredException -> SetupError.LinkExpired
        else ->
            when (toSeerrError()) {
                SeerrError.Unauthorized, SeerrError.Forbidden -> SetupError.Rejected
                SeerrError.Unreachable -> SetupError.Unreachable
                else -> SetupError.Unknown
            }
    }
