package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.binge.designsystem.component.BingeNavSuiteItem
import com.binge.designsystem.component.rememberBingeNavPresentation
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.ui.DefaultSection
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.SeerrRoute
import com.cooper.binge.companion.seerr.ui.hubSection
import com.cooper.binge.companion.seerr.ui.replaceAll
import com.cooper.binge.companion.seerr.ui.route
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import kotlinx.serialization.Serializable

/** The tabs of the discovery shell, in the order the bar shows them. */
enum class SeerrTab(
    val icon: ImageVector,
    val testTag: String,
) {
    Movies(Icons.Filled.Movie, "tab_movies"),
    Tv(Icons.Filled.Tv, "tab_tv"),
    Discover(Icons.Filled.Explore, "tab_discover"),
    Search(Icons.Filled.Search, "tab_search"),
    Settings(Icons.Filled.Settings, "tab_settings"),
}

/** The root of each tab's own stack. Anything a tab opens goes on the app's stack above the shell, not on these. */
@Serializable
data object MoviesRoot : NavKey

@Serializable
data object TvRoot : NavKey

@Serializable
data object SearchRoot : NavKey

@Serializable
data object SettingsRoot : NavKey

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

/**
 * Discovery was turned on or off: the app starts over, with the start tab showing, every tab at its root and nothing open over
 * the shell, so no screen from the other mode is left on top.
 */
internal fun TabShellState<SeerrTab>.restart(appStack: NavBackStack<NavKey>) {
    appStack.popToRoot()
    SeerrTab.entries.forEach { stack(it).popToRoot() }
    show(startTab)
}

/**
 * Keeps a wide window's open section off the app's stack while another tab shows. A section on that stack is drawn by
 * [ShellPanesStrategy] inside the Settings tab, so anything pushed over it from Movies, TV or Search would be swallowed by
 * that scene. Leaving Settings moves the section's chain onto the Settings tab's own stack, and coming back puts it
 * on the app's stack again, so choosing Settings again from another tab finds the section open. Its entries are made anew, so its
 * filter and scroll start over. The default section seeded in three panes does not block the restore; the chain replaces it.
 */
internal fun TabShellState<SeerrTab>.settleSectionStash(appStack: NavBackStack<NavKey>) {
    val stash = stack(SeerrTab.Settings)
    val above = appStack.drop(1)
    if (selected != SeerrTab.Settings) {
        if (above.firstOrNull()?.hubSection() == null) return
        stash.replaceAll(stash.take(1) + above)
        appStack.replaceAll(appStack.take(1))
    } else if (stash.size > 1) {
        if (above.isEmpty() || above == listOf(DefaultSection.route())) appStack.replaceAll(appStack.take(1) + stash.drop(1))
        stash.replaceAll(stash.take(1))
    }
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

/** Whether Discover is a tab: on a tablet-width window (sw600dp) only, as in Binge. On a phone the grids open from the hubs. */
@Composable
@ReadOnlyComposable
internal fun showDiscoverTab(): Boolean = booleanResource(R.bool.shell_show_discover)

/** The bar's destinations, labelled in the user's language. */
@Composable
internal fun seerrTabItems(): List<BingeNavSuiteItem> =
    SeerrTab.entries
        .filter {
            it != SeerrTab.Discover || showDiscoverTab()
        }.map { BingeNavSuiteItem(key = it, label = it.label(), icon = it.icon, testTag = it.testTag) }

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
    search: @Composable () -> Unit = { TabPlaceholder(SeerrTab.Search) },
    grid: @Composable () -> Unit = { TabPlaceholder(SeerrTab.Discover) },
    settings: @Composable () -> Unit,
) {
    // A window that stops being tablet-width takes Discover out of the bar: leave it for the start tab.
    val discoverShown = showDiscoverTab()
    LaunchedEffect(discoverShown, tabs.selected) { if (!discoverShown && tabs.selected == SeerrTab.Discover) tabs.show(tabs.startTab) }
    TabShell(
        state = tabs,
        items = seerrTabItems(),
        // The design system's own choice: the floating pill, or the rail on a landscape tablet, as Binge does.
        presentation = rememberBingeNavPresentation(),
        modifier = modifier,
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
