package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.scottcooper92.binge.seerr.ADMITS_UNVERIFIED_CALLERS
import io.github.scottcooper92.binge.seerr.ui.hub.DeveloperRow
import io.github.scottcooper92.binge.seerr.ui.hub.HubActions
import io.github.scottcooper92.binge.seerr.ui.hub.HubScreen
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection
import io.github.scottcooper92.binge.seerr.ui.hub.HubViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsActions
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsScreen
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsViewModel

@Composable
internal fun HubEntry(
    selectedSection: HubSection?,
    onOpenSection: (HubSection) -> Unit,
    onOpenAccount: (Int) -> Unit,
    onOpenRequest: (Int) -> Unit,
    onReconnect: () -> Unit,
    developerRows: List<DeveloperRow>,
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
        admitsUnverifiedCallers = ADMITS_UNVERIFIED_CALLERS,
        actions =
            HubActions(
                onOpenSection = onOpenSection,
                onOpenAccount = onOpenAccount,
                onOpenRequest = onOpenRequest,
                onRetry = viewModel::recheck,
                onReconnect = onReconnect,
                onDisconnect = viewModel::disconnect,
                onDismissBingeHint = viewModel::dismissBingeHint,
                developerRows = developerRows,
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
    SettingsScreen(state = state, showBack = showBack, actions = actions)
}
