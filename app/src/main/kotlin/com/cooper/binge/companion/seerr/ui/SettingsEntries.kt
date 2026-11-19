package com.cooper.binge.companion.seerr.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge
import com.binge.designsystem.component.ProvideRowFocus
import com.cooper.binge.companion.seerr.ui.hub.HubSection
import com.cooper.binge.companion.seerr.ui.settings.diagnostics.DiagnosticsEntry
import com.cooper.binge.companion.seerr.ui.settings.licences.LicencesEntry
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen

/**
 * The settings search, beside the hub on the hub route and over the whole window on a narrow one (#2343). It is a detail pane,
 * not a section: on the shell it stays out of [ShellPanesStrategy]'s section scene. What was open beneath it stays on the stack,
 * so Back brings it back. A result opens its page above the search through [PaneLayout.openFromSearch], and the search stays on
 * the stack, so Back from the page returns to the search as it was.
 */
internal fun EntryProviderScope<NavKey>.searchEntries(
    backStack: NavBackStack<NavKey>,
    panes: PaneLayout,
) {
    entry<SettingsSearchRoute>(metadata = DetailPane + panes.motion.detail) { route ->
        SettingsSearchEntry(
            onOpen = { target -> panes.openFromSearch(backStack, target) },
            onBack = { backStack.popIfTop(route) },
        )
    }
}

/**
 * The hub's manage sections: the detail pane beside it on a wide window, or the whole window on a narrow one.
 */
internal fun EntryProviderScope<NavKey>.sectionEntries(
    backStack: NavBackStack<NavKey>,
    panes: PaneLayout,
) {
    val section = DetailPane + SectionPane + panes.motion.tab
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
    entry<ThisAppRoute>(
        metadata = section,
    ) { route ->
        DetailPaneContent {
            ProvideRowFocus(
                route.row,
            ) { SectionContent(HubSection.ThisApp, backStack, panes.showBack(), open) }
        }
    }
}

/**
 * [DefaultSection] standing in beside the hub while no section is open: what it opens puts it on the stack first.
 * It is not an entry, so it gets no entry scope, and its ViewModel belongs to the host.
 */
