package com.cooper.binge.companion.seerr.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.binge.designsystem.shell.RestartOnSessionEnd
import com.binge.designsystem.shell.TabShellState
import com.binge.designsystem.shell.nothingOpenBeside
import com.binge.designsystem.shell.popToRoot
import com.binge.designsystem.shell.settleStash
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.ui.discover.request.LocalServerSettingsLinks
import com.cooper.binge.companion.seerr.ui.discover.request.rememberServerSettingsLinks
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle
import com.cooper.binge.companion.seerr.ui.discover.title.rememberOpenTitle
import com.cooper.binge.companion.seerr.ui.discover.title.titleEntries
import com.cooper.binge.companion.seerr.ui.grid.DiscoverTabEntry
import com.cooper.binge.companion.seerr.ui.grid.discoverGridEntries
import com.cooper.binge.companion.seerr.ui.hub.AccountBarViewModel
import com.cooper.binge.companion.seerr.ui.hub.HubSection
import com.cooper.binge.companion.seerr.ui.hubs.DiscoverHubEntry
import com.cooper.binge.companion.seerr.ui.requests.LocalRequestDetail
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailFromViewModel
import com.cooper.binge.companion.seerr.ui.search.title.TitleSearchEntry
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab
import com.cooper.binge.companion.seerr.ui.shell.SeerrTabShell
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen

/**
 * The one [NavDisplay]. Every screen is an entry here; the ViewModel-store decorator gives each
 * entry its own store, so a screen's ViewModel lives and dies with its place on the stack rather
 * than with the Activity.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SeerrNavHost(
    backStack: NavBackStack<NavKey>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    tabs: TabShellState<SeerrTab>? = null,
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
    // The tab bar is for every window with the flag on. A wide one draws a section beside the hub in the Settings tab.
    val shell = tabs != null && LocalDiscoveryEnabled.current
    // And on the stack's size: a link rooted on the same HomeRoute changes neither of the others, and signed out its pages are dropped.
    LaunchedEffect(connected, root, shell, backStack.size) { backStack.settleHome(connected, shell) }
    // A page parked on a hidden tab belongs to the session it was opened in: a sign-out starts the shell over, so the next one does not open it.
    if (tabs != null) RestartOnSessionEnd(connected, tabs, backStack)
    // The default section is off the stack, so Back from it leaves; the host's own Back shares the once-only exit.
    val exit = rememberExitOnce(onExit)
    // A disconnect still waiting on the server is not a connection to ask a review of: its card would show as the user leaves.
    val askable = remember(state) { derivedStateOf { if (state.value == HomeUiState.Disconnecting) false else state.value.connected() } }
    ReviewPromptOnReturnToRoot(connected = askable.value, stackDepth = backStack.size)
    CompositionLocalProvider(
        LocalOpenTitle provides rememberOpenTitle(backStack),
        LocalServerSettingsLinks provides rememberServerSettingsLinks(backStack),
        LocalRequestDetail provides RequestDetailFromViewModel,
    ) {
        SeerrPaneHost(
            backStack,
            connected,
            exit,
            modifier,
        ) { panes ->
            homeEntries(backStack, connected = { connectedState.value }, panes = panes, onExit = exit, tabs = tabs)
            settingsEntries(backStack, connected = { connectedState.value }, panes = panes, onExit = exit)
            discoverGridEntries(backStack, panes.motion)
            titleEntries(backStack, panes.motion)
        }
    }
}

private fun HomeUiState.connected(): Boolean? =
    when (this) {
        HomeUiState.Resolving -> null
        HomeUiState.Setup -> false
        HomeUiState.Connected, HomeUiState.Disconnecting, HomeUiState.Reconnect -> true
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
    tabs: TabShellState<SeerrTab>?,
) {
    entry<HomeRoute> {
        // Connected shows the spinner for a frame at most, while settleHome swaps the hub in.
        when (connected()) {
            false -> SetupEntry()
            else -> LoadingScreen()
        }
    }
    // On a phone the shell takes the whole window and what a tab opens covers it from above. A wide window's strategy draws the
    // open section beside the hub, inside the Settings tab.
    entry<ShellRoute>(metadata = ShellPane) {
        if (connected() == true && tabs != null) {
            val hubBeside = panes.hubBeside()
            val account by hiltViewModel<AccountBarViewModel>().uiState.collectAsStateWithLifecycle()
            val stack = backStack.toList()
            LaunchedEffect(tabs.selected, hubBeside, stack) {
                if (hubBeside) {
                    tabs.settleStash(backStack, SeerrTab.Settings, NavKey::opensBesideHub, NavKey::isDefaultStandIn)
                }
            }
            SeerrTabShell(
                tabs,
                account = account,
                backToStart =
                    nothingOpenBeside(
                        stack,
                        NavKey::isPaneRoot,
                        NavKey::isDefaultStandIn,
                    ),
                discover = {
                    DiscoverHubEntry(
                        it,
                        onOpenGrid = { grid, title -> backStack.push(DiscoverGridRoute(grid.kind, grid.preset, title)) },
                        onOpenRequests = {
                            if (panes.hubBeside()) tabs.show(SeerrTab.Settings)
                            backStack.openSection(HubSection.Requests)
                        },
                    )
                },
                search = {
                    TitleSearchEntry(
                        onOpenGrid = { grid, title -> backStack.push(DiscoverGridRoute(grid.kind, grid.preset, title)) },
                    )
                },
                grid = {
                    DiscoverTabEntry(tabs.stack(SeerrTab.Discover))
                },
                // The shell pops the tab's own stack; what the tab opened sits on the app's, so Settings closes it here.
                onReselect = { if (it == SeerrTab.Settings) backStack.popToRoot() },
                settings = {
                    if (panes.hubBeside()) {
                        SettingsPanes(
                            hub = {
                                PaneContent(innerEdge = PaneEdge.End) {
                                    SettingsHub(backStack, panes, backStack.selectedSection(defaultShowing = true))
                                }
                            },
                            defaultSection = { DefaultSectionPane(backStack, onExit) },
                        )
                    } else {
                        SettingsHub(backStack, panes, null)
                    }
                },
            )
        } else {
            LoadingScreen()
        }
    }
}
