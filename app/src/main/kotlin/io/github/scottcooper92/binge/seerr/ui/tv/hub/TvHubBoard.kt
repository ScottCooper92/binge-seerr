package io.github.scottcooper92.binge.seerr.ui.tv.hub

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.template.TvBoard
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.AllowLocalNetwork
import io.github.scottcooper92.binge.seerr.ui.hub.ConnectionHealth
import io.github.scottcooper92.binge.seerr.ui.hub.HubUiState
import io.github.scottcooper92.binge.seerr.ui.hub.isProblem
import io.github.scottcooper92.binge.seerr.ui.rememberAllowLocalNetwork
import io.github.scottcooper92.binge.seerr.ui.tv.TvBoardPlate
import io.github.scottcooper92.binge.seerr.ui.tv.TvHubLoading

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
    // A re-probe passes through Checking. The page keeps the problem it was showing rather than blinking to the
    // loading page, so the remote stays on its button and the retry's backoff carries on (#796).
    var shown by remember { mutableStateOf<ConnectionHealth?>(null) }
    if (health != null && health.isProblem()) shown = health
    val problem = shown.takeIf { health == ConnectionHealth.Checking || health?.isProblem() == true }
    // Held here, above the problem: the permission's answer is read again whichever problem the page is showing.
    val allow = rememberAllowLocalNetwork { if (problem == ConnectionHealth.LocalNetworkDenied) actions.onRetry() }
    if (problem == null) {
        // Loading is the same page as the rows' own, so Home's wait is one page and not a board's plate and then another.
        TvHubLoading(modifier)
        return
    }
    TvBoard(title = ready?.server?.title ?: stringResource(R.string.companion_name), modifier = modifier) {
        TvHubProblem(problem, actions, allow, modifier = Modifier.weight(1f))
    }
}

/**
 * Server gone or the overview not loaded: retry, or edit the connection. A session the server rejected never reaches
 * here, since the app goes to sign-in instead (#810).
 */
@Composable
private fun TvHubProblem(
    health: ConnectionHealth,
    actions: TvHubActions,
    allow: AllowLocalNetwork,
    modifier: Modifier = Modifier,
) {
    val arrival = rememberTvArrivalFocus()
    TvArrivalFocusEffect(arrival)
    TvBoardPlate(
        headline =
            stringResource(
                when (health) {
                    ConnectionHealth.CouldNotLoad -> R.string.hub_couldnt_load_headline
                    ConnectionHealth.LocalNetworkDenied -> R.string.hub_local_network_headline
                    else -> R.string.hub_unreachable_headline
                },
            ),
        body =
            stringResource(
                when (health) {
                    ConnectionHealth.CouldNotLoad -> R.string.hub_couldnt_load_body
                    ConnectionHealth.LocalNetworkDenied -> R.string.hub_local_network_body
                    else -> R.string.hub_unreachable_body
                },
            ),
        icon = Icons.Filled.Warning,
        primary =
            if (health == ConnectionHealth.LocalNetworkDenied) {
                stringResource(allow.shortLabel) to allow.run
            } else {
                stringResource(R.string.hub_retry) to actions.onRetry
            },
        alternate = stringResource(R.string.settings_edit_connection) to actions.onReconnect,
        secondary = stringResource(R.string.hub_disconnect) to actions.onDisconnect,
        modifier = modifier,
        arrival = arrival,
    )
}