@Composable
internal fun DefaultSectionPane(
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
internal fun SectionContent(
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
                onOpenServices = { open(ServerSettingsPageRoute(ServerSettingsPage.Services)) },
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
        HubSection.ThisApp -> ThisAppEntry(showBack = showBack, open = open, onBack = onBack)
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
internal fun EntryProviderScope<NavKey>.detailEntries(
    backStack: NavBackStack<NavKey>,
    showBack: () -> Boolean,
    motion: NavMotionSpecs,
) {
    debugDetailEntries(backStack, showBack, motion)
    // Each page leaves through popIfTop with its own key, for its back arrow and for leaving on its own (a delete, a
    // save) alike: a late call from a page already on its way out never takes the one now on top.
    entry<RequestDetailRoute>(metadata = DetailPane + motion.tab) { route ->
        DetailPaneContent {
            // No showBack: DetailOverlayTopBar always draws its back arrow, and its onBack is non-nullable.
            // Harmless here: a request page is always stacked above a section or UserDetailEntry, so its
            // pane depth is always > 1. Gating it for real is a design-system change.
            RequestDetailEntry(
                route.requestId,
                onBack = { backStack.popIfTop(route) },
                onOpenUser = { id -> backStack.add(UserDetailRoute(id)) },
                onOpenServices = { backStack.add(ServerSettingsPageRoute(ServerSettingsPage.Services)) },
            )
        }
    }
    entry<IssueDetailRoute>(metadata = DetailPane + motion.tab) { route ->
        DetailPaneContent { IssueDetailEntry(route.issueId, onBack = { backStack.popIfTop(route) }, showBack = showBack()) }
    }
    entry<BlocklistDetailRoute>(metadata = DetailPane + motion.tab) { route ->
        DetailPaneContent {
            // No showBack: like RequestDetailRoute, this is only ever stacked above BlocklistRoute, so
            // its pane depth is always > 1 and the hero's DetailOverlayTopBar back arrow is never redundant.
            BlocklistDetailEntry(route.item, route.canManage, onBack = { backStack.popIfTop(route) })
        }
    }
    entry<UserDetailRoute>(metadata = DetailPane + motion.tab) { route ->
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
    entry<UserSettingsRoute>(metadata = DetailPane + motion.tab) { route ->
        DetailPaneContent {
            UserSettingsEntry(
                route.userId,
                onBack = { backStack.popIfTop(route) },
                showBack = showBack(),
                onOpenPage = { page -> backStack.add(UserSettingsPageRoute(route.userId, page)) },
            )
        }
    }
    entry<UserSettingsPageRoute>(metadata = DetailPane + motion.tab) { route ->
        DetailPaneContent {
            // No showBack: the page's own scaffold reads the pane depth through paneBackOrNull. Stacked above UserSettingsRoute
            // or above the settings search the arrow shows, and Back is what it came from.
            UserSettingsPageEntry(route.userId, route.page, route.row, onBack = { backStack.popIfTop(route) })
        }
    }
    entry<EditConnectionRoute>(metadata = DetailPane + motion.tab) { route ->
        DetailPaneContent { EditConnectionEntry(onDone = { backStack.popIfTop(route) }, showBack = showBack()) }
    }
}

/**
 * The server's own settings pages and the editors they open. They stack in the detail pane too. Reached by way of [SettingsRoute]
 * (or [ThisAppRoute], for licences) the pane depth is > 1, and the settings search opens a page above the search, whose Back is the
 * search. None of them take [showBack]: each page's scaffold decides its arrow from the depth, through the design system's
 * `paneBackOrNull`, so a page reached either way is right. A route added here needs its scaffold to do the same.
 */
internal fun EntryProviderScope<NavKey>.serverSettingsEntries(
    backStack: NavBackStack<NavKey>,
    motion: NavMotionSpecs,
) {
    entry<ServerSettingsPageRoute>(metadata = DetailPane + motion.tab) { route ->
        DetailPaneContent {
            ServerSettingsPageEntry(
                page = route.page,
                row = route.row,
                onBack = { backStack.popIfTop(route) },
                onOpenPage = { page -> backStack.add(ServerSettingsPageRoute(page)) },
                onOpenInstance = { type, id -> backStack.add(DvrInstanceRoute(type, id)) },
                onOpenRule = { id -> backStack.add(OverrideRuleRoute(id)) },
                onOpenAgent = { agent -> backStack.add(NotificationAgentRoute(agent)) },
                onOpenSlider = { id -> backStack.add(DiscoverSliderRoute(id)) },
            )
        }
    }
    entry<LicencesRoute>(metadata = DetailPane + motion.tab) { route ->
        DetailPaneContent { LicencesEntry(onBack = { backStack.popIfTop(route) }) }
    }
    entry<DiagnosticsRoute>(metadata = DetailPane + motion.tab) { route ->
        DetailPaneContent { DiagnosticsEntry(onBack = { backStack.popIfTop(route) }) }
    }
    entry<DiscoverSliderRoute>(metadata = DetailPane + motion.tab) { route ->
        DetailPaneContent { DiscoverSliderEntry(route.id, onBack = { backStack.popIfTop(route) }) }
    }
    entry<NotificationAgentRoute>(metadata = DetailPane + motion.tab) { route ->
        DetailPaneContent { NotificationAgentEntry(route.agent, onBack = { backStack.popIfTop(route) }) }
    }
    entry<DvrInstanceRoute>(metadata = DetailPane + motion.tab) { route ->
        DetailPaneContent { DvrInstanceEntry(route.type, route.id, onBack = { backStack.popIfTop(route) }) }
    }
    entry<OverrideRuleRoute>(metadata = DetailPane + motion.tab) { route ->
        DetailPaneContent { OverrideRuleEntry(route.id, onBack = { backStack.popIfTop(route) }) }
    }
}

/**
 * The hub as a list pane, and everything a section opens beside it. [SeerrNavHost] registers them on the app's stack; a wide
 * window's Settings tab registers the same on its own stack, so the tab bar stays on screen while a section is open.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun EntryProviderScope<NavKey>.settingsEntries(
    backStack: NavBackStack<NavKey>,
    connected: () -> Boolean?,
    panes: PaneLayout,
    onExit: () -> Unit,
) {
    // The placeholder is the default section, so a wide window never shows an empty pane. Off the stack,
    // Back from it leaves the app and a narrow window shows the hub alone (openAboveDefault stacks it first).
    // A placeholder gets no entry scope, so its ViewModel belongs to the host.
    entry<HubRoute>(
        metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DefaultSectionPane(backStack, onExit) }),
    ) {
        PaneContent(innerEdge = PaneEdge.End) {
            // And the other way: after a disconnect, settleHome is already swapping setup back in.
            if (connected() == true) {
                SettingsHub(backStack, panes, backStack.selectedSection(defaultShowing = panes.hubBeside()))
            } else {
                LoadingScreen()
            }
        }
    }
    sectionEntries(backStack, panes)
    searchEntries(backStack, panes)
    detailEntries(backStack, showBack = panes.showBack, motion = panes.motion)
    serverSettingsEntries(backStack, panes.motion)
}

/** The hub itself: [selectedSection] is the row marked as open. */
@Composable
internal fun SettingsHub(
    backStack: NavBackStack<NavKey>,
    panes: PaneLayout,
    selectedSection: HubSection?,
) {
    HubEntry(
        selectedSection = selectedSection,
        onOpenSection = { section -> backStack.openSection(section) },
        onOpenAccount = { id -> panes.open(backStack, UserDetailRoute(id)) },
        onOpenSearch = { backStack.push(SettingsSearchRoute) },
        onOpenRequest = { id -> panes.open(backStack, RequestDetailRoute(id)) },
        onReconnect = { panes.open(backStack, EditConnectionRoute) },
        developerRows = debugDeveloperRows(backStack),
    )
}
