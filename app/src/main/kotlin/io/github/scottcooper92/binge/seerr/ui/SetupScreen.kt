package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.StepFlowScreen
import com.binge.designsystem.template.StepHeading
import io.github.scottcooper92.binge.seerr.R

/** Everything the setup screen can ask of its ViewModel, in one place so the entry stays a wiring. */
class SetupActions(
    val onEditAddress: (String) -> Unit,
    val onInspect: () -> Unit,
    val onChangeServer: () -> Unit,
    val onEditForm: (SignInForm.() -> SignInForm) -> Unit,
    val onConnect: () -> Unit,
    val onPlexLaunched: () -> Unit,
    val onCancelLink: () -> Unit,
    val onRequestPasswordReset: () -> Unit,
    /** The opt-in to plain HTTP to a public host. Defaulted so a preview that never shows one need not wire it. */
    val onAllowCleartext: (Boolean) -> Unit = {},
    /** The television's hand-off from a phone (#323). Defaulted for the same reason: the phone never offers it. */
    val onStartHandOff: () -> Unit = {},
    val onCancelHandOff: () -> Unit = {},
    /** The TV's sign-in step asks for a code of its own when the address didn't come with one. */
    val onOfferSignInCode: () -> Unit = {},
    /** The local-network permission may have changed; read it again. Defaulted like [onAllowCleartext]. */
    val onLocalNetworkChanged: () -> Unit = {},
    /**
     * Leaving the saved server, offered beside the sign-in only when the app put the form up itself because the server
     * rejected the session (#810), so a user whose server is gone is not stuck on it. On TV it sits on the code page,
     * where that sign-in lands; the typed form's bar has no room for a third button. Null everywhere else.
     */
    val onDisconnect: (() -> Unit)? = null,
)

/**
 * The two steps to a connection, on the design system's step flow: the address, then the sign-in the server accepts.
 * Back from the sign-in, the arrow or the gesture, is changing server, so it returns to the address.
 *
 * Edit connection passes a [title] and its [onBack]: it opens as a pane of its own, and the step flow has no bar to
 * carry either, so the bar sits above the flow there. Setup and reconnect have no bar; the step's heading says
 * where the user is.
 */
@Composable
fun SetupScreen(
    state: SetupUiState,
    actions: SetupActions,
    title: String? = null,
    onBack: (() -> Unit)? = null,
) {
    if (title == null) {
        SetupStepFlow(state, actions)
    } else {
        BingeScreenScaffold(bar = ScreenBar.Small, title = title, onBack = onBack) { padding ->
            // The bar's insets are consumed here so the step flow does not pad the status bar a second time.
            SetupStepFlow(state, actions, Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
}

@Composable
private fun SetupStepFlow(
    state: SetupUiState,
    actions: SetupActions,
    modifier: Modifier = Modifier,
) {
    // A step keeps drawing itself while it slides out, after the state has moved on, so each draws the last state it had.
    val address = rememberLatest(state as? SetupUiState.Address)
    val signIn = rememberLatest(state as? SetupUiState.SignIn)
    // While the address waits on the local-network permission, asking for it is the way on: Continue could only fail (#1099).
    val allow = if (address?.needsLocalNetwork == true) rememberAllowLocalNetwork(actions.onLocalNetworkChanged) else null
    StepFlowScreen(
        stepCount = SETUP_STEPS,
        currentStep = if (state is SetupUiState.Address || state is SetupUiState.Loading) ADDRESS_STEP else SIGN_IN_STEP,
        // Both steps type into a field, so the flow, footer and all, sits above the keyboard.
        modifier = modifier.imePadding(),
        onBack = actions.onChangeServer,
        // The home swaps to the hub on the credentials landing; Connected is the frame in between.
        loading = state is SetupUiState.Loading || state is SetupUiState.Connected,
        aside = { step -> if (step == SIGN_IN_STEP) signIn?.let { SetupServerBackdrop(it.server) } },
        heading = { step ->
            when (step) {
                ADDRESS_STEP ->
                    StepHeading(
                        title = stringResource(R.string.tv_setup_headline),
                        subtitle = stringResource(R.string.setup_intro),
                    )
                else -> signIn?.let { SetupServerHeading(it.server) }
            }
        },
        footer = { step ->
            when (step) {
                ADDRESS_STEP -> address?.let { SetupAddressFooter(it, allow, actions.onInspect) }
                else -> signIn?.let { SetupSignInFooter(it, actions) }
            }
        },
    ) { step ->
        when (step) {
            ADDRESS_STEP -> address?.let { SetupAddressContent(it, allow, actions) }
            else -> signIn?.let { SetupSignInContent(it, actions) }
        }
    }
    (state as? SetupUiState.SignIn)?.link?.let { link -> SetupLinkSheet(link, actions.onPlexLaunched, actions.onCancelLink) }
}

private const val SETUP_STEPS = 2
private const val ADDRESS_STEP = 0
private const val SIGN_IN_STEP = 1

/** The last non-null [value] composed here. A plain holder rather than state: [value] changing is what recomposes. */
@Composable
private fun <T : Any> rememberLatest(value: T?): T? {
    val holder = remember { LatestHolder<T>() }
    if (value != null) holder.latest = value
    return holder.latest
}

private class LatestHolder<T : Any> {
    var latest: T? = null
}

internal fun SetupError.messageRes(): Int =
    when (this) {
        SetupError.InvalidUrl -> R.string.setup_error_invalid_url
        SetupError.NotSeerr -> R.string.setup_error_not_seerr
        SetupError.Rejected -> R.string.setup_error_rejected
        SetupError.Unreachable -> R.string.setup_error_unreachable
        SetupError.UnreachableNotLocal -> R.string.setup_error_unreachable_not_local
        SetupError.LocalNetworkDenied -> R.string.setup_error_local_network_denied
        SetupError.Unknown -> R.string.setup_error_unknown
        SetupError.LinkExpired -> R.string.setup_error_link_expired
        SetupError.HandOffSessionRejected -> R.string.setup_error_handoff_session_rejected
    }
