package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.R

/**
 * The setup form on the live connection, prefilled with its address. The connection stays in
 * place until new credentials are saved, so a rejected edit changes nothing; success pops.
 *
 * Reachable straight off the hub's own reconnect prompt as well as from Settings, so [showBack]
 * follows the same rule as everywhere else in the detail pane: hidden only when this is the one
 * thing standing between the viewer and the hub they never left.
 */
@Composable
internal fun EditConnectionEntry(
    onDone: () -> Unit,
    showBack: Boolean,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.beginEdit() }
    rememberEnteredEditingGuard(state, onDone)
    SetupScreen(
        state = state,
        actions = viewModel.actions(),
        title = stringResource(R.string.settings_edit_connection),
        onBack = onDone.takeIf { showBack },
    )
}

/**
 * The sign-in again, after the server rejected the session (#810): the setup form on the saved server, at its sign-in
 * step, saying why, with a way to leave the server instead. Back has nothing to return to, so it leaves the app.
 */
@Composable
internal fun ReconnectEntry(
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.beginEdit(notice = SetupNotice.SessionRejected) }
    Box(modifier) { SetupScreen(state = state, actions = viewModel.actions(onDisconnect)) }
}

@Composable
internal fun SetupEntry(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SetupScreen(state = state, actions = viewModel.actions())
}

internal fun SetupViewModel.actions(onDisconnect: (() -> Unit)? = null): SetupActions =
    SetupActions(
        onEditAddress = ::editAddress,
        onInspect = ::inspect,
        onChangeServer = ::changeServer,
        onEditForm = ::editForm,
        onConnect = ::connect,
        onPlexLaunched = ::plexLaunched,
        onCancelLink = ::cancelLink,
        onRequestPasswordReset = ::requestPasswordReset,
        onAllowCleartext = ::allowCleartext,
        onLocalNetworkChanged = ::localNetworkResult,
        onDisconnect = onDisconnect,
    )
