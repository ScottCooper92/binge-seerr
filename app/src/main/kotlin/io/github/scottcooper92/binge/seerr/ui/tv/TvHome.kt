package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.theme.SeerrTvTheme
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestUiState
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
                TvHomeUiState.Setup -> TvViewModelScope("setup") { TvSetupEntry() }
                TvHomeUiState.Connected -> TvViewModelScope("connected") { TvConnectedShell() }
            }
        }
    }
}

@Composable
private fun TvSetupEntry(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvSetupScreen(state = state, actions = viewModel.tvActions(), offerHandOff = true)
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
