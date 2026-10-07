package io.github.scottcooper92.binge.seerr.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.PlexPinFlow
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.handoff.AddressHandOffs
import io.github.scottcooper92.binge.seerr.handoff.HAND_OFF_SESSION_MODE
import io.github.scottcooper92.binge.seerr.seerr.LocalNetworkPermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.insecurePublicHostOrNull
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsEvents
import io.github.scottcooper92.binge.seerr.telemetry.CrashBreadcrumbs
import io.github.scottcooper92.binge.seerr.telemetry.NoOpAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.NoOpCrashBreadcrumbs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
        handOffs: AddressHandOffs,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        private val analytics: Analytics = NoOpAnalytics,
        private val crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
        private val localNetwork: LocalNetworkPermission = LocalNetworkPermission.AlwaysGranted,
    ) : ViewModel() {
        private val draft = MutableStateFlow(SetupDraft())

        private val links =
            SetupLinks(
                scope = viewModelScope,
                dispatcher = dispatcher,
                connection = connection,
                plex = plex,
                savedState = savedState,
                cipher = cipher,
                onLink = { link -> draft.update { it.copy(busy = false, link = link) } },
                onFinished = { failure -> draft.finish(failure, analytics) },
            )

        private val handOff =
            SetupHandOff(
                scope = viewModelScope,
                dispatcher = dispatcher,
                handOffs = handOffs,
                onState = { handOff -> draft.update { it.copy(handOff = handOff) } },
                // An address a phone sent, already checked as a base URL, goes exactly where a typed
                // one does - into the field, then inspect() - so the plain-HTTP opt-in and the sign-in
                // after it are the same as for an address entered on the remote.
                // A session sent with the address is held in memory only, for the one inspect() it rides on.
                onAddress = { address, session ->
                    draft.update {
                        it.copy(serverUrl = address, handOff = null, error = null, received = true, handedSession = session)
                    }
                    inspect()
                },
                // Credentials a phone app sealed for this TV, already opened by the listener. They go into the form and
                // through connect() exactly as if typed, so a refusal or a failure reads the same on both screens. Taken
                // only on the sign-in step, for a mode that has fields to fill, and never while another attempt runs.
                onCredentials = { credentials ->
                    val current = draft.value
                    val server = current.server?.takeIf { !current.busy && current.link == null }
                    val form = server?.let { credentials.toSignInForm(it.modes) }
                    if (server != null && credentials.mode == HAND_OFF_SESSION_MODE && credentials.session.isNotEmpty()) {
                        // A phone that scanned the sign-in step's code, carrying on with its own session: the same path as one
                        // that came with the address, through a fresh read of the server.
                        draft.update { it.copy(handedSession = credentials.session, error = null, notice = null) }
                        inspect()
                    } else if (form != null) {
                        draft.update { it.copy(form = form, error = null, attempts = it.attempts + 1) }
                        connect()
                    } else {
                        // Counted all the same: the phone was told 200, and waits for the TV to reach this number.
                        draft.update { it.copy(attempts = it.attempts + 1) }
                    }
                },
                // One read of the draft, so `failed` and the attempt it is about never come from different moments.
                onCode = { code -> draft.update { it.copy(code = code) } },
                progress = {
                    val snapshot = draft.value
                    uiState.value.toHandOffProgress(snapshot.received, snapshot.error != null, snapshot.attempts)
                },
            )

        init {
            restore()
        }

        val uiState: StateFlow<SetupUiState> =
            combine(connection.credentials, draft) { saved, draft -> draft.toUiState(saved, localNetwork) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SetupUiState.Loading)

        /**
         * Settings' Edit connection: the form on the live server, prefilled and read, with that connection kept until a new one
         * saves. [notice] says why the form is up when the user didn't ask for it: a session the server rejected (#810).
         */
        fun beginEdit(notice: SetupNotice? = null) {
            if (draft.value.editing != null) return
            crashBreadcrumbs.log("editing server connection")
            viewModelScope.launch(dispatcher) {
                val saved = runCatching { connection.current() }.getOrNull() ?: return@launch
                // A connection already opted in to plain HTTP keeps its tick, or Edit would stall on it.
                val consented = saved.baseUrl.insecurePublicHostOrNull()?.takeIf { connection.allowsCleartextTo(it) }
                draft.update { it.copy(editing = saved, serverUrl = saved.baseUrl, cleartextHost = consented, notice = notice) }
                inspect()
            }
        }

        /** The permission prompt came back, or the user returned from Settings: read the permission again. */
        fun localNetworkResult() = draft.update { it.copy(permissionReads = it.permissionReads + 1, error = null) }

        fun editAddress(value: String) {
            // Typing takes over from the phone: the follow phase ends, so the phone cannot overwrite the field
            // and a later request for the plate starts a fresh listener.
            if (draft.value.received) handOff.cancel()
            draft.update { it.copy(serverUrl = value, error = null, received = false, handedSession = null) }
        }

        /**
         * The user's explicit opt-in to plain HTTP to the public host the address names. It is held
         * against that host, so editing the address to another one asks again.
         */
        fun allowCleartext(allowed: Boolean) =
            draft.update { it.copy(cleartextHost = if (allowed) it.serverUrl.insecurePublicHostOrNull() else null) }

        fun inspect() {
            val url = draft.value.serverUrl
            if (url.isBlank() || draft.value.busy) return
            val insecureHost = url.insecurePublicHostOrNull()
            // Plain HTTP to a public host is refused until the user opts in for that host.
            if (insecureHost != null && insecureHost != draft.value.cleartextHost) return
            // A handed session is an attempt the phone has been told the TV will count, so it is counted before the
            // server is read: a failed read then reports against the right number instead of leaving the phone waiting.
            draft.update { it.copy(busy = true, error = null, attempts = if (it.handedSession != null) it.attempts + 1 else it.attempts) }
            viewModelScope.launch(dispatcher) {
                insecureHost?.let { connection.allowCleartextTo(it) }
                connection
                    .inspect(url)
                    .onSuccess { preview ->
                        val server = preview.toSetupServer()
                        // With the phone's session, straight in: the sign-in form is shown only if the server turns it down.
                        val session = draft.value.handedSession
                        if (session == null) {
                            draft.update { it.copy(server = server, form = SignInForm(mode = server.modes.first())) }
                        } else {
                            // The server decides: a session it doesn't answer to is dropped, never kept, and the sign-in step
                            // comes up with a line saying why, which the phone reads as a refused attempt.
                            draft.update { it.copy(handedSession = null) }
                            val adopted = connection.adoptHandedSession(server.baseUrl, session, analytics, crashBreadcrumbs)
                            draft.update {
                                if (adopted) {
                                    it.copy(editing = null)
                                } else {
                                    it.copy(
                                        server = server,
                                        form = SignInForm(mode = server.modes.first()),
                                        error = SetupError.HandOffSessionRejected,
                                    )
                                }
                            }
                        }
                    }.onFailure { failure ->
                        draft.update {
                            it.copy(
                                error = failure.toSetupError().orLocalNetworkDenied(url, localNetwork).forAddress(url, it.received),
                                handedSession = null,
                            )
                        }
                    }
                draft.update { it.copy(busy = false) }
            }
        }

        /**
         * The television's "send the address from your phone": [showing] puts up a code with a
         * listener behind it, and taking the plate down stops listening at once.
         */
        fun showHandOff(showing: Boolean) {
            val current = draft.value
            when {
                // Once an address is in, the plate going is the page moving on, not the user leaving: the listener
                // stays up to tell the phone how the sign-in went, and ends on its own.
                !showing ->
                    if (!current.received) {
                        handOff.cancel()
                        draft.update { it.copy(handOff = null) }
                    }
                // The sign-in step offers a code too, so a phone can finish what the remote would type: the listener that
                // brought the address is still up when there was one, and otherwise this opens one.
                current.server != null -> if (current.code == null) handOff.start()
                current.busy -> Unit
                else -> {
                    // Past an address the listener is only following the sign-in for the phone's page. Asking for
                    // the plate again wants a new code, so that phase ends and a fresh listener replaces it.
                    if (current.received) {
                        handOff.cancel()
                        draft.update { it.copy(handOff = null, received = false) }
                    }
                    draft.update { it.copy(error = null) }
                    handOff.start()
                }
            }
        }

        fun changeServer() {
            cancelLink()
            // Back to the address: the sign-in step's code goes with the step.
            handOff.cancel()
            draft.update { it.copy(server = null, form = SignInForm(), error = null, notice = null, code = null, received = false) }
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
            crashBreadcrumbs.log("signing in via ${current.form.mode}")
            val editing = current.editing != null
            when (current.form.mode) {
                SeerrSignInMode.Plex -> links.startPlex(server, editing, forLink)
                SeerrSignInMode.QuickConnect -> links.startQuickConnect(server, editing)
                else ->
                    viewModelScope.launch(dispatcher) {
                        connection.signIn(server.baseUrl, current.form)?.let { draft.finish(it.exceptionOrNull(), analytics) }
                    }
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
            crashBreadcrumbs.log("requesting password reset")
            viewModelScope.launch(dispatcher) {
                connection
                    .requestPasswordReset(server.baseUrl, current.form.email.trim())
                    .onSuccess { draft.update { it.copy(notice = SetupNotice.ResetEmailSent) } }
                    .onFailure { failure -> draft.update { it.copy(error = failure.toSetupError()) } }
                    .also { result ->
                        analytics.event(
                            AnalyticsEvents.PASSWORD_RESET_REQUESTED,
                            mapOf(AnalyticsEvents.PARAM_SUCCESS to result.isSuccess),
                        )
                    }
                draft.update { it.copy(busy = false) }
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

        /** Reads the pending link's server again and picks the wait up where it was. */
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
                    // finish() reads the draft's form.mode for the sign_in event; set it to the mode
                    // actually being resumed before that early return, or it reports the stale default.
                    draft.update { it.copy(form = SignInForm(mode = pending.mode)) }
                    return draft.finish(failure, analytics)
                }
            // `busy` stays true here: `links.resume()` genuinely suspends before `onLink` fires for
            // Plex, and clearing it early would re-enable Connect and let a second flow start.
            draft.update { it.copy(server = server, form = SignInForm(mode = pending.mode)) }
            links.resume(server, pending)
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
