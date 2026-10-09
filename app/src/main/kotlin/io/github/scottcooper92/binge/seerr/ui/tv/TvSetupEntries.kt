package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.ui.SetupActions
import io.github.scottcooper92.binge.seerr.ui.SetupNotice
import io.github.scottcooper92.binge.seerr.ui.SetupViewModel

@Composable
internal fun TvSetupEntry(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvSetupScreen(state = state, actions = viewModel.tvActions(), offerHandOff = true)
}

/**
 * The sign-in again, after the server rejected the session (#810): the setup form on the saved server, at its sign-in
 * step, saying why. The saved connection stays until a new sign-in saves, and that sign-in brings the rail back on its
 * own. Back has nothing to return to, so it leaves the app.
 */
@Composable
internal fun TvReconnectEntry(
    onDisconnect: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.beginEdit(notice = SetupNotice.SessionRejected) }
    TvSetupScreen(state = state, actions = viewModel.tvActions(onDisconnect), offerHandOff = true)
}

/**
 * The television screen's own wiring: Connect, whose Plex PIN is the plate's, not the browser's, and
 * the hand-off from a phone, which only a television offers.
 */
internal fun SetupViewModel.tvActions(onDisconnect: (() -> Unit)? = null): SetupActions =
    SetupActions(
        onEditAddress = ::editAddress,
        onInspect = ::inspect,
        onChangeServer = ::changeServer,
        onEditForm = ::editForm,
        onConnect = { connect(forLink = true) },
        onPlexLaunched = ::plexLaunched,
        onCancelLink = ::cancelLink,
        onRequestPasswordReset = ::requestPasswordReset,
        onAllowCleartext = ::allowCleartext,
        onStartHandOff = { showHandOff(true) },
        onCancelHandOff = { showHandOff(false) },
        onOfferSignInCode = { showHandOff(true) },
        onLocalNetworkChanged = ::localNetworkResult,
        onDisconnect = onDisconnect,
    )
