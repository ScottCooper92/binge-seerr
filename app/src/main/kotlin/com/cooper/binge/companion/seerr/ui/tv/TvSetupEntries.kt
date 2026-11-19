package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.ui.SetupActions
import com.cooper.binge.companion.seerr.ui.SetupNotice
import com.cooper.binge.companion.seerr.ui.SetupSource
import com.cooper.binge.companion.seerr.ui.SetupViewModel
import com.cooper.binge.companion.seerr.ui.rememberSetupSource
import com.cooper.binge.companion.seerr.ui.setupActions

@Composable
internal fun TvSetupEntry(source: SetupSource = rememberSetupSource()) {
    val state by source.uiState.collectAsStateWithLifecycle()
    TvSetupScreen(state = state, actions = source.setupActions(tv = true), offerHandOff = true)
}

/**
 * The sign-in again, after the server rejected the session (#810): the setup form on the saved server, at its sign-in
 * step, saying why. The saved connection stays until a new sign-in saves, and that sign-in brings Home back on its
 * own. Back has nothing to return to, so it leaves the app.
 */
@Composable
internal fun TvReconnectEntry(
    onDisconnect: () -> Unit,
    source: SetupSource = rememberSetupSource(),
) {
    val state by source.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(source) { source.beginEdit(notice = SetupNotice.SessionRejected) }
    TvSetupScreen(state = state, actions = source.setupActions(tv = true, onDisconnect = onDisconnect), offerHandOff = true)
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
        onCheckAgain = ::inspect,
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
        onTryDemo = tryDemo,
    )
