package com.cooper.binge.companion.seerr.ui

import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.telemetry.AnalyticsEvents
import com.cooper.homelab.core.presentation.connect.ConnectEvent
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.LinkState
import com.cooper.homelab.core.presentation.connect.SignInMode
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.presentation.connect.SignInForm as ConnectForm

/**
 * What the shared setup keeps beside the machine: the television's hand-off, which the machine knows nothing of, and the few things
 * the old view model held that are the app's own (a notice, a password reset). Each is one more input to [toSetupUiState].
 */
internal data class SharedSetupApp(
    val handOff: AddressHandOff? = null,
    /** The hand-off's live code, kept through the sign-in step so a phone can carry on there. */
    val code: AddressHandOff.Listening? = null,
    /** Whether the address is the one a phone sent, untouched since. */
    val received: Boolean = false,
    /** The session a phone sent with the address, until the server it names has been read and has judged it. */
    val handedSession: String? = null,
    /** How many sets of credentials a phone has sent this TV, taken or not; the phone's way to tell which attempt an error is about. */
    val attempts: Int = 0,
    /** A phone's session was sent and the server turned it down, so the sign-in step says so. */
    val handOffRejected: Boolean = false,
    /** The first read of a phone's address failed on the local-network permission, so its session waits for the grant's read (#2428). */
    val awaitingPermission: Boolean = false,
    val notice: SetupNotice? = null,
    val resetBusy: Boolean = false,
    val resetError: SetupError? = null,
    val resetCoolingDown: Boolean = false,
)

/** The form the screens read, from the machine's: the same four fields, under the mode the machine chose. */
internal fun ConnectForm.toSetupForm(): SignInForm =
    SignInForm(
        mode = (mode as? SeerrMode)?.mode ?: SeerrSignInMode.ApiKey,
        apiKey = value(FIELD_API_KEY),
        username = value(FIELD_USERNAME),
        email = value(FIELD_EMAIL),
        password = value(FIELD_PASSWORD),
    )

/** What the screen shows for [this] step, with [saved] the connection already stored and [app] what is the app's own. */
internal fun ConnectState<SetupServer>.toSetupUiState(
    saved: SeerrCredentials?,
    app: SharedSetupApp,
    link: LinkState? = null,
): SetupUiState =
    when (this) {
        ConnectState.Loading -> SetupUiState.Loading
        is ConnectState.Address ->
            SetupUiState.Address(
                serverUrl = address,
                insecure = insecure,
                isInspecting = isInspecting,
                error = error.toSetupError()?.forAddress(address, app.received),
                cleartextAllowed = cleartextAllowed,
                handOff = app.handOff,
                needsLocalNetwork = needsLocalNetwork,
                code = app.code,
                received = app.received,
                canTryDemo = canTryDemo && !app.received,
            )
        is ConnectState.NotSetUp -> SetupUiState.NotSetUp(server = server.preview, isChecking = isChecking, error = error.toSetupError())
        is ConnectState.SignIn ->
            SetupUiState.SignIn(
                server = server.preview,
                form = form.toSetupForm(),
                isConnecting = isConnecting || app.resetBusy,
                link = link?.toLinkFlow(form.mode),
                error = error.toSetupError() ?: app.resetError ?: SetupError.HandOffSessionRejected.takeIf { app.handOffRejected },
                notice = app.notice,
                code = app.code,
                resetCoolingDown = app.resetCoolingDown,
            )
        ConnectState.Connected -> saved?.let { SetupUiState.Connected(it) } ?: SetupUiState.Loading
    }

/** What field [id] holds, as the screens' form keeps it. */
internal fun SignInForm.field(id: String): String =
    when (id) {
        FIELD_API_KEY -> apiKey
        FIELD_USERNAME -> username
        FIELD_EMAIL -> email
        else -> password
    }

/** The machine's sign-in finished: counted under the mode's name, or as the demo. Never an address, a host or a user. */
internal fun ConnectEvent.report(analytics: Analytics) {
    if (this !is ConnectEvent.SignedIn) return
    val method = if (mode == "demo") AnalyticsEvents.METHOD_DEMO else mode
    analytics.event(
        AnalyticsEvents.SIGN_IN,
        mapOf(AnalyticsEvents.PARAM_METHOD to method, AnalyticsEvents.PARAM_SUCCESS to success),
    )
}

/** What the screens call: the phone's, or the television's, which also offers the hand-off and asks Plex for its short PIN. */
internal fun SharedSetupViewModel.actions(
    tv: Boolean,
    onDisconnect: (() -> Unit)?,
): SetupActions =
    SetupActions(
        onEditAddress = ::editAddress,
        onInspect = ::inspect,
        onChangeServer = ::changeServer,
        onCheckAgain = ::inspect,
        onEditForm = ::editForm,
        onConnect = { connect(tv) },
        onPlexLaunched = links::launched,
        onCancelLink = links::cancel,
        onRequestPasswordReset = ::requestPasswordReset,
        onAllowCleartext = ::allowCleartext,
        onStartHandOff = { if (tv) showHandOff(true) },
        onCancelHandOff = { if (tv) showHandOff(false) },
        onOfferSignInCode = { if (tv) showHandOff(true) },
        onLocalNetworkChanged = ::localNetworkResult,
        onDisconnect = onDisconnect,
        onTryDemo = tryDemo,
    )

/**
 * The link to draw beside this step. An attempt that fails posts its error and the link flow takes the code down as two
 * updates, so a reader of both can see the error with the code still up. An attempt's ending, whatever its error, never draws the code.
 */
internal fun ConnectState<SetupServer>.linkWhileLive(link: LinkState?): LinkState? =
    if ((this as? ConnectState.SignIn)?.error != null) null else link

/** The connect screen's input: the machine's step, and the notice and reset cooldown that sit beside it, with [received] for the address's error. */
internal data class SeerrConnectUi(
    val state: ConnectState<SetupServer>,
    val notice: SetupNotice? = null,
    val resetCoolingDown: Boolean = false,
    val received: Boolean = false,
    val resetBusy: Boolean = false,
    val resetError: SetupError? = null,
    val link: LinkState? = null,
)

/** The code waiting to be approved, as the screens that still read [LinkFlow] show it: Plex's carries the page to open. */
internal fun LinkState.toLinkFlow(mode: SignInMode?): LinkFlow =
    if ((mode as? SeerrMode)?.mode == SeerrSignInMode.Plex && openUrl != null) {
        LinkFlow.Plex(code, openUrl.orEmpty(), launchPending)
    } else {
        LinkFlow.QuickConnect(code)
    }
