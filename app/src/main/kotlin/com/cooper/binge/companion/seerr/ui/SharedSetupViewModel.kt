package com.cooper.binge.companion.seerr.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.auth.PlexPinFlow
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.demo.demoCredentials
import com.cooper.binge.companion.seerr.di.IoDispatcher
import com.cooper.binge.companion.seerr.handoff.AddressHandOffs
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.insecurePublicHostOrNull
import com.cooper.binge.companion.seerr.seerr.toServerFailure
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.homelab.core.presentation.connect.ConnectLinks
import com.cooper.homelab.core.presentation.connect.ConnectPorts
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.ConnectViewModel
import com.cooper.homelab.core.presentation.connect.SessionSink
import com.cooper.homelab.core.server.address.PortDefaults
import com.cooper.homelab.core.server.connection.SecretCipher
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
 * Setup on the shared connect machine: the one setup view model, for the phone's connect screen and the television's own screens.
 * It gives them a [SetupUiState] and [SetupActions]. The address and sign-in steps are the machine's; what is beside it is the
 * app's own: the television's hand-off, the password reset and the notice. Plex and Quick Connect, which finish in another app,
 * run on the library's link flows ([links]).
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
    ) : ViewModel() {
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
                cipher = cipher,
                events = { it.report(analytics) },
            )

        private val handed = SharedHandedSession(app, machine, connection, analytics, crashBreadcrumbs) { inspect() }

        private val handOff =
            SetupHandOff(
                scope = viewModelScope,
                dispatcher = dispatcher,
                handOffs = handOffs,
                onState = { handOff -> app.update { it.copy(handOff = handOff) } },
                onAddress = handed::onAddress,
                onCredentials = handed::onCredentials,
                onCode = { code -> app.update { it.copy(code = code) } },
                progress = {
                    // The permission prompt is the TV's user's to answer, and the session waits for it, so the phone is not told the
                    // hand-off failed: it would stop following a TV that goes on to sign in (#2428).
                    val waitingOnPermission = app.value.awaitingPermission && app.value.handedSession != null
                    val failed = (machine.draft.value.error != null && !waitingOnPermission) || app.value.handOffRejected
                    uiState.value.toHandOffProgress(app.value.received, failed, app.value.attempts)
                },
            )

        val uiState: StateFlow<SetupUiState> =
            combine(links.view, connection.credentials, app) { view, saved, beside ->
                view.state.toSetupUiState(saved, beside, view.link)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SetupUiState.Loading)

        /** What the shared connect screen reads: the machine's step, and the two things beside it that are the app's own. */
        internal val connectUi: StateFlow<SeerrConnectUi> =
            combine(links.view, app) { view, beside ->
                SeerrConnectUi(
                    view.state,
                    beside.notice,
                    beside.resetCoolingDown,
                    beside.received,
                    beside.resetBusy,
                    beside.resetError,
                    view.link,
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
                onStartLink = { startLink() },
                onChangeServer = ::changeServer,
                onCheckAgain = ::inspect,
            )

        init {
            handOff.signedInWith(viewModelScope, dispatcher, connection.credentials)
            links.restore()
            reopenSignedOutServer()
            handed.watch(viewModelScope, dispatcher)
        }

        /** Settings' Edit connection, or the sign-in again after the server rejected the session when [notice] says so. */
        fun beginEdit(notice: SetupNotice? = null) {
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
            machine.inspect(then = handed::adopt)
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
            app.update {
                it.copy(
                    notice = null,
                    code = null,
                    received = false,
                    handedSession = null,
                    handOffRejected = false,
                    resetError = null,
                )
            }
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
            if (form.mode?.fields?.isEmpty() == true) startLink(forTelevision) else machine.connect()
        }

        private fun startLink(forTelevision: Boolean = false) {
            crashBreadcrumbs.log("signing in via ${machine.draft.value.form.mode?.id}")
            links.start(forTelevision)
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
