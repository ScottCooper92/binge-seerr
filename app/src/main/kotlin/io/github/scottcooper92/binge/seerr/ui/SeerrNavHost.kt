package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.hub.HubActions
import io.github.scottcooper92.binge.seerr.ui.hub.HubScreen
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection
import io.github.scottcooper92.binge.seerr.ui.hub.HubViewModel
import io.github.scottcooper92.binge.seerr.ui.requests.ListRefresh
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailScreen
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsScreen
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsActions
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsScreen
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsViewModel
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen

/**
 * The one [NavDisplay]. Every screen is an entry here; the ViewModel-store decorator gives each
 * entry its own store, so a screen's ViewModel lives and dies with its place on the stack rather
 * than with the Activity.
 */
@Composable
fun SeerrNavHost(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<HomeRoute> {
                    HomeEntry(
                        onOpenSection = { section ->
                            backStack.add(
                                when (section) {
                                    HubSection.Requests -> RequestsRoute
                                    HubSection.Settings -> SettingsRoute
                                    else -> SectionRoute(section)
                                },
                            )
                        },
                    )
                }
                entry<RequestsRoute> {
                    RequestsEntry(onBack = { backStack.removeLastOrNull() }, onOpen = { id -> backStack.add(RequestDetailRoute(id)) })
                }
                entry<RequestDetailRoute> { route -> RequestDetailEntry(route.requestId, onBack = { backStack.removeLastOrNull() }) }
                entry<SettingsRoute> {
                    SettingsEntry(onBack = { backStack.removeLastOrNull() }, onEditConnection = { backStack.add(EditConnectionRoute) })
                }
                entry<EditConnectionRoute> { EditConnectionEntry(onDone = { backStack.removeLastOrNull() }) }
                entry<SectionRoute> { route ->
                    EmptyScreen(title = stringResource(route.section.titleRes), message = stringResource(R.string.section_coming_soon))
                }
            },
    )
}

/** The home swaps between setup and the hub on the saved credentials, so neither has to know the other. */
@Composable
private fun HomeEntry(
    onOpenSection: (HubSection) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val connected by viewModel.isConnected.collectAsStateWithLifecycle()
    when (connected) {
        null -> LoadingScreen()
        false -> SetupEntry()
        true -> HubEntry(onOpenSection)
    }
}

@Composable
private fun HubEntry(
    onOpenSection: (HubSection) -> Unit,
    viewModel: HubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The downloading poll and the auto-retry run only while the hub is on screen.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    HubScreen(
        state = state,
        actions =
            HubActions(
                onOpenSection = onOpenSection,
                onRetry = viewModel::recheck,
                onDisconnect = viewModel::disconnect,
            ),
    )
}

@Composable
private fun RequestDetailEntry(
    requestId: Int,
    onBack: () -> Unit,
    viewModel: RequestDetailViewModel =
        hiltViewModel<RequestDetailViewModel, RequestDetailViewModel.Factory>(creationCallback = { factory -> factory.create(requestId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RequestDetailScreen(
        state = state,
        events = viewModel.moderation.events,
        actions =
            RequestDetailActions(
                onBack = onBack,
                onRetry = viewModel::reload,
                onReportIssue = viewModel::reportIssue,
                onDismissReport = viewModel::dismissReport,
                onApprove = { viewModel.moderation.approve(requestId) },
                onRetryRequest = { viewModel.moderation.retry(requestId) },
                onDecline = { block ->
                    (state as? RequestDetailUiState.Ready)?.let { viewModel.moderation.decline(it.detail.item, block) }
                },
                onRemove = { block -> (state as? RequestDetailUiState.Ready)?.let { viewModel.moderation.remove(it.detail.item, block) } },
            ),
    )
}

@Composable
private fun RequestsEntry(
    onBack: () -> Unit,
    onOpen: (Int) -> Unit,
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The chip counts refetch on arrival, so a moderation elsewhere shows without a poll.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    RequestsScreen(
        state = state,
        requestsFor = viewModel::requests,
        events = viewModel.moderation.events,
        refresh = ListRefresh(viewModel.listVersion, viewModel::shouldRefresh),
        actions =
            RequestsActions(
                onBack = onBack,
                onFilterChange = viewModel::setFilter,
                onSortChange = viewModel::setSort,
                onOpen = { item -> onOpen(item.id) },
                onOpenActions = viewModel::openActions,
                onDismissActions = viewModel::dismissActions,
                onApprove = viewModel.moderation::approve,
                onRetry = viewModel.moderation::retry,
                onDecline = viewModel.moderation::decline,
                onRemove = viewModel.moderation::remove,
            ),
    )
}

@Composable
private fun SettingsEntry(
    onBack: () -> Unit,
    onEditConnection: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Refetched on every arrival, so returning from Edit connection shows the new server.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    SettingsScreen(
        state = state,
        actions =
            SettingsActions(
                onBack = onBack,
                onEditConnection = onEditConnection,
                // The home swaps to setup on the credentials clearing; leaving Settings is what lets it show.
                onDisconnect = {
                    viewModel.disconnect()
                    onBack()
                },
            ),
    )
}

/**
 * The setup form on the live connection, prefilled with its address. The connection stays in
 * place until new credentials are saved, so a rejected edit changes nothing; success pops.
 */
@Composable
private fun EditConnectionEntry(
    onDone: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.beginEdit() }
    LaunchedEffect(state) { if (state is SetupUiState.Connected) onDone() }
    SetupScreen(
        state = state,
        actions = viewModel.actions(),
        title = stringResource(R.string.settings_edit_connection),
        onBack = onDone,
    )
}

@Composable
private fun SetupEntry(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SetupScreen(state = state, actions = viewModel.actions())
}

private fun SetupViewModel.actions(): SetupActions =
    SetupActions(
        onEditAddress = ::editAddress,
        onInspect = ::inspect,
        onChangeServer = ::changeServer,
        onEditForm = ::editForm,
        onConnect = ::connect,
        onPlexLaunched = ::plexLaunched,
        onCancelLink = ::cancelLink,
        onRequestPasswordReset = ::requestPasswordReset,
    )
