package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection
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
    // The one back-arrow rule (paneShowsBack), fed the stack's own shape (paneDepth) alongside
    // hubBeside — read here as a provider for the same reason hubBeside is: an entry's metadata is
    // fixed when it is built, so what the stack looks like later has to be read inside the content.
    val showBack = { paneShowsBack(hubBeside.value, backStack.paneDepth()) }
    // The design system's PaneContent only shares an edge while this is false, and it defaults to true. Read here
    // from the same hubBeside as the back arrow, so the two agree about whether the hub is beside a section.
    CompositionLocalProvider(LocalIsSinglePaneNav provides !hubBeside.value) {
        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies = listOf(rememberSeerrPaneStrategy(directive, backStack)),
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
                    sectionEntries(backStack, showBack = showBack)
                    detailEntries(backStack, showBack = showBack)
                    serverSettingsEntries(backStack)
                },
        )
    }
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
    // show the hub alone. What it opens puts the section on the stack first (openAboveDefault). A
    // placeholder gets no entry scope, so its ViewModel belongs to the host.
    entry<HubRoute>(
        metadata =
            ListDetailSceneStrategy.listPane(
                detailPlaceholder = {
                    DetailPaneContent { SectionContent(DefaultSection, backStack, showBack = false, open = backStack::openAboveDefault) }
                },
            ),
    ) {
        PaneContent(innerEdge = PaneEdge.End) {
            // And the other way: after a disconnect, settleHome is already swapping setup back in.
            if (connected() == true) {
                HubEntry(
                    selectedSection = backStack.selectedSection(defaultShowing = hubBeside()),
                    onOpenSection = { section -> backStack.openSection(section) },
                    onOpenAccount = { id -> backStack.add(UserDetailRoute(id)) },
                    onOpenRequest = { id -> backStack.add(RequestDetailRoute(id)) },
                    onReconnect = { backStack.add(EditConnectionRoute) },
                    developerRows = debugDeveloperRows(backStack),
                )
            } else {
                LoadingScreen()
            }
        }
    }
}

/** The hub's manage sections: the detail pane beside it, or the whole window on a narrow one. */
private fun EntryProviderScope<NavKey>.sectionEntries(
    backStack: NavBackStack<NavKey>,
    showBack: () -> Boolean,
) {
    entry<RequestsRoute>(metadata = DetailPane) { DetailPaneContent { SectionContent(HubSection.Requests, backStack, showBack()) } }
    entry<IssuesRoute>(metadata = DetailPane) { DetailPaneContent { SectionContent(HubSection.Issues, backStack, showBack()) } }
    entry<BlocklistRoute>(metadata = DetailPane) { DetailPaneContent { SectionContent(HubSection.Blocklist, backStack, showBack()) } }
    entry<UsersRoute>(metadata = DetailPane) { DetailPaneContent { SectionContent(HubSection.Users, backStack, showBack()) } }
    entry<SettingsRoute>(metadata = DetailPane) { DetailPaneContent { SectionContent(HubSection.Settings, backStack, showBack()) } }
    entry<SectionRoute>(metadata = DetailPane) { route ->
        DetailPaneContent {
            EmptyScreen(title = stringResource(route.section.titleRes), message = stringResource(R.string.section_coming_soon))
        }
    }
}

/**
 * One section's screen, whether it is on the stack or standing in as the default beside the hub. [open] pushes what
 * its rows open.
 */
@Composable
private fun SectionContent(
    section: HubSection,
    backStack: NavBackStack<NavKey>,
    showBack: Boolean,
    open: (NavKey) -> Unit = backStack::add,
) {
    val onBack: () -> Unit = { backStack.removeLastOrNull() }
    when (section) {
        HubSection.Requests ->
            RequestsEntry(
                onBack = onBack,
                showBack = showBack,
                onOpen = { id -> open(RequestDetailRoute(id)) },
                onOpenUser = { id -> open(UserDetailRoute(id)) },
            )
        HubSection.Issues ->
            IssuesEntry(onBack = onBack, showBack = showBack, onOpen = { id -> open(IssueDetailRoute(id)) })
        HubSection.Blocklist ->
            BlocklistEntry(
                onBack = onBack,
                showBack = showBack,
                onOpen = { item, canManage -> open(BlocklistDetailRoute(item, canManage)) },
            )
        HubSection.Users ->
            UsersEntry(onBack = onBack, showBack = showBack, onOpen = { id -> open(UserDetailRoute(id)) })
        HubSection.Settings -> SettingsEntry(backStack, showBack = showBack)
    }
}

/**
 * What a section's rows open: one request, one issue, one user and their settings. Most of these
 * stack above a section, where pane depth is always > 1 and the arrow is always right — but
 * [UserDetailRoute] and [EditConnectionRoute] are also opened straight off the hub itself (an
 * account card, a reconnect prompt), so they take [showBack] too. The rest accept it where wiring it
 * is a one-line forward to an existing [com.binge.designsystem.template.BingeScreenScaffold];
 * see [UserSettingsPageEntry] below for the one that does not.
 */
