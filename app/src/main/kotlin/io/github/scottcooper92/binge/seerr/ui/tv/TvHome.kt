package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.theme.SeerrTvTheme
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestUiState
import io.github.scottcooper92.binge.seerr.ui.ScopedViewModels
import io.github.scottcooper92.binge.seerr.ui.SetupNotice
import io.github.scottcooper92.binge.seerr.ui.SetupViewModel
import io.github.scottcooper92.binge.seerr.ui.tvActions

/**
 * The television shell of the app's own UI: setup while no server is saved, and once one is, the rail
 * with the hub, the lists and settings beside it.
 */
@Composable
internal fun TvSeerrShell(viewModel: TvHomeViewModel = hiltViewModel()) {
    SeerrTvTheme {
        TvConsentGate {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            when (state) {
                TvHomeUiState.Loading -> TvLoadingPlate()
                // Each in its own view-model scope: a new connection gets new screens, not the last one's.
                TvHomeUiState.Setup -> ScopedViewModels("setup") { TvSetupEntry() }
                TvHomeUiState.Reconnect -> ScopedViewModels("reconnect") { TvReconnectEntry(onDisconnect = viewModel::disconnect) }
                TvHomeUiState.Connected -> ScopedViewModels("connected") { TvConnectedShell() }
            }
        }
    }
}

@Composable
private fun TvSetupEntry(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvSetupScreen(state = state, actions = viewModel.tvActions(), offerHandOff = true)
}

/**
 * The sign-in again, after the server rejected the session (#810): the setup form on the saved server, at its sign-in
 * step, saying why. The saved connection stays until a new sign-in saves, and that sign-in brings the rail back on its
 * own. Back has nothing to return to, so it leaves the app.
 */
@Composable
private fun TvReconnectEntry(
    onDisconnect: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.beginEdit(notice = SetupNotice.SessionRejected) }
    TvSetupScreen(state = state, actions = viewModel.tvActions(onDisconnect), offerHandOff = true)
}

/** The hand-off's television shell: the same state and callbacks as the phone's picker, under the TV theme. */
@Composable
internal fun TvAdvancedRequestShell(
    state: AdvancedRequestUiState,
    actions: TvAdvancedRequestActions,
) {
    SeerrTvTheme {
        TvAdvancedRequestScreen(state = state, actions = actions)
    }
}
