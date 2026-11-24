package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.binge.designsystem.component.BingeNavSuiteItem
import com.binge.designsystem.component.rememberBingeNavPresentation
import com.binge.designsystem.shell.TabShell
import com.binge.designsystem.shell.TabShellState
import com.binge.designsystem.shell.rememberTabShellState
import com.binge.designsystem.shell.replaceAll
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.SeerrRoute
import com.cooper.binge.companion.seerr.ui.hub.AccountBarState
import com.cooper.binge.companion.seerr.ui.hub.HubAccount
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen

/**
 * The shell's state: the selected tab, and a stack for each tab holding its root. With [discovery] on it starts on Movies, which is
 * also the tab Back returns to; off, there are no discovery tabs and it starts on Settings as the app always has. The selected
 * tab survives a recreate, so this only decides the first one.
 */
@Composable
internal fun rememberSeerrTabs(discovery: Boolean): TabShellState<SeerrTab> {
    val movies = rememberNavBackStack(MoviesRoot)
    val tv = rememberNavBackStack(TvRoot)
    val discover = rememberNavBackStack(DiscoverGridRoute(TitleKind.Movie, GridPreset.Popular))
    val search = rememberNavBackStack(SearchRoot)
    val settings = rememberNavBackStack(SettingsRoot)
    val stacks =
        remember(movies, tv, discover, search, settings) {
            mapOf(
                SeerrTab.Movies to movies,
                SeerrTab.Tv to tv,
                SeerrTab.Discover to discover,
                SeerrTab.Search to search,
                SeerrTab.Settings to settings,
            )
        }
    return rememberTabShellState(tabs = SeerrTab.entries, initial = if (discovery) SeerrTab.Movies else SeerrTab.Settings, stacks = stacks)
}

/**
 * A link's routes replace the app's stack, and Settings is the tab under them, so Back walks the link's own chain and then
 * lands on the Settings hub. The other tabs keep what they had.
 */
internal fun TabShellState<SeerrTab>.openLink(
    appStack: NavBackStack<NavKey>,
    routes: List<SeerrRoute>,
) {
    appStack.replaceAll(routes)
    show(SeerrTab.Settings)
}

@Composable
private fun SeerrTab.label(): String =
    stringResource(
        when (this) {
            SeerrTab.Movies -> R.string.shell_tab_movies
            SeerrTab.Tv -> R.string.shell_tab_tv
            SeerrTab.Discover -> R.string.shell_tab_discover
            SeerrTab.Search -> R.string.shell_tab_search
            SeerrTab.Settings -> R.string.shell_tab_settings
        },
    )

/**
 * The bar's destinations, labelled in the user's language. The Settings item is the account's avatar once there is one: the photo, or
 * the initials where the server has none, and the Settings icon only until the account is known.
 */
@Composable
internal fun seerrTabItems(account: HubAccount? = null): List<BingeNavSuiteItem> =
    SeerrTab.entries
        .filter {
            it != SeerrTab.Discover || showDiscoverTab()
        }.map { tab ->
            val avatar = account.takeIf { tab == SeerrTab.Settings }
            BingeNavSuiteItem(
                key = tab,
                label = tab.label(),
                icon = tab.icon,
                testTag = tab.testTag,
                avatarName = avatar?.name,
                avatarUrl = avatar?.avatarUrl,
                largeAvatar = avatar != null,
            )
        }

/**
 * The tab bar over each tab's root: the Settings tab is the hub, [settings], Movies and TV are the discovery hubs, [discover],
 * and Search is [search]. Only a root is drawn here. What a tab opens is an entry on the app's stack above this one, so it takes the
 * whole window and the bar is hidden until Back pops it. Back at another tab's root returns to the start tab, and Back at the
 * start tab's root leaves the app.
 */
@Composable
internal fun SeerrTabShell(
    tabs: TabShellState<SeerrTab>,
    discover: @Composable (HubTab) -> Unit,
    modifier: Modifier = Modifier,
    account: AccountBarState = AccountBarState.Loading,
    search: @Composable () -> Unit = { TabPlaceholder(SeerrTab.Search) },
    grid: @Composable () -> Unit = { TabPlaceholder(SeerrTab.Discover) },
    backToStart: Boolean = true,
    settings: @Composable () -> Unit,
) {
    // A window that stops being tablet-width takes Discover out of the bar: leave it for the start tab.
    val discoverShown = showDiscoverTab()
    LaunchedEffect(discoverShown, tabs.selected) { if (!discoverShown && tabs.selected == SeerrTab.Discover) tabs.show(tabs.startTab) }
    TabShell(
        state = tabs,
        items = seerrTabItems((account as? AccountBarState.Ready)?.bar?.account),
        // The design system's own choice: the floating pill, or the rail on a landscape tablet, as Binge does.
        presentation = rememberBingeNavPresentation(),
        modifier = modifier,
        backToStart = backToStart,
    ) { tab ->
        when (tab) {
            SeerrTab.Search -> search()
            SeerrTab.Settings -> settings()
            SeerrTab.Movies -> discover(HubTab.Movies)
            SeerrTab.Tv -> discover(HubTab.Series)
            SeerrTab.Discover -> grid()
        }
    }
}

/** A tab whose screen has not landed. Only a build with the flag on shows it. */
@Composable
internal fun TabPlaceholder(
    tab: SeerrTab,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        EmptyScreen(
            message = stringResource(R.string.shell_placeholder_message),
            title = tab.label(),
            icon = tab.icon,
        )
    }
}
