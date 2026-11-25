package com.cooper.binge.companion.seerr.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.template.StepHeading
import com.cooper.binge.companion.seerr.R
import com.cooper.homelab.core.flags.bingePromotion
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.uiandroid.connect.AllowLocalNetwork
import com.cooper.homelab.core.uiandroid.connect.ConnectAddressActions
import com.cooper.homelab.core.uiandroid.connect.ConnectLinkActions
import com.cooper.homelab.core.uiandroid.connect.ConnectScreen
import com.cooper.homelab.core.uiandroid.connect.ConnectScreenText
import com.cooper.homelab.core.uiandroid.connect.ConnectSignInActions
import com.cooper.homelab.core.uiandroid.connect.rememberAllowLocalNetwork
import com.cooper.homelab.core.uiandroid.feedback.ReportProblemAction
import com.cooper.homelab.core.uiandroid.flags.LocalFlags
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

/**
 * The phone's setup flow on the shared connect screen, on [SharedSetupViewModel]: the address, then the sign-in the server accepts or
 * the message of one not set up yet. The words and parts are Seerr's own ([seerrConnectCopy], [seerrSignInOptions]); the heading, the
 * server's backdrop and the report-a-problem menu are passed in.
 *
 * [title] and [onExit] are Edit connection's bar, and [onDisconnect] is the way off a server that rejected the session (#810).
 */
@Composable
internal fun SeerrConnectScreen(
    vm: SharedSetupViewModel,
    modifier: Modifier = Modifier,
    title: String? = null,
    onExit: (() -> Unit)? = null,
    onReportProblem: (() -> Unit)? = null,
    onDisconnect: (() -> Unit)? = null,
) {
    val ui by vm.connectUi.collectAsStateWithLifecycle()
    // While the address waits on the local-network permission, asking for it is the way on: Continue could only fail (#1099).
    val allow =
        if ((ui.state as? ConnectState.Address)?.needsLocalNetwork ==
            true
        ) {
            rememberAllowLocalNetwork(vm::localNetworkResult)
        } else {
            null
        }
    SeerrConnectContent(
        ui = ui,
        addressActions = ConnectAddressActions(vm::editAddress, vm::allowCleartext, vm::inspect, vm.tryDemo),
        signInActions = vm.connectSignInActions,
        linkActions = ConnectLinkActions(onLaunched = vm.links::launched, onCancel = vm.links::cancel),
        onRequestPasswordReset = vm::requestPasswordReset,
        modifier = modifier,
        localNetwork = allow,
        title = title,
        onExit = onExit,
        onReportProblem = onReportProblem,
        onDisconnect = onDisconnect,
    )
}

/** [SeerrConnectScreen] over the state it reads and the actions it runs, so a frame or a test needs no view model. */
@Composable
internal fun SeerrConnectContent(
    ui: SeerrConnectUi,
    addressActions: ConnectAddressActions,
    signInActions: ConnectSignInActions,
    linkActions: ConnectLinkActions,
    onRequestPasswordReset: () -> Unit,
    modifier: Modifier = Modifier,
    localNetwork: AllowLocalNetwork? = null,
    title: String? = null,
    onExit: (() -> Unit)? = null,
    onReportProblem: (() -> Unit)? = null,
    onDisconnect: (() -> Unit)? = null,
) {
    val address = (ui.state as? ConnectState.Address)?.address
    // A reset in flight holds the step as an attempt does: Connect and the reset wait for it (the old form did the same).
    val state = (ui.state as? ConnectState.SignIn)?.takeIf { ui.resetBusy }?.copy(isConnecting = true) ?: ui.state
    ConnectScreen(
        state = state,
        link = ui.link,
        copy = seerrConnectCopy(),
        text =
            ConnectScreenText(
                placeholder = stringResource(AdminR.string.placeholder_server_url),
                notThisServer = stringResource(SetupError.NotSeerr.messageRes()),
                notSetUpMessage = stringResource(R.string.setup_not_set_up_message),
                linkCopy = { mode -> seerrLinkCopy(mode) },
            ),
        addressActions = addressActions,
        signInActions = signInActions,
        linkActions = linkActions,
        addressHeading = { SeerrAddressHeading() },
        // The name and edition are set into the aside (over its artwork, or on their own with none), so the heading slot has nothing left to draw.
        serverHeading = {},
        modifier = modifier.fillMaxWidth(),
        title = title,
        onExit = onExit,
        // Before a server is connected Settings is out of reach, so a problem can still be reported from the bar.
        barActions =
            onReportProblem?.let { report ->
                { alpha -> ReportProblemAction(onReportProblem = report, glassBackgroundAlpha = alpha) }
            },
        aside = { server -> SetupServerBackdrop(server.preview) },
        localNetwork = localNetwork,
        errorText = { error -> seerrErrorText(error, address = address, received = ui.received) },
        signInOptions =
            seerrSignInOptions(
                notice = ui.notice,
                resetCoolingDown = ui.resetCoolingDown,
                resetError = ui.resetError,
                onRequestPasswordReset = onRequestPasswordReset,
                onEdit = signInActions.onEditForm,
                // The row runs out under the gutter so it scrolls edge to edge, and its first tab starts back at the fields' edge.
                tabEdgePadding = dimensionResource(R.dimen.setup_connect_tabs_edge_padding),
                tabBleed = dimensionResource(R.dimen.setup_connect_tabs_bleed),
                onDisconnect = onDisconnect,
            ),
    )
}

@Composable
private fun SeerrAddressHeading() {
    StepHeading(
        title = stringResource(R.string.tv_setup_headline),
        subtitle = stringResource(if (LocalFlags.current.bingePromotion) R.string.setup_intro else R.string.setup_intro_plain),
    )
}
