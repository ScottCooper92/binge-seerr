package com.cooper.binge.companion.seerr.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.binge.designsystem.component.BingeSnackbarHost
import com.cooper.binge.companion.seerr.ADMITS_UNVERIFIED_CALLERS
import com.cooper.binge.companion.seerr.tip.TipHost
import com.cooper.binge.companion.seerr.ui.hub.DeveloperRow
import com.cooper.binge.companion.seerr.ui.hub.HubActions
import com.cooper.binge.companion.seerr.ui.hub.HubScreen
import com.cooper.binge.companion.seerr.ui.hub.HubSection
import com.cooper.binge.companion.seerr.ui.hub.HubViewModel
import com.cooper.binge.companion.seerr.ui.search.SettingsSearchHost
import com.cooper.binge.companion.seerr.ui.search.SwitchPage
import com.cooper.binge.companion.seerr.ui.search.SwitchPageEvents
import com.cooper.binge.companion.seerr.ui.search.searchToggle
import com.cooper.binge.companion.seerr.ui.settings.SettingsActions
import com.cooper.binge.companion.seerr.ui.settings.SettingsScreen
import com.cooper.binge.companion.seerr.ui.settings.SettingsViewModel
import com.cooper.binge.companion.seerr.ui.settings.ThisAppActions
import com.cooper.binge.companion.seerr.ui.settings.ThisAppScreen
import com.cooper.binge.companion.seerr.ui.settings.ThisAppViewModel
import com.cooper.homelab.core.uiandroid.search.SettingsSearchScreen

@Composable
internal fun HubEntry(
    selectedSection: HubSection?,
    onOpenSection: (HubSection) -> Unit,
    onOpenAccount: (Int) -> Unit,
    onOpenSearch: () -> Unit,
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
                onOpenSearch = onOpenSearch,
                onOpenRequest = onOpenRequest,
                onRetry = viewModel::recheck,
                onReconnect = onReconnect,
                onDisconnect = viewModel::disconnect,
                onDismissBingeHint = viewModel::dismissBingeHint,
                onRefresh = viewModel::refresh,
                developerRows = developerRows,
            ),
    )
}

@Composable
internal fun SettingsEntry(
    backStack: NavBackStack<NavKey>,
    showBack: Boolean,
    open: (NavKey) -> Unit = backStack::add,
    onBack: () -> Unit = { backStack.pop() },
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Refetched on every arrival, so returning from Edit connection shows the new server.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    val actions =
        SettingsActions(
            onBack = onBack,
            onEditConnection = { open(EditConnectionRoute) },
            onOpenPage = { page -> open(ServerSettingsPageRoute(page)) },
            // The home swaps to setup on the credentials clearing; leaving Settings is what lets it show. Only Settings
            // itself is taken off: a second tap while it leaves finds something else on top and takes nothing.
            onDisconnect = {
                viewModel.disconnect()
                backStack.popIfTop(SettingsRoute)
            },
        )
    SettingsScreen(
        state = state,
        showBack = showBack,
        actions = actions,
    )
}

@Composable
internal fun ThisAppEntry(
    showBack: Boolean,
    open: (NavKey) -> Unit,
    onBack: () -> Unit,
    viewModel: ThisAppViewModel = hiltViewModel(),
    tipHost: TipHost = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tip by tipHost.model.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    // Re-read on every arrival, so Binge installed while this page was away shows on return.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    val actions =
        ThisAppActions(
            onBack = onBack,
            onToggleShakeToReport = viewModel::setShakeToReport,
            onToggleShareUsageData = viewModel::setShareUsageData,
            onToggleSendCrashReports = viewModel::setSendCrashReports,
            onToggleSignal = viewModel::setSignal,
            onNotificationAccessChanged = viewModel::recheckNotificationAccess,
            onSetPollInterval = viewModel::setPollInterval,
            onOpenLicences = { open(LicencesRoute) },
            onOpenDiagnostics = { open(DiagnosticsRoute) },
        )
    ThisAppScreen(
        state = state,
        showBack = showBack,
        actions = actions,
        tip = tip,
        tipEvents = tipHost.model.events,
        onGiveTip = { step -> activity?.let { tipHost.model.give(it, step) } },
    )
}

/**
 * The settings search over the whole window. A result opens its page above the search, which stays on the stack, so Back from
 * the page returns to the search.
 */
@Composable
internal fun SettingsSearchEntry(
    onOpen: (SeerrRoute) -> Unit,
    onBack: () -> Unit,
    host: SettingsSearchHost = hiltViewModel(),
) {
    val state by host.model.state.collectAsStateWithLifecycle()
    val switches by host.searchTogglesEnabled.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var inUse by remember { mutableStateOf(emptySet<SwitchPage>()) }
    Box {
        SettingsSearchScreen(
            state = state,
            entries = host.entries,
            icon = { it.target.icon },
            onOpen = { entry, viewer ->
                host.model.remember(entry)
                onOpen(entry.target.target.route(viewer.userId, entry.row))
            },
            onBack = onBack,
            onRetry = host.model::retry,
            onClearRecent = host.model::clearRecent,
            toggle =
                if (switches) {
                    { entry, viewer -> searchToggle(entry, viewer.isAdmin) { page -> inUse = inUse + page } }
                } else {
                    null
                },
        )
        inUse.forEach { SwitchPageEvents(it, snackbar) }
        BingeSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
