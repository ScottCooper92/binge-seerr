package com.cooper.binge.companion.seerr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.feedback.BugReportLinks

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
    source: SetupSource = rememberSetupSource(),
) {
    val state by source.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(source) { source.beginEdit() }
    rememberEnteredEditingGuard(state, onDone)
    SetupFlowScreen(
        source = source,
        state = state,
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
    source: SetupSource = rememberSetupSource(),
) {
    val state by source.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(source) { source.beginEdit(notice = SetupNotice.SessionRejected) }
    val context = LocalContext.current
    Box(modifier) {
        SetupFlowScreen(
            source = source,
            state = state,
            onDisconnect = onDisconnect,
            onReportProblem = { context.openInBrowser(BugReportLinks().url()) },
        )
    }
}

@Composable
internal fun SetupEntry(source: SetupSource = rememberSetupSource()) {
    val state by source.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SetupFlowScreen(
        source = source,
        state = state,
        onReportProblem = { context.openInBrowser(BugReportLinks().url()) },
    )
}

internal fun SetupViewModel.actions(onDisconnect: (() -> Unit)? = null): SetupActions =
    SetupActions(
        onEditAddress = ::editAddress,
        onInspect = ::inspect,
        onChangeServer = ::changeServer,
        onCheckAgain = ::inspect,
        onEditForm = ::editForm,
        onConnect = ::connect,
        onPlexLaunched = ::plexLaunched,
        onCancelLink = ::cancelLink,
        onRequestPasswordReset = ::requestPasswordReset,
        onAllowCleartext = ::allowCleartext,
        onLocalNetworkChanged = ::localNetworkResult,
        onDisconnect = onDisconnect,
        onTryDemo = tryDemo,
    )

/** The phone's setup on the path [source] runs on: the shared connect screen for the shared view model, the app's own screen otherwise. */
@Composable
private fun SetupFlowScreen(
    source: SetupSource,
    state: SetupUiState,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    onReportProblem: (() -> Unit)? = null,
    onDisconnect: (() -> Unit)? = null,
) {
    when (source) {
        is SharedSetupViewModel ->
            SeerrConnectScreen(
                vm = source,
                title = title,
                onExit = onBack,
                onReportProblem = onReportProblem,
                onDisconnect = onDisconnect,
            )
        else ->
            SetupScreen(
                state = state,
                actions = source.setupActions(tv = false, onDisconnect = onDisconnect),
                title = title,
                onBack = onBack,
                onReportProblem = onReportProblem,
            )
    }
}
