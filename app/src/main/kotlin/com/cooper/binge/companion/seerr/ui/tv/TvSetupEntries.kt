package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.ui.SetupNotice
import com.cooper.binge.companion.seerr.ui.SharedSetupViewModel
import com.cooper.binge.companion.seerr.ui.actions

@Composable
internal fun TvSetupEntry(source: SharedSetupViewModel = hiltViewModel()) {
    val state by source.uiState.collectAsStateWithLifecycle()
    TvSetupScreen(state = state, actions = source.actions(tv = true, onDisconnect = null), offerHandOff = true)
}

/**
 * The sign-in again, after the server rejected the session (#810): the setup form on the saved server, at its sign-in
 * step, saying why. The saved connection stays until a new sign-in saves, and that sign-in brings Home back on its
 * own. Back has nothing to return to, so it leaves the app.
 */
@Composable
internal fun TvReconnectEntry(
    onDisconnect: () -> Unit,
    source: SharedSetupViewModel = hiltViewModel(),
) {
    val state by source.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(source) { source.beginEdit(notice = SetupNotice.SessionRejected) }
    TvSetupScreen(state = state, actions = source.actions(tv = true, onDisconnect = onDisconnect), offerHandOff = true)
}
