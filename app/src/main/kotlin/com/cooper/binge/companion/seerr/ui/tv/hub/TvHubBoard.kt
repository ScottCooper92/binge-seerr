package com.cooper.binge.companion.seerr.ui.tv.hub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.ErrorKind
import com.binge.designsystem.tv.component.TvButton
import com.binge.designsystem.tv.component.TvErrorPlate
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.focus.tvArrivalObserver
import com.binge.designsystem.tv.template.TvBoard
import com.binge.designsystem.tv.theme.TvButtonStyle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.AllowLocalNetwork
import com.cooper.binge.companion.seerr.ui.hub.ConnectionHealth
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.isProblem
import com.cooper.binge.companion.seerr.ui.rememberAllowLocalNetwork
import com.cooper.binge.companion.seerr.ui.tv.TvHubLoading

/** Everything the problem page can ask for, in one place so the entry stays a wiring. */
internal class TvHubActions(
    val onRetry: () -> Unit,
    val onReconnect: () -> Unit,
    val onDisconnect: () -> Unit,
)

/**
 * The server's problem as a television board, which Home shows in place of its requests while the server is not
 * answering: the problem, a retry, editing the connection, or disconnecting. A healthy server is Home's requests, never
 * this page (#796), so a hub that reads healthy here is only the moment before Home swaps the requests in.
 */
@Composable
internal fun TvHubBoard(
    state: HubUiState,
    actions: TvHubActions,
    modifier: Modifier = Modifier,
) {
    val ready = state as? HubUiState.Ready
    val health = if (state is HubUiState.Error) state.health else ready?.health
    // The view model holds a problem through the re-check meant to clear it (#873), so the page keeps its problem, and the
    // remote its button, rather than blinking to the loading page and back (#796).
    val problem = health?.takeIf { it.isProblem() }
    val rechecking = (state as? HubUiState.Error)?.rechecking ?: ready?.rechecking ?: false
    // Held here, above the problem: the permission's answer is read again whichever problem the page is showing.
    val allow = rememberAllowLocalNetwork { if (problem == ConnectionHealth.LocalNetworkDenied) actions.onRetry() }
    if (problem == null) {
        // Loading is the same page as the rows' own, so Home's wait is one page and not a board's plate and then another.
        // A rejected session is never a problem here: the app answers it by going to sign-in (#810).
        TvHubLoading(modifier)
        return
    }
    TvBoard(title = ready?.server?.title ?: stringResource(R.string.companion_name), modifier = modifier) {
        TvHubProblem(problem, rechecking, actions, allow, modifier = Modifier.weight(1f))
    }
}

/**
 * Server gone or the overview not loaded: retry, or edit the connection. A session the server rejected never reaches
 * here, since the app goes to sign-in instead (#810).
 */
@Composable
private fun TvHubProblem(
    health: ConnectionHealth,
    rechecking: Boolean,
    actions: TvHubActions,
    allow: AllowLocalNetwork,
    modifier: Modifier = Modifier,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    val denied = health == ConnectionHealth.LocalNetworkDenied
    TvErrorPlate(
        kind =
            when (health) {
                ConnectionHealth.CouldNotLoad -> ErrorKind.Server
                ConnectionHealth.LocalNetworkDenied -> ErrorKind.Forbidden
                else -> ErrorKind.Network
            },
        // The hub's own words say what to do about each problem, so they stand in for the kind's copy.
        title =
            stringResource(
                when (health) {
                    ConnectionHealth.CouldNotLoad -> R.string.hub_couldnt_load_headline
                    ConnectionHealth.LocalNetworkDenied -> R.string.hub_local_network_headline
                    else -> R.string.hub_unreachable_headline
                },
            ),
        // A retry in flight says so where the problem was, and Try again does nothing more while it runs; focus stays on it.
        message =
            stringResource(
                when {
                    rechecking -> R.string.hub_rechecking
                    health == ConnectionHealth.CouldNotLoad -> R.string.hub_couldnt_load_body
                    health == ConnectionHealth.LocalNetworkDenied -> R.string.hub_local_network_body
                    else -> R.string.hub_unreachable_body
                },
            ),
        // A refused local network is fixed by the permission, not a retry, so the plate leads with it instead.
        onRetry =
            if (denied) {
                null
            } else if (rechecking) {
                ({})
            } else {
                actions.onRetry
            },
        alignment = Alignment.Center,
        retryFocusRequester = arrival.requester,
        modifier = modifier.tvArrivalObserver(arrival),
        extraActions = {
            if (denied) {
                TvButton(
                    label = stringResource(allow.shortLabel),
                    onClick = allow.run,
                    style = TvButtonStyle.Primary,
                    modifier = Modifier.focusRequester(arrival.requester),
                )
            }
            TvButton(label = stringResource(R.string.settings_edit_connection), onClick = actions.onReconnect)
            TvButton(label = stringResource(R.string.hub_disconnect), onClick = actions.onDisconnect)
        },
    )
}