private fun EntryProviderScope<NavKey>.detailEntries(
    backStack: NavBackStack<NavKey>,
    showBack: () -> Boolean,
) {
    debugDetailEntries(backStack, showBack)
    entry<RequestDetailRoute>(metadata = DetailPane) { route ->
        DetailPaneContent {
            // No showBack: the hero's DetailOverlayTopBar renders its back arrow unconditionally, which a
            // request page never notices in practice — it is only ever stacked above a section or above
            // UserDetailEntry, never pushed straight onto [HubRoute], so its pane depth is always > 1.
            // Gating it for real is a design-system change (DetailOverlayTopBar's onBack is non-nullable).
            RequestDetailEntry(
                route.requestId,
                onBack = { backStack.removeLastOrNull() },
                onOpenUser = { id -> backStack.add(UserDetailRoute(id)) },
            )
        }
    }
    entry<IssueDetailRoute>(metadata = DetailPane) { route ->
        DetailPaneContent { IssueDetailEntry(route.issueId, onBack = { backStack.removeLastOrNull() }, showBack = showBack()) }
    }
    entry<BlocklistDetailRoute>(metadata = DetailPane) { route ->
        DetailPaneContent {
            // No showBack: like RequestDetailRoute, this is only ever stacked above BlocklistRoute, so
            // its pane depth is always > 1 and the hero's DetailOverlayTopBar back arrow is never redundant.
            BlocklistDetailEntry(route.item, route.canManage, onBack = { backStack.removeLastOrNull() })
        }
    }
    entry<UserDetailRoute>(metadata = DetailPane) { route ->
        DetailPaneContent {
            UserDetailEntry(
                route.userId,
                onBack = { backStack.removeLastOrNull() },
                showBack = showBack(),
                onOpenRequest = { id -> backStack.add(RequestDetailRoute(id)) },
                onOpenSettings = { backStack.add(UserSettingsRoute(route.userId)) },
            )
        }
    }
    entry<UserSettingsRoute>(metadata = DetailPane) { route ->
        DetailPaneContent {
            UserSettingsEntry(
                route.userId,
                onBack = { backStack.removeLastOrNull() },
                showBack = showBack(),
                onOpenPage = { page -> backStack.add(UserSettingsPageRoute(route.userId, page)) },
            )
        }
    }
    entry<UserSettingsPageRoute>(metadata = DetailPane) { route ->
        DetailPaneContent {
            // No showBack: every page here is stacked above UserSettingsRoute, which is itself never
            // pushed straight onto [HubRoute] (only from UserDetailEntry), so pane depth is always > 1.
            UserSettingsPageEntry(route.userId, route.page, onBack = { backStack.removeLastOrNull() })
        }
    }
    entry<EditConnectionRoute>(metadata = DetailPane) {
        DetailPaneContent { EditConnectionEntry(onDone = { backStack.removeLastOrNull() }, showBack = showBack()) }
    }
}

/**
 * The server's own settings pages and the editors they open. They stack in the detail pane too, and
 * every one of them is reached by way of [SettingsRoute] first, so pane depth here is always > 1 and
 * none of them take [showBack] — unlike [detailEntries], where two routes are reachable straight off
 * the hub. Wire a future route in here the same way [detailEntries] wires those two, if it changes that.
 */
private fun EntryProviderScope<NavKey>.serverSettingsEntries(backStack: NavBackStack<NavKey>) {
    entry<ServerSettingsPageRoute>(metadata = DetailPane) { route ->
        DetailPaneContent {
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
    }
    entry<DiscoverSliderRoute>(metadata = DetailPane) { route ->
        DetailPaneContent { DiscoverSliderEntry(route.id, onBack = { backStack.removeLastOrNull() }) }
    }
    entry<NotificationAgentRoute>(metadata = DetailPane) { route ->
        DetailPaneContent { NotificationAgentEntry(route.agent, onBack = { backStack.removeLastOrNull() }) }
    }
    entry<DvrInstanceRoute>(metadata = DetailPane) { route ->
        DetailPaneContent { DvrInstanceEntry(route.type, route.id, onBack = { backStack.removeLastOrNull() }) }
    }
    entry<OverrideRuleRoute>(metadata = DetailPane) { route ->
        DetailPaneContent { OverrideRuleEntry(route.id, onBack = { backStack.removeLastOrNull() }) }
    }
}

/**
 * The setup form on the live connection, prefilled with its address. The connection stays in
 * place until new credentials are saved, so a rejected edit changes nothing; success pops.
 *
 * Reachable straight off the hub's own reconnect prompt as well as from Settings, so [showBack]
 * follows the same rule as everywhere else in the detail pane: hidden only when this is the one
 * thing standing between the viewer and the hub they never left.
 */
@Composable
private fun EditConnectionEntry(
    onDone: () -> Unit,
    showBack: Boolean,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.beginEdit() }
    rememberEnteredEditingGuard(state, onDone)
    SetupScreen(
        state = state,
        actions = viewModel.actions(),
        title = stringResource(R.string.settings_edit_connection),
        onBack = onDone.takeIf { showBack },
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
        onAllowCleartext = ::allowCleartext,
        onLocalNetworkChanged = ::localNetworkResult,
    )

/**
 * The television screen's own wiring: Connect, whose Plex PIN is the plate's, not the browser's, and
 * the hand-off from a phone, which only a television offers.
 */
internal fun SetupViewModel.tvActions(): SetupActions =
    SetupActions(
        onEditAddress = ::editAddress,
        onInspect = ::inspect,
        onChangeServer = ::changeServer,
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
    )
