package com.cooper.binge.companion.seerr.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.kit.SecretCipher
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.PlexPinFlow
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.demo.demoCredentials
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.handoff.AddressHandOffs
import com.cooper.binge.companion.seerr.handoff.HAND_OFF_SESSION_MODE
import com.cooper.binge.companion.seerr.handoff.HandOffCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.insecurePublicHostOrNull
import com.cooper.binge.companion.seerr.seerr.toServerFailure
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.connect.ConnectError
import com.cooper.homelab.core.presentation.connect.ConnectLinks
import com.cooper.homelab.core.presentation.connect.ConnectPorts
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.ConnectViewModel
import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.presentation.connect.SessionSink
import com.cooper.homelab.core.server.address.PortDefaults
import com.cooper.homelab.core.server.network.CleartextConsent
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.CrashBreadcrumbs
import com.cooper.homelab.core.telemetry.NoOpAnalytics
import com.cooper.homelab.core.telemetry.NoOpCrashBreadcrumbs
import com.cooper.homelab.core.uiandroid.connect.ConnectSignInActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

/**
 * Setup on the shared connect machine, behind the shared-setup flag. It gives the screens what [SetupViewModel] does, a
 * [SetupUiState] and the same actions, so they do not change. The address and sign-in steps are the machine's; what is beside
 * it is the app's own: the television's hand-off, the password reset and the notice. Plex and Quick Connect, which finish in
 * another app, run on the library's link flows ([links]).
 */
