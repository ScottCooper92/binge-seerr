package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import com.cooper.binge.companion.seerr.ui.SetupViewModel
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.HubViewModel
import com.cooper.binge.companion.seerr.ui.hub.openBingeOnPlayStore
import com.cooper.binge.companion.seerr.ui.rememberEnteredEditingGuard
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SettingsViewModel
import com.cooper.binge.companion.seerr.ui.settings.server.JobsViewModel
import com.cooper.binge.companion.seerr.ui.tv.settings.TvJobActions
import com.cooper.binge.companion.seerr.ui.tv.settings.TvSettingsActions
import com.cooper.binge.companion.seerr.ui.tv.settings.TvSettingsSheet

/**
 * The settings sheet over Home, on the same view models as the phone's Settings. [onEditConnection] is called before the sheet
 * closes: the form is a full-screen page above Home, and the sheet's focus trap would refuse its request for focus.
 */
@Composable
internal fun TvSettingsEntry(
    onEditConnection: () -> Unit,
    onDismissRequest: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    hubViewModel: HubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hub by hubViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Refetched on every arrival, so returning from Edit connection shows the new server.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    // The hub view model is shared with Home and is `Lazily`, so it re-reads whether Binge is installed and the
    // pending count only when told it is visible; without this, installing Binge from the Binge row stays stale.
    // Settings draws no downloads strip, so it leaves the downloads poll off (#837).
    DisposableEffect(hubViewModel) {
        hubViewModel.setScreenVisible(true, downloads = false)
        onDispose { hubViewModel.setScreenVisible(false) }
    }
    val actions =
        TvSettingsActions(
            onEditConnection = {
                onEditConnection()
                onDismissRequest()
            },
            onDisconnect = viewModel::disconnect,
            onToggleShareUsageData = viewModel::setShareUsageData,
            onToggleSendCrashReports = viewModel::setSendCrashReports,
            onOpenBingeListing = { context.openBingeOnPlayStore() },
        )
    // The jobs view model is only stood up once the row it feeds can actually appear — admin-only, same
    // gate as the row itself — so a non-admin viewer never pays for a `/settings/jobs` fetch they cannot use.
    if ((state as? SettingsUiState.Ready)?.config != null) {
        TvAdminSettingsSheet(state = state, actions = actions, hub = hub, onDismissRequest = onDismissRequest)
    } else {
        TvSettingsSheet(state = state, actions = actions, onDismissRequest = onDismissRequest, hub = hub)
    }
}

@Composable
private fun TvAdminSettingsSheet(
    state: SettingsUiState,
    actions: TvSettingsActions,
    hub: HubUiState,
    onDismissRequest: () -> Unit,
    jobsViewModel: JobsViewModel = hiltViewModel(),
) {
    val jobs by jobsViewModel.uiState.collectAsStateWithLifecycle()
    TvSettingsSheet(
        state = state,
        // `runWhenReady`, not `run`: this view model's own load races the scan row becoming visible, so an early press
        // has to wait it out (and retry once from a failed load) rather than being dropped.
        actions =
            TvSettingsActions(
                onEditConnection = actions.onEditConnection,
                onDisconnect = actions.onDisconnect,
                jobs = TvJobActions(onRun = jobsViewModel::runWhenReady, onCancel = jobsViewModel::cancel),
                onToggleShareUsageData = actions.onToggleShareUsageData,
                onToggleSendCrashReports = actions.onToggleSendCrashReports,
                onOpenBingeListing = actions.onOpenBingeListing,
            ),
        onDismissRequest = onDismissRequest,
        jobs = jobs,
        hub = hub,
    )
}

/** The setup form on the live connection, above Home; leaves once new credentials are saved, or on Back. */
@Composable
internal fun TvEditConnectionOverlay(
    onDone: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.beginEdit() }
    rememberEnteredEditingGuard(state, onDone)
    BackHandler(onBack = onDone)
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TvSetupScreen(state = state, actions = viewModel.tvActions(), offerHandOff = true)
    }
}
