package io.github.scottcooper92.binge.seerr.ui.tv

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
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.SetupViewModel
import io.github.scottcooper92.binge.seerr.ui.hub.HubUiState
import io.github.scottcooper92.binge.seerr.ui.hub.HubViewModel
import io.github.scottcooper92.binge.seerr.ui.hub.openBingeOnPlayStore
import io.github.scottcooper92.binge.seerr.ui.rememberEnteredEditingGuard
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsUiState
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.JobsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.MEDIA_SERVER_SCAN_JOB_ID
import io.github.scottcooper92.binge.seerr.ui.tv.settings.TvSettingsBoard
import io.github.scottcooper92.binge.seerr.ui.tvActions

@Composable
internal fun TvSettingsEntry(
    onEditConnection: () -> Unit,
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
    // The jobs view model is only stood up once the row it feeds can actually appear — admin-only, same
    // gate as the row itself — so a non-admin viewer never pays for a `/settings/jobs` fetch they cannot use.
    if ((state as? SettingsUiState.Ready)?.config != null) {
        TvAdminSettingsEntry(
            state = state,
            onEditConnection = onEditConnection,
            viewModel = viewModel,
            hub = hub,
            onOpenBingeListing = { context.openBingeOnPlayStore() },
        )
    } else {
        TvSettingsBoard(
            state = state,
            onEditConnection = onEditConnection,
            onDisconnect = viewModel::disconnect,
            onToggleShareUsageData = viewModel::setShareUsageData,
            onToggleSendCrashReports = viewModel::setSendCrashReports,
            hub = hub,
            onOpenBingeListing = { context.openBingeOnPlayStore() },
        )
    }
}

@Composable
private fun TvAdminSettingsEntry(
    state: SettingsUiState,
    onEditConnection: () -> Unit,
    viewModel: SettingsViewModel,
    hub: HubUiState,
    onOpenBingeListing: () -> Unit,
    jobsViewModel: JobsViewModel = hiltViewModel(),
) {
    TvSettingsBoard(
        hub = hub,
        onOpenBingeListing = onOpenBingeListing,
        state = state,
        onEditConnection = onEditConnection,
        onDisconnect = viewModel::disconnect,
        onToggleShareUsageData = viewModel::setShareUsageData,
        onToggleSendCrashReports = viewModel::setSendCrashReports,
        // The Jobs & cache page's job-row run action, reused rather than a second call to the same endpoint:
        // this board has no jobs list of its own, so the notice is what tells the admin it started.
        // `runWhenReady`, not `run`: this view model's own load races the row becoming visible, so an
        // early tap has to wait it out (and retry once from a failed one) rather than being dropped.
        onStartLibraryScan = { jobsViewModel.runWhenReady(MEDIA_SERVER_SCAN_JOB_ID, R.string.tv_settings_scan_started) },
        libraryScanEvents = jobsViewModel.events,
    )
}

/** The setup form on the live connection, above the rail; leaves once new credentials are saved, or on Back. */
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
