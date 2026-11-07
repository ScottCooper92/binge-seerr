package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.binge.designsystem.tv.focus.TvUserMovesHost
import com.cooper.binge.companion.seerr.theme.SeerrTvTheme
import com.cooper.binge.companion.seerr.ui.AdvancedRequestUiState
import com.cooper.binge.companion.seerr.ui.ScopedViewModels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The television shell of the app's own UI: setup while no server is saved, and once one is, Home. Home is the only
 * destination: Account, Issues and Settings open above it from its top row.
 */
@Composable
internal fun TvSeerrShell(
    viewModel: TvHomeViewModel = hiltViewModel(),
    linkedRequests: Flow<Int> = emptyFlow(),
) {
    SeerrTvTheme {
        // The one wrapper that notes the user's D-pad presses, so an arrival whose target attaches late (the setup and
        // home pages each have one) never pulls focus back from where the user has moved.
        TvUserMovesHost {
            TvConsentGate {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                when (state) {
                    TvHomeUiState.Loading -> TvLoadingPlate()
                    // Each in its own view-model scope: a new connection gets new screens, not the last one's.
                    TvHomeUiState.Setup -> ScopedViewModels("setup") { TvSetupEntry() }
                    TvHomeUiState.Reconnect -> ScopedViewModels("reconnect") { TvReconnectEntry(onDisconnect = viewModel::disconnect) }
                    TvHomeUiState.Connected -> ScopedViewModels("connected") { TvConnectedShell(linkedRequests = linkedRequests) }
                }
            }
        }
    }
}

/** The hand-off's television shell: the same state and callbacks as the phone's picker, under the TV theme. */
@Composable
internal fun TvAdvancedRequestShell(
    state: AdvancedRequestUiState,
    actions: TvAdvancedRequestActions,
) {
    SeerrTvTheme {
        TvUserMovesHost { TvAdvancedRequestScreen(state = state, actions = actions) }
    }
}
