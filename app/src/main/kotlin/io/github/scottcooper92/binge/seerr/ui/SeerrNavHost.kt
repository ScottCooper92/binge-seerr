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
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsActions
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsScreen
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerAgent
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerSettingsPage
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
                        onOpenAccount = { id -> backStack.add(UserDetailRoute(id)) },
                        onReconnect = { backStack.add(EditConnectionRoute) },
                        onOpenSection = { section ->
                            backStack.add(
                                when (section) {
                                    HubSection.Requests -> RequestsRoute
                                    HubSection.Issues -> IssuesRoute
                                    HubSection.Users -> UsersRoute
                                    HubSection.Blocklist -> BlocklistRoute
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
                entry<IssuesRoute> {
                    IssuesEntry(onBack = { backStack.removeLastOrNull() }, onOpen = { id -> backStack.add(IssueDetailRoute(id)) })
                }
                entry<IssueDetailRoute> { route -> IssueDetailEntry(route.issueId, onBack = { backStack.removeLastOrNull() }) }
                entry<BlocklistRoute> { BlocklistEntry(onBack = { backStack.removeLastOrNull() }) }
                entry<UsersRoute> {
                    UsersEntry(onBack = { backStack.removeLastOrNull() }, onOpen = { id -> backStack.add(UserDetailRoute(id)) })
                }
                entry<UserDetailRoute> { route ->
                    UserDetailEntry(
                        route.userId,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenRequest = { id -> backStack.add(RequestDetailRoute(id)) },
                        onOpenSettings = { backStack.add(UserSettingsRoute(route.userId)) },
                    )
                }
                entry<UserSettingsRoute> { route ->
                    UserSettingsEntry(
                        route.userId,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenPage = { page -> backStack.add(UserSettingsPageRoute(route.userId, page)) },
                    )
                }
                entry<UserSettingsPageRoute> { route ->
                    UserSettingsPageEntry(route.userId, route.page, onBack = { backStack.removeLastOrNull() })
                }
                entry<ServerSettingsPageRoute> { route ->
                    ServerSettingsPageEntry(
                        page = route.page,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenPage = { page -> backStack.add(ServerSettingsPageRoute(page)) },
                        onOpenInstance = { type, id -> backStack.add(DvrInstanceRoute(type, id)) },
                        onOpenRule = { id -> backStack.add(OverrideRuleRoute(id)) },
                        onOpenAgent = { agent -> backStack.add(NotificationAgentRoute(agent)) },
                        onOpenSlider = { id -> backStack.add(DiscoverSliderRoute(id)) },
                    )
                }
                entry<DiscoverSliderRoute> { route -> DiscoverSliderEntry(route.id, onBack = { backStack.removeLastOrNull() }) }
                entry<NotificationAgentRoute> { route -> NotificationAgentEntry(route.agent, onBack = { backStack.removeLastOrNull() }) }
                entry<DvrInstanceRoute> { route -> DvrInstanceEntry(route.type, route.id, onBack = { backStack.removeLastOrNull() }) }
                entry<OverrideRuleRoute> { route -> OverrideRuleEntry(route.id, onBack = { backStack.removeLastOrNull() }) }
                entry<SettingsRoute> {
                    SettingsEntry(
                        onBack = { backStack.removeLastOrNull() },
                        onEditConnection = { backStack.add(EditConnectionRoute) },
                        onOpenServerSettings = { backStack.add(ServerSettingsPageRoute(ServerSettingsPage.General)) },
                        onOpenMediaServer = { backStack.add(ServerSettingsPageRoute(ServerSettingsPage.MediaServer)) },
                        onOpenServices = { backStack.add(ServerSettingsPageRoute(ServerSettingsPage.Services)) },
                        onOpenInstance = { type, id -> backStack.add(DvrInstanceRoute(type, id)) },
                        onOpenAgents = { backStack.add(ServerSettingsPageRoute(ServerSettingsPage.NotificationAgents)) },
                        onOpenAgent = { agent -> backStack.add(NotificationAgentRoute(agent)) },
                        onOpenSliders = { backStack.add(ServerSettingsPageRoute(ServerSettingsPage.DiscoverSliders)) },
                        onOpenNetwork = { backStack.add(ServerSettingsPageRoute(ServerSettingsPage.Network)) },
                        onOpenMetadata = { backStack.add(ServerSettingsPageRoute(ServerSettingsPage.Metadata)) },
                        onOpenJobs = { backStack.add(ServerSettingsPageRoute(ServerSettingsPage.Jobs)) },
                        onOpenCache = { backStack.add(ServerSettingsPageRoute(ServerSettingsPage.Cache)) },
                        onOpenLogs = { backStack.add(ServerSettingsPageRoute(ServerSettingsPage.Logs)) },
                        onOpenAbout = { backStack.add(ServerSettingsPageRoute(ServerSettingsPage.About)) },
                    )
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
    onOpenAccount: (Int) -> Unit,
    onReconnect: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val connected by viewModel.isConnected.collectAsStateWithLifecycle()
    when (connected) {
        null -> LoadingScreen()
        false -> SetupEntry()
        true -> HubEntry(onOpenSection, onOpenAccount, onReconnect)
    }
}

@Composable
private fun HubEntry(
    onOpenSection: (HubSection) -> Unit,
    onOpenAccount: (Int) -> Unit,
    onReconnect: () -> Unit,
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
                onOpenAccount = onOpenAccount,
                onRetry = viewModel::recheck,
                onReconnect = onReconnect,
                onDisconnect = viewModel::disconnect,
            ),
    )
}

@Composable
private fun SettingsEntry(
    onBack: () -> Unit,
    onEditConnection: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onOpenMediaServer: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenInstance: (ServiceType, Int) -> Unit,
    onOpenAgents: () -> Unit,
    onOpenAgent: (ServerAgent) -> Unit,
    onOpenSliders: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenMetadata: () -> Unit,
    onOpenJobs: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
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
                onOpenServerSettings = onOpenServerSettings,
                onOpenMediaServer = onOpenMediaServer,
                onOpenServices = onOpenServices,
                onOpenInstance = onOpenInstance,
                onOpenAgents = onOpenAgents,
                onOpenAgent = onOpenAgent,
                onOpenSliders = onOpenSliders,
                onOpenNetwork = onOpenNetwork,
                onOpenMetadata = onOpenMetadata,
                onOpenJobs = onOpenJobs,
                onOpenCache = onOpenCache,
                onOpenLogs = onOpenLogs,
                onOpenAbout = onOpenAbout,
                onToggleSignal = viewModel::setSignal,
                onNotificationAccessChanged = viewModel::recheckNotificationAccess,
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

internal fun SetupViewModel.actions(): SetupActions =
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
