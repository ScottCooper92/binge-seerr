package com.cooper.binge.companion.seerr.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge
import com.cooper.binge.companion.seerr.ui.hub.HubSection
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen

/**
 * The one [NavDisplay]. Every screen is an entry here; the ViewModel-store decorator gives each
 * entry its own store, so a screen's ViewModel lives and dies with its place on the stack rather
 * than with the Activity.
 */
@Composable
fun SeerrNavHost(
    backStack: NavBackStack<NavKey>,
    onExit: () -> Unit,
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
    // The default section is off the stack, so Back from it leaves; the host's own Back shares the once-only exit.
    val exit = rememberExitOnce(onExit)
    SeerrPaneHost(backStack, connected, exit, modifier, defaultSection = { DefaultSectionPane(backStack, exit) }) { panes ->
        homeEntries(backStack, connected = { connectedState.value }, panes = panes, onExit = exit)
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
    onExit: () -> Unit,
) {
    entry<HomeRoute> {
        // Connected shows the spinner for a frame at most, while settleHome swaps the hub in.
        when (connected()) {
            false -> SetupEntry()
            else -> LoadingScreen()
        }
    }
    // The placeholder is the default section, so a wide window never shows an empty pane. Off the stack,
    // Back from it leaves the app and a narrow window shows the hub alone (openAboveDefault stacks it first).
    // A placeholder gets no entry scope, so its ViewModel belongs to the host.
    entry<HubRoute>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DefaultSectionPane(backStack, onExit) }) + ThreePaneHub,
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
}

/**
 * [DefaultSection] standing in beside the hub while no section is open: what it opens puts it on the stack first.
 * It is not an entry, so it gets no entry scope, and its ViewModel belongs to the host.
 */
@Composable
private fun DefaultSectionPane(
    backStack: NavBackStack<NavKey>,
    onExit: () -> Unit,
) = DetailPaneContent {
    SectionContent(DefaultSection, backStack, showBack = false, open = backStack::openAboveDefault, onBack = onExit)
}

/**
 * One section's screen, whether it is on the stack or standing in as the default beside the hub. [open] pushes what
 * its rows open, and [onBack] leaves: a pop for a section on the stack, the activity's exit for the stand-in.
 */
@Composable
private fun SectionContent(
    section: HubSection,
    backStack: NavBackStack<NavKey>,
    showBack: Boolean,
    open: (NavKey) -> Unit = backStack::add,
    onBack: () -> Unit = { backStack.pop() },
) {
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
        HubSection.Settings -> SettingsEntry(backStack, showBack = showBack, open = open, onBack = onBack)
    }
}

/**
 * What a section's rows open: one request, one issue, one user and their settings. Most of these
 * stack above a section, where pane depth is always > 1 and the arrow is always right — but
 * [UserDetailRoute] and [EditConnectionRoute] are also opened straight off the hub itself (the
 * bar's account action, a reconnect prompt), so they take [showBack] too. The rest accept it where wiring it
 * is a one-line forward to an existing [com.binge.designsystem.template.BingeScreenScaffold];
 * see [UserSettingsPageEntry] below for the one that does not.
 */
private fun EntryProviderScope<NavKey>.detailEntries(
    backStack: NavBackStack<NavKey>,
    showBack: () -> Boolean,
) {
    debugDetailEntries(backStack, showBack)
    // Each page leaves through popIfTop with its own key, for its back arrow and for leaving on its own (a delete, a
    // save) alike: a late call from a page already on its way out never takes the one now on top.
    entry<RequestDetailRoute>(metadata = DetailPane) { route ->
        DetailPaneContent {
            // No showBack: DetailOverlayTopBar always draws its back arrow, and its onBack is non-nullable.
            // Harmless here: a request page is always stacked above a section or UserDetailEntry, so its
            // pane depth is always > 1. Gating it for real is a design-system change.
            RequestDetailEntry(
                route.requestId,
                onBack = { backStack.popIfTop(route) },
                onOpenUser = { id -> backStack.add(UserDetailRoute(id)) },
            )
        }
    }
    entry<IssueDetailRoute>(metadata = DetailPane) { route ->
        DetailPaneContent { IssueDetailEntry(route.issueId, onBack = { backStack.popIfTop(route) }, showBack = showBack()) }
    }
    entry<BlocklistDetailRoute>(metadata = DetailPane) { route ->
        DetailPaneContent {
            // No showBack: like RequestDetailRoute, this is only ever stacked above BlocklistRoute, so
            // its pane depth is always > 1 and the hero's DetailOverlayTopBar back arrow is never redundant.
            BlocklistDetailEntry(route.item, route.canManage, onBack = { backStack.popIfTop(route) })
        }
    }
    entry<UserDetailRoute>(metadata = DetailPane) { route ->
        DetailPaneContent {
            UserDetailEntry(
                route.userId,
                onBack = { backStack.popIfTop(route) },
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
                onBack = { backStack.popIfTop(route) },
                showBack = showBack(),
                onOpenPage = { page -> backStack.add(UserSettingsPageRoute(route.userId, page)) },
            )
        }
    }
    entry<UserSettingsPageRoute>(metadata = DetailPane) { route ->
        DetailPaneContent {
            // No showBack: every page here is stacked above UserSettingsRoute, which is itself never
            // pushed straight onto [HubRoute] (only from UserDetailEntry), so pane depth is always > 1.
            UserSettingsPageEntry(route.userId, route.page, onBack = { backStack.popIfTop(route) })
        }
    }
    entry<EditConnectionRoute>(metadata = DetailPane) { route ->
        DetailPaneContent { EditConnectionEntry(onDone = { backStack.popIfTop(route) }, showBack = showBack()) }
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
                onBack = { backStack.popIfTop(route) },
                onOpenPage = { page -> backStack.add(ServerSettingsPageRoute(page)) },
                onOpenInstance = { type, id -> backStack.add(DvrInstanceRoute(type, id)) },
                onOpenRule = { id -> backStack.add(OverrideRuleRoute(id)) },
                onOpenAgent = { agent -> backStack.add(NotificationAgentRoute(agent)) },
                onOpenSlider = { id -> backStack.add(DiscoverSliderRoute(id)) },
            )
        }
    }
    entry<DiscoverSliderRoute>(metadata = DetailPane) { route ->
        DetailPaneContent { DiscoverSliderEntry(route.id, onBack = { backStack.popIfTop(route) }) }
    }
    entry<NotificationAgentRoute>(metadata = DetailPane) { route ->
        DetailPaneContent { NotificationAgentEntry(route.agent, onBack = { backStack.popIfTop(route) }) }
    }
    entry<DvrInstanceRoute>(metadata = DetailPane) { route ->
        DetailPaneContent { DvrInstanceEntry(route.type, route.id, onBack = { backStack.popIfTop(route) }) }
    }
    entry<OverrideRuleRoute>(metadata = DetailPane) { route ->
        DetailPaneContent { OverrideRuleEntry(route.id, onBack = { backStack.popIfTop(route) }) }
    }
}
