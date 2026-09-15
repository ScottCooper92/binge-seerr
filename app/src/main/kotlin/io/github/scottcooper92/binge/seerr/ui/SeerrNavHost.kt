package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
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
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsActions
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsScreen
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerSettingsPage
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen

/**
 * The one [NavDisplay]. Every screen is an entry here; the ViewModel-store decorator gives each
 * entry its own store, so a screen's ViewModel lives and dies with its place on the stack rather
 * than with the Activity.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SeerrNavHost(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val connectedState = viewModel.isConnected.collectAsStateWithLifecycle()
    val connected by connectedState
    // One directive for both the strategy and the back-arrow decision, so the two cannot disagree
    // about whether the hub is on screen beside a section.
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val hubBeside = rememberUpdatedState(connected == true && directive.maxHorizontalPartitions > 1)
    // Keyed on the root as well as the connection: a notification's link replaces the stack with one
    // rooted on HomeRoute, and it can arrive after the connection has already resolved.
    val root = backStack.firstOrNull()
    LaunchedEffect(connected, root) { backStack.settleHome(connected) }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(rememberSeerrPaneStrategy(directive)),
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        // Navigation 3 builds an entry once for its key and keeps it, content and metadata both, for as
        // long as the key is on the stack. A value captured here is the value from the frame the entry
        // was built in. So what changes later is handed over as a provider and read inside the content,
        // where reading the state is what recomposes it.
        entryProvider =
            entryProvider {
                homeEntries(backStack, connected = { connectedState.value }, hubBeside = { hubBeside.value })
                sectionEntries(backStack, showBack = { !hubBeside.value })
                detailEntries(backStack)
                serverSettingsEntries(backStack)
            },
    )
}

/**
 * Home is two routes. [HomeRoute] takes the whole window while the connection is worked out, and for
 * setup. [HubRoute] is the list pane the sections open beside. An entry's metadata cannot change
 * once it is built, so a change of layout has to be a change of route: [settleHome] swaps one for the
 * other at the root as the connection resolves.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun EntryProviderScope<NavKey>.homeEntries(
    backStack: NavBackStack<NavKey>,
    connected: () -> Boolean?,
    hubBeside: () -> Boolean,
) {
    entry<HomeRoute> {
        // Connected shows the spinner for a frame at most, while settleHome swaps the hub in.
        when (connected()) {
            false -> SetupEntry()
            else -> LoadingScreen()
        }
    }
    // The placeholder is the default section itself, so a wide window never shows an empty pane. It
    // is not an entry on the stack, which is what lets Back from it leave the app, and a narrow window
    // show the hub alone. A placeholder gets no entry scope, so its ViewModel belongs to the host.
    entry<HubRoute>(
        metadata =
            ListDetailSceneStrategy.listPane(
                detailPlaceholder = { SectionContent(DefaultSection, backStack, showBack = false) },
            ),
    ) {
        // And the other way: after a disconnect, settleHome is already swapping setup back in.
        if (connected() == true) {
            HubEntry(
                selectedSection = backStack.selectedSection(defaultShowing = hubBeside()),
                onOpenSection = { section -> backStack.openSection(section, defaultShowing = hubBeside()) },
                onOpenAccount = { id -> backStack.add(UserDetailRoute(id)) },
                onReconnect = { backStack.add(EditConnectionRoute) },
            )
        } else {
            LoadingScreen()
        }
    }
}

/** The hub's manage sections: the detail pane beside it, or the whole window on a narrow one. */
private fun EntryProviderScope<NavKey>.sectionEntries(
    backStack: NavBackStack<NavKey>,
    showBack: () -> Boolean,
) {
    entry<RequestsRoute>(metadata = DetailPane) { SectionContent(HubSection.Requests, backStack, showBack()) }
    entry<IssuesRoute>(metadata = DetailPane) { SectionContent(HubSection.Issues, backStack, showBack()) }
    entry<BlocklistRoute>(metadata = DetailPane) { SectionContent(HubSection.Blocklist, backStack, showBack()) }
    entry<UsersRoute>(metadata = DetailPane) { SectionContent(HubSection.Users, backStack, showBack()) }
    entry<SettingsRoute>(metadata = DetailPane) { SectionContent(HubSection.Settings, backStack, showBack()) }
    entry<SectionRoute>(metadata = DetailPane) { route ->
        EmptyScreen(title = stringResource(route.section.titleRes), message = stringResource(R.string.section_coming_soon))
    }
}

