package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.binge.designsystem.LocalPaneDepth
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
@Composable
fun SeerrNavHost(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    if (state.value == HomeUiState.Reconnect) {
        // Nothing behind it: the screens would only fail one by one. A new sign-in brings them back on its own (#810).
        ScopedViewModels("reconnect") { ReconnectEntry(onDisconnect = viewModel::disconnect, modifier = modifier) }
        return
    }
    // The layout below only asks whether a server is saved: null while that is unknown.
    val connectedState = remember(state) { derivedStateOf { state.value.connected() } }
    val connected by connectedState
    // Keyed on the root as well as the connection: a notification's link replaces the stack with one
    // rooted on HomeRoute, and it can arrive after the connection has already resolved.
    val root = backStack.firstOrNull()
    LaunchedEffect(connected, root) { backStack.settleHome(connected) }
    SeerrPaneHost(backStack, connected, modifier, defaultSection = { DefaultSectionPane(backStack) }) { panes ->
        homeEntries(backStack, connected = { connectedState.value }, panes = panes)
        sectionEntries(backStack, panes)
        detailEntries(backStack, showBack = panes.showBack)
        serverSettingsEntries(backStack)
    }
}

private fun HomeUiState.connected(): Boolean? =
    when (this) {
        HomeUiState.Resolving -> null
        HomeUiState.Setup -> false
        HomeUiState.Connected, HomeUiState.Reconnect -> true
    }

/**
 * What an entry asks of the layout it is in, as providers rather than values. Navigation 3 builds an entry once for its
 * key and keeps it, content and metadata both, for as long as the key is on the stack. A value captured when it is
 * built is the value from that frame. So what changes later is read inside the content, where reading the state is
 * what recomposes it.
 *
 * [hubBeside]: the hub is on screen beside the pane. [showBack]: the pane's Back arrow, by [paneShowsBack].
 * [threePane]: a landscape tablet's three panes, where what a section opens replaces what it had open (#1110).
 */
internal class PaneLayout(
    val hubBeside: () -> Boolean,
    val showBack: () -> Boolean,
    val threePane: () -> Boolean,
) {
    /** Opens [route] from a section's list or the hub: in place of the open item in three panes, else above it. */
    fun open(
        backStack: NavBackStack<NavKey>,
        route: NavKey,
    ) = if (threePane()) backStack.openBeside(route) else backStack.add(route)
}

/**
 * The [NavDisplay] and the pane locals around it, apart from the entries that fill it. [SeerrNavHost] passes the real
 * entries; [entries] is a parameter so a test can pin the panes with stand-ins,
 * cheaply and on their own. [defaultSection] is the section a wide window shows while none is open.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun SeerrPaneHost(
    backStack: NavBackStack<NavKey>,
    connected: Boolean?,
    modifier: Modifier = Modifier,
    defaultSection: @Composable () -> Unit = {},
    entries: EntryProviderScope<NavKey>.(panes: PaneLayout) -> Unit,
) {
    // One directive for both the strategy and the back-arrow decision, so the two cannot disagree
    // about whether the hub is on screen beside a section.
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val hubBeside = rememberUpdatedState(connected == true && directive.maxHorizontalPartitions > 1)
    val threePane = rememberUpdatedState(hubBeside.value && isThreePaneWindow())
    // Three panes show a section in the middle all the time, so the default one goes on the stack, not a placeholder:
    // its list then keeps its state when it opens an item. Back from it leaves the app, as from the placeholder (#815).
    LaunchedEffect(threePane.value, backStack.size) {
        if (threePane.value && backStack.toList() == listOf(HubRoute)) backStack.add(DefaultSection.route())
    }
    // The one back-arrow rule (paneShowsBack), fed the stack's own shape (paneDepth) alongside
    // hubBeside — read here as a provider for the same reason hubBeside is: an entry's metadata is
    // fixed when it is built, so what the stack looks like later has to be read inside the content.
    val showBack = { paneShowsBack(hubBeside.value, backStack.paneDepth()) }
    // The design system's PaneContent only shares an edge while this is false, and it defaults to true. Read here
    // from the same hubBeside as the back arrow, so the two agree about whether the hub is beside a section.
    // Its paneBackOrNull reads the depth beside it, so that is provided from the same stack showBack reads:
    // left at its default of 1, a stacked screen beside the hub would lose its Back arrow.
    CompositionLocalProvider(
        LocalIsSinglePaneNav provides !hubBeside.value,
        LocalPaneDepth provides backStack.paneDepth(),
    ) {
        NavDisplay(
            backStack = backStack,
            modifier = modifier,
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies = listOf(rememberSeerrPaneStrategy(directive, backStack, threePane.value, defaultSection)),
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider = entryProvider { entries(PaneLayout({ hubBeside.value }, showBack, { threePane.value })) },
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
    panes: PaneLayout,
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
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DefaultSectionPane(backStack) }) + ThreePaneHub,
    ) {
        PaneContent(innerEdge = PaneEdge.End) {
            // And the other way: after a disconnect, settleHome is already swapping setup back in.
            if (connected() == true) {
                HubEntry(
                    selectedSection = backStack.selectedSection(defaultShowing = panes.hubBeside()),
                    onOpenSection = { section -> backStack.openSection(section) },
                    onOpenAccount = { id -> panes.open(backStack, UserDetailRoute(id)) },
                    onOpenRequest = { id -> panes.open(backStack, RequestDetailRoute(id)) },
                    onReconnect = { panes.open(backStack, EditConnectionRoute) },
                    developerRows = debugDeveloperRows(backStack),
                )
            } else {
                LoadingScreen()
            }
        }
    }
}

/**
 * The hub's manage sections: the detail pane beside it, the middle of three panes on a landscape tablet, or the whole
 * window on a narrow one.
 */
private fun EntryProviderScope<NavKey>.sectionEntries(
    backStack: NavBackStack<NavKey>,
    panes: PaneLayout,
) {
    val section = DetailPane + ThreePaneSection
    val open: (NavKey) -> Unit = { route -> panes.open(backStack, route) }
    entry<RequestsRoute>(
        metadata = section,
    ) { DetailPaneContent { SectionContent(HubSection.Requests, backStack, panes.showBack(), open) } }
    entry<IssuesRoute>(metadata = section) { DetailPaneContent { SectionContent(HubSection.Issues, backStack, panes.showBack(), open) } }
    entry<BlocklistRoute>(
        metadata = section,
    ) { DetailPaneContent { SectionContent(HubSection.Blocklist, backStack, panes.showBack(), open) } }
    entry<UsersRoute>(metadata = section) { DetailPaneContent { SectionContent(HubSection.Users, backStack, panes.showBack(), open) } }
    entry<SettingsRoute>(
        metadata = section,
    ) { DetailPaneContent { SectionContent(HubSection.Settings, backStack, panes.showBack(), open) } }
    entry<SectionRoute>(metadata = section) { route ->
        DetailPaneContent {
            EmptyScreen(title = stringResource(route.section.titleRes), message = stringResource(R.string.section_coming_soon))
        }
    }
}

/**
 * [DefaultSection] standing in beside the hub while no section is open: what it opens puts it on the stack first.
 * It is not an entry, so it gets no entry scope, and its ViewModel belongs to the host.
 */
@Composable
private fun DefaultSectionPane(backStack: NavBackStack<NavKey>) =
    DetailPaneContent { SectionContent(DefaultSection, backStack, showBack = false, open = backStack::openAboveDefault) }

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
        HubSection.Settings -> SettingsEntry(backStack, showBack = showBack, open = open)
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
