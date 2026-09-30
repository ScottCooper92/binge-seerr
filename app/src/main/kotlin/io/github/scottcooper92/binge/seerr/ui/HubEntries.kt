package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.scottcooper92.binge.seerr.ui.hub.HubActions
import io.github.scottcooper92.binge.seerr.ui.hub.HubScreen
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection
import io.github.scottcooper92.binge.seerr.ui.hub.HubViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsActions
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsScreen
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsUiState
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.JobsActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.JobsUiState
import io.github.scottcooper92.binge.seerr.ui.settings.server.JobsViewModel
import kotlinx.coroutines.flow.emptyFlow

@Composable
internal fun HubEntry(
    selectedSection: HubSection?,
    onOpenSection: (HubSection) -> Unit,
    onOpenAccount: (Int) -> Unit,
    onOpenRequest: (Int) -> Unit,
    onReconnect: () -> Unit,
    onOpenDeveloperOptions: (() -> Unit)?,
    viewModel: HubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The downloading poll and the auto-retry run while the hub is composed. Beside a section that
    // means all the time it is open, which is the point of keeping it there: its badge counts and
    // its downloading strip are what the admin is watching while they work in the pane next door.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    HubScreen(
        state = state,
        selectedSection = selectedSection,
        actions =
            HubActions(
                onOpenSection = onOpenSection,
                onOpenAccount = onOpenAccount,
                onOpenRequest = onOpenRequest,
                onRetry = viewModel::recheck,
                onReconnect = onReconnect,
                onDisconnect = viewModel::disconnect,
                onOpenDeveloperOptions = onOpenDeveloperOptions,
            ),
    )
}

@Composable
internal fun SettingsEntry(
    backStack: NavBackStack<NavKey>,
    showBack: Boolean,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Refetched on every arrival, so returning from Edit connection shows the new server.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    val onBack = {
        backStack.removeLastOrNull()
        Unit
    }
    val actions =
        SettingsActions(
            onBack = onBack,
            onEditConnection = { backStack.add(EditConnectionRoute) },
            onOpenPage = { page -> backStack.add(ServerSettingsPageRoute(page)) },
            onOpenInstance = { type, id -> backStack.add(DvrInstanceRoute(type, id)) },
            onOpenAgent = { agent -> backStack.add(NotificationAgentRoute(agent)) },
            onToggleSignal = viewModel::setSignal,
            onNotificationAccessChanged = viewModel::recheckNotificationAccess,
            onToggleShakeToReport = viewModel::setShakeToReport,
            onToggleShareUsageData = viewModel::setShareUsageData,
            onToggleSendCrashReports = viewModel::setSendCrashReports,
            // The home swaps to setup on the credentials clearing; leaving Settings is what lets it show.
            onDisconnect = {
                viewModel.disconnect()
                onBack()
            },
        )
    // The jobs view model is only stood up once the System group it feeds can actually appear —
    // admin-only, the same gate the group itself reads — so a non-admin viewer never fires the
    // `/settings/jobs` call the server would refuse. Mirrors ui/tv/TvEntries.kt's TvAdminSettingsEntry gate.
    if ((state as? SettingsUiState.Ready)?.config != null) {
        SettingsAdminEntry(state = state, showBack = showBack, actions = actions)
    } else {
        SettingsScreen(
            state = state,
            showBack = showBack,
            jobs = JobsUiState.Loading,
            jobEvents = emptyFlow(),
            jobActions = JobsActions(onRun = {}, onCancel = {}, onSchedule = { _, _ -> }),
            actions = actions,
        )
    }
}

@Composable
private fun SettingsAdminEntry(
    state: SettingsUiState,
    showBack: Boolean,
    actions: SettingsActions,
    jobsViewModel: JobsViewModel = hiltViewModel(),
) {
    val jobs by jobsViewModel.uiState.collectAsStateWithLifecycle()
    // Refetched on every arrival, same as the main view model above, so a job's next-run/running
    // detail doesn't go stale on a trip to Cache, Logs or Edit connection and back.
    DisposableEffect(jobsViewModel) {
        jobsViewModel.reload()
        onDispose {}
    }
    SettingsScreen(
        state = state,
        showBack = showBack,
        jobs = jobs,
        jobEvents = jobsViewModel.events,
        jobActions = JobsActions(onRun = jobsViewModel::run, onCancel = jobsViewModel::cancel, onSchedule = jobsViewModel::schedule),
        actions = actions,
    )
}
