package com.cooper.binge.companion.seerr.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
    source: SharedSetupViewModel = hiltViewModel(),
) {
    val state by source.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(source) { source.beginEdit() }
    rememberEnteredEditingGuard(state, onDone)
    SeerrConnectScreen(
        vm = source,
        title = stringResource(R.string.settings_edit_connection),
        onExit = onDone.takeIf { showBack },
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
    source: SharedSetupViewModel = hiltViewModel(),
) {
    LaunchedEffect(source) { source.beginEdit(notice = SetupNotice.SessionRejected) }
    val context = LocalContext.current
    Box(modifier) {
        SeerrConnectScreen(
            vm = source,
            onReportProblem = { context.openInBrowser(BugReportLinks().url()) },
            onDisconnect = onDisconnect,
        )
    }
}

@Composable
internal fun SetupEntry(source: SharedSetupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    SeerrConnectScreen(
        vm = source,
        onReportProblem = { context.openInBrowser(BugReportLinks().url()) },
    )
}