@HiltViewModel
class SharedSetupViewModel
    @Inject
    constructor(
        private val connection: SeerrConnection,
        plex: PlexPinFlow,
        savedState: SavedStateHandle,
        cipher: SecretCipher,
        handOffs: AddressHandOffs,
        cleartext: CleartextConsent,
        localNetwork: LocalNetworkPermission,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
        private val analytics: Analytics = NoOpAnalytics,
        private val crashBreadcrumbs: CrashBreadcrumbs = NoOpCrashBreadcrumbs,
    ) : ViewModel(),
        SetupSource {
        private val app = MutableStateFlow(SharedSetupApp())

        private val sink = SessionSink<SetupServer, Unit> { _, _ -> true }

        private val machine =
            ConnectViewModel(
                scope = viewModelScope,
                dispatcher = dispatcher,
                probe = SeerrServerProbe(connection),
                authenticators = seerrAuthenticators(connection, crashBreadcrumbs),
                sink = sink,
                classify = { it.toServerFailure() },
                ports = ConnectPorts(PortDefaults.SEERR),
                cleartext = cleartext,
                consentHost = { address -> address.url.toHttpUrlOrNull()?.host ?: address.host },
                localNetwork = localNetwork,
                saved = connection.credentials.map { it != null },
                events = { it.report(analytics) },
                demo = ::enterDemo,
            )

        /**
         * The sign-ins that finish in another app: Plex in the browser, Quick Connect on Jellyfin. The pending link is kept encrypted
         * in the saved state, so the wait is picked up again after the process dies while the user is over there.
         */
        internal val links =
            ConnectLinks(
                scope = viewModelScope,
                dispatcher = dispatcher,
                machine = machine,
                providers =
                    mapOf(
                        SeerrSignInMode.Plex.name to SeerrPlexLink(connection, plex),
                        SeerrSignInMode.QuickConnect.name to SeerrQuickConnectLink(connection),
                    ),
                sink = sink,
                stash = SavedStateLinkStash(savedState),
                cipher = cipher.forLinks(),
                events = { it.report(analytics) },
            )

        private val handOff =
            SetupHandOff(
                scope = viewModelScope,
                dispatcher = dispatcher,
                handOffs = handOffs,
                onState = { handOff -> app.update { it.copy(handOff = handOff) } },
                // A phone's address fills the field and waits for the TV's user (#1084). A session sealed for it proves the sender
                // had the code's key and is bound to this address (#1029), so it goes straight to the read.
                onAddress = { address, session ->
                    machine.editAddress(address)
                    app.update {
                        it.copy(
                            handOff = null,
                            received = true,
                            handedSession = session,
                            awaitingPermission = false,
                            handOffRejected = false,
                        )
                    }
                    if (session != null) inspect()
                },
                onCredentials = ::onHandedCredentials,
                onCode = { code -> app.update { it.copy(code = code) } },
                progress = {
                    // The permission prompt is the TV's user's to answer, and the session waits for it, so the phone is not told the
                    // hand-off failed: it would stop following a TV that goes on to sign in (#2428).
                    val waitingOnPermission = app.value.awaitingPermission && app.value.handedSession != null
                    val failed = (machine.draft.value.error != null && !waitingOnPermission) || app.value.handOffRejected
                    uiState.value.toHandOffProgress(app.value.received, failed, app.value.attempts)
                },
            )

        override val uiState: StateFlow<SetupUiState> =
            combine(machine.state, connection.credentials, app, links.link) { state, saved, beside, link ->
                state.toSetupUiState(saved, beside, link)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SetupUiState.Loading)

        /** What the shared connect screen reads: the machine's step, and the two things beside it that are the app's own. */
        internal val connectUi: StateFlow<SeerrConnectUi> =
            combine(
                machine.state,
                app,
                links.link,
            ) { state, beside, link ->
                SeerrConnectUi(
                    state,
                    beside.notice,
                    beside.resetCoolingDown,
                    beside.received,
                    beside.resetBusy,
                    beside.resetError,
                    link,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SeerrConnectUi(ConnectState.Loading))

        /** The sign-in step's controls, on the machine's own form. A change clears what the last attempt left on the page. */
        internal val connectSignInActions: ConnectSignInActions =
            ConnectSignInActions(
                onSelectMode = { mode ->
                    app.update { it.copy(notice = null, handOffRejected = false, resetError = null) }
                    machine.selectMode(mode)
                },
                onEditForm = { transform ->
                    app.update { it.copy(notice = null, handOffRejected = false, resetError = null) }
                    machine.editForm(transform)
                },
                onConnect = { connect() },
                onStartLink = { links.start() },
                onChangeServer = ::changeServer,
                onCheckAgain = ::inspect,
            )

        init {
            handOff.signedInWith(viewModelScope, dispatcher, connection.credentials)
            links.restore()
            reopenSignedOutServer()
            watchHandedSession()
        }

        override fun beginEdit(notice: SetupNotice?) {
            if (machine.draft.value.editing) return
            crashBreadcrumbs.log("editing server connection")
            viewModelScope.launch(dispatcher) {
                val saved = attempt { connection.current() }.getOrNull() ?: return@launch
                app.update { it.copy(notice = notice) }
                machine.beginEdit(saved.baseUrl)
                // A connection already opted in to plain HTTP keeps its tick, or Edit would stall on it.
                val consented = saved.baseUrl.insecurePublicHostOrNull()?.takeIf { connection.allowsCleartextTo(it) }
                if (consented != null) machine.allowCleartext(true)
                inspect()
            }
        }

        fun localNetworkResult() = machine.localNetworkResult()

        fun editAddress(value: String) {
            // Typing takes over from the phone: the follow phase ends, so the phone cannot overwrite the field.
            if (app.value.received) handOff.cancel()
            app.update { it.copy(received = false, handedSession = null, awaitingPermission = false) }
            machine.editAddress(value)
        }

        /** For an address a phone sent, agreeing is the go-ahead the TV was waiting on, so it reads the server at once (#907). */
        fun allowCleartext(allowed: Boolean) {
            machine.allowCleartext(allowed)
            if (allowed && app.value.received) inspect()
        }

        fun inspect() {
            val current = machine.draft.value
            if (current.address.isBlank() || current.busy) return
            val insecureHost = current.address.insecurePublicHostOrNull()
            if (insecureHost != null && insecureHost != current.cleartextHost) return
            // A handed session is an attempt the phone has been told the TV will count, so it is counted before the server is read.
            if (app.value.handedSession != null) app.update { it.copy(attempts = it.attempts + 1) }
            app.update { it.copy(handOffRejected = false, resetError = null) }
            machine.inspect(then = ::adoptHandedSession)
        }

        fun showHandOff(showing: Boolean) {
            val current = machine.draft.value
            val beside = app.value
            when {
                !showing ->
                    if (!beside.received) {
                        handOff.cancel()
                        app.update { it.copy(handOff = null) }
                    }
                current.server != null -> if (beside.code == null) handOff.start()
                current.busy -> Unit
                else -> {
                    if (beside.received) {
                        handOff.cancel()
                        app.update { it.copy(handOff = null, received = false) }
                    }
                    machine.editAddress(current.address)
                    handOff.start()
                }
            }
        }

        fun changeServer() {
            handOff.cancel()
            links.cancel()
            app.update { it.copy(notice = null, code = null, received = false, handOffRejected = false, resetError = null) }
            machine.changeServer()
        }

        fun editForm(transform: SignInForm.() -> SignInForm) {
            val before =
                machine.draft.value.form
                    .toSetupForm()
            val after = before.transform()
            app.update { it.copy(notice = null, handOffRejected = false, resetError = null) }
            if (after.mode != before.mode) after.mode.formMode()?.let(machine::selectMode)
            val typed =
                mapOf(
                    FIELD_API_KEY to after.apiKey,
                    FIELD_USERNAME to after.username,
                    FIELD_EMAIL to after.email,
                    FIELD_PASSWORD to after.password,
                )
            val changed = typed.filter { (id, value) -> value != before.field(id) }
            if (changed.isNotEmpty()) machine.editForm { changed.entries.fold(this) { form, (id, value) -> form.with(id, value) } }
        }

        /** A sign-in with fields runs on the machine; one with none (Plex, Quick Connect) asks for its code, a short one for a television. */
        fun connect(forTelevision: Boolean = false) {
            app.update { it.copy(notice = null, handOffRejected = false, resetError = null) }
            val form = machine.draft.value.form
            if (form.mode?.fields?.isEmpty() == true) links.start(forTelevision) else machine.connect()
        }

        /** Connects to the demo server: canned data on this device, no address and no sign-in. Disconnecting leaves it. */
        val tryDemo: () -> Unit = machine::tryDemo

        fun requestPasswordReset() {
            val current = machine.draft.value
            val server = current.server ?: return
            val email = current.form.value(FIELD_EMAIL)
            val waiting = app.value.let { it.resetBusy || it.resetCoolingDown }
            if (email.isBlank() || current.busy || waiting) return
            app.update { it.copy(resetBusy = true, resetError = null, notice = null) }
            crashBreadcrumbs.log("requesting password reset")
            viewModelScope.launch(dispatcher) {
                val result = connection.requestPasswordReset(server.preview.baseUrl, email.trim())
                analytics.event(AnalyticsEvents.PASSWORD_RESET_REQUESTED, mapOf(AnalyticsEvents.PARAM_SUCCESS to result.isSuccess))
                result
                    .onSuccess {
                        // Seerr sends a new link on every request and voids the one before, so the button waits a minute.
                        app.update { it.copy(notice = SetupNotice.ResetEmailSent, resetCoolingDown = true) }
                        viewModelScope.launch(dispatcher) {
                            delay(RESET_COOLDOWN)
                            app.update { it.copy(resetCoolingDown = false) }
                        }
                    }.onFailure { failure -> app.update { it.copy(resetError = failure.toSetupError()) } }
                app.update { it.copy(resetBusy = false) }
            }
        }

        private suspend fun enterDemo() {
            crashBreadcrumbs.log("trying the demo")
            val demo = demoCredentials()
            connection.connect(demo.baseUrl, demo.auth).getOrThrow()
        }

        /**
         * With the phone's session, straight in: the sign-in form is shown only if the server turns it down. The server decides:
         * a session it does not answer to is dropped, never kept, and the sign-in step comes up with a line saying why.
         */
        private suspend fun adoptHandedSession(server: ProbedServer<SetupServer>): Boolean {
            val session = app.value.handedSession
            if (session == null || !server.setUp) return false
            app.update { it.copy(handedSession = null, awaitingPermission = false) }
            val adopted = connection.adoptHandedSession(server.preview.baseUrl, session, analytics, crashBreadcrumbs)
            if (!adopted) app.update { it.copy(handOffRejected = true) }
            return adopted
        }

        /**
         * Credentials a phone app sealed for this TV, already opened by the listener. They go into the form and through connect()
         * exactly as if typed. Taken only on the sign-in step, for a mode that has fields to fill, and never while an attempt runs.
         */
        private fun onHandedCredentials(credentials: HandOffCredentials) {
            val current = machine.draft.value
            val server = current.server?.takeIf { !current.busy }
            val form = server?.let { credentials.toSignInForm(it.preview.modes) }
            val handedSession = credentials.mode == HAND_OFF_SESSION_MODE && credentials.session.isNotEmpty()
            if (server != null && handedSession) {
                app.update {
                    it.copy(
                        handedSession = credentials.session,
                        awaitingPermission = false,
                        notice = null,
                        handOffRejected = false,
                    )
                }
                machine.editAddress(current.address)
                inspect()
            } else if (form != null) {
                app.update { it.copy(attempts = it.attempts + 1, notice = null, handOffRejected = false) }
                form.mode.formMode()?.let(machine::selectMode)
                machine.editForm {
                    listOf(
                        FIELD_API_KEY to form.apiKey,
                        FIELD_USERNAME to form.username,
                        FIELD_EMAIL to form.email,
                        FIELD_PASSWORD to form.password,
                    ).fold(this) { acc, (id, value) -> if (value.isEmpty()) acc else acc.with(id, value) }
                }
                machine.connect()
            } else {
                app.update { it.copy(attempts = it.attempts + 1) }
            }
        }

        /**
         * A phone's session waits for the read it came with. A read that failed ends it for good, so it is never tried against
         * another address, except when the failure is the local-network permission: the session then waits for the grant's read,
         * and is adopted once that read has answered (#2428). Only that read is adopted here: any other read takes the session
         * through `inspect(then)`, so this does not count or adopt it a second time.
         */
        private fun watchHandedSession() {
            viewModelScope.launch(dispatcher) {
                machine.draft.collect { draft ->
                    if (app.value.handedSession == null) {
                        if (app.value.awaitingPermission) app.update { it.copy(awaitingPermission = false) }
                        return@collect
                    }
                    val server = draft.server
                    val waiting = app.value.awaitingPermission
                    when {
                        draft.error == ConnectError.Failed(ServerFailure.LocalNetworkDenied) ->
                            if (!waiting) app.update { it.copy(awaitingPermission = true) }
                        draft.error != null -> app.update { it.copy(handedSession = null, awaitingPermission = false) }
                        waiting && server != null && !draft.busy -> {
                            app.update { it.copy(awaitingPermission = false) }
                            if (server.setUp) {
                                app.update { it.copy(attempts = it.attempts + 1) }
                                adoptHandedSession(server)
                            } else {
                                // A server that is not set up cannot judge the session: it is dropped, not kept (#1994).
                                app.update { it.copy(handedSession = null) }
                            }
                        }
                    }
                }
            }
        }

        /** After a sign-out, the server it left, read again so setup opens on its sign-in rather than an empty address. */
        private fun reopenSignedOutServer() {
            viewModelScope.launch(dispatcher) {
                val url = connection.signedOutServer.first() ?: return@launch
                if (machine.draft.value.address
                        .isNotBlank()
                ) {
                    return@launch
                }
                machine.editAddress(url)
                inspect()
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