/** One section's screen, whether it is on the stack or standing in as the default beside the hub. */
@Composable
private fun SectionContent(
    section: HubSection,
    backStack: NavBackStack<NavKey>,
    showBack: Boolean,
) {
    val onBack: () -> Unit = { backStack.removeLastOrNull() }
    when (section) {
        HubSection.Requests ->
            RequestsEntry(onBack = onBack, showBack = showBack, onOpen = { id -> backStack.add(RequestDetailRoute(id)) })
        HubSection.Issues ->
            IssuesEntry(onBack = onBack, showBack = showBack, onOpen = { id -> backStack.add(IssueDetailRoute(id)) })
        HubSection.Blocklist -> BlocklistEntry(onBack = onBack, showBack = showBack)
        HubSection.Users ->
            UsersEntry(onBack = onBack, showBack = showBack, onOpen = { id -> backStack.add(UserDetailRoute(id)) })
        HubSection.Settings -> SettingsEntry(backStack, showBack = showBack)
    }
}

/** What a section's rows open: one request, one issue, one user and their settings. They stack in the detail pane. */
private fun EntryProviderScope<NavKey>.detailEntries(backStack: NavBackStack<NavKey>) {
    entry<RequestDetailRoute>(metadata = DetailPane) { route ->
        RequestDetailEntry(route.requestId, onBack = { backStack.removeLastOrNull() })
    }
    entry<IssueDetailRoute>(metadata = DetailPane) { route ->
        IssueDetailEntry(route.issueId, onBack = { backStack.removeLastOrNull() })
    }
    entry<UserDetailRoute>(metadata = DetailPane) { route ->
        UserDetailEntry(
            route.userId,
            onBack = { backStack.removeLastOrNull() },
            onOpenRequest = { id -> backStack.add(RequestDetailRoute(id)) },
            onOpenSettings = { backStack.add(UserSettingsRoute(route.userId)) },
        )
    }
    entry<UserSettingsRoute>(metadata = DetailPane) { route ->
        UserSettingsEntry(
            route.userId,
            onBack = { backStack.removeLastOrNull() },
            onOpenPage = { page -> backStack.add(UserSettingsPageRoute(route.userId, page)) },
        )
    }
    entry<UserSettingsPageRoute>(metadata = DetailPane) { route ->
        UserSettingsPageEntry(route.userId, route.page, onBack = { backStack.removeLastOrNull() })
    }
    entry<EditConnectionRoute>(metadata = DetailPane) { EditConnectionEntry(onDone = { backStack.removeLastOrNull() }) }
}

/** The server's own settings pages and the editors they open. They stack in the detail pane too. */
private fun EntryProviderScope<NavKey>.serverSettingsEntries(backStack: NavBackStack<NavKey>) {
    entry<ServerSettingsPageRoute>(metadata = DetailPane) { route ->
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
    entry<DiscoverSliderRoute>(metadata = DetailPane) { route ->
        DiscoverSliderEntry(route.id, onBack = { backStack.removeLastOrNull() })
    }
    entry<NotificationAgentRoute>(metadata = DetailPane) { route ->
        NotificationAgentEntry(route.agent, onBack = { backStack.removeLastOrNull() })
    }
    entry<DvrInstanceRoute>(metadata = DetailPane) { route ->
        DvrInstanceEntry(route.type, route.id, onBack = { backStack.removeLastOrNull() })
    }
    entry<OverrideRuleRoute>(metadata = DetailPane) { route ->
        OverrideRuleEntry(route.id, onBack = { backStack.removeLastOrNull() })
    }
}

@Composable
private fun HubEntry(
    selectedSection: HubSection?,
    onOpenSection: (HubSection) -> Unit,
    onOpenAccount: (Int) -> Unit,
    onReconnect: () -> Unit,
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
                onRetry = viewModel::recheck,
                onReconnect = onReconnect,
                onDisconnect = viewModel::disconnect,
            ),
    )
}

@Composable
private fun SettingsEntry(
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
    SettingsScreen(
        state = state,
        showBack = showBack,
        actions =
            SettingsActions(
                onBack = onBack,
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
