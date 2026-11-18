package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.binge.designsystem.component.BingeNavSuiteItem
import com.binge.designsystem.component.rememberBingeNavPresentation
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.sliders.HubTab
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.SeerrRoute
import com.cooper.binge.companion.seerr.ui.TitleCreditsRoute
import com.cooper.binge.companion.seerr.ui.TitleDetailRoute
import com.cooper.binge.companion.seerr.ui.hubSection
import com.cooper.binge.companion.seerr.ui.replaceAll
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import kotlinx.serialization.Serializable

/** The tabs of the discovery shell, in the order the bar shows them. */
enum class SeerrTab(
    val icon: ImageVector,
    val testTag: String,
) {
    Movies(Icons.Filled.Movie, "tab_movies"),
    Tv(Icons.Filled.Tv, "tab_tv"),
    Search(Icons.Filled.Search, "tab_search"),
    Settings(Icons.Filled.Settings, "tab_settings"),
}

/**
 * The root of each tab's own stack, bar Settings', whose root is [HubRoute]. On a phone anything a tab opens goes on the app's
 * stack above the shell. In a wide window Settings keeps the hub beside its sections, so those go on its own stack.
 */
@Serializable
data object MoviesRoot : NavKey

@Serializable
data object TvRoot : NavKey

@Serializable
data object SearchRoot : NavKey

/** The shell's state: the selected tab, and a stack for each tab holding its root. */
@Composable
internal fun rememberSeerrTabs(): TabShellState<SeerrTab> {
    val movies = rememberNavBackStack(MoviesRoot)
    val tv = rememberNavBackStack(TvRoot)
    val search = rememberNavBackStack(SearchRoot)
    val settings = rememberNavBackStack(HubRoute)
    val stacks =
        remember(movies, tv, search, settings) {
            mapOf(SeerrTab.Movies to movies, SeerrTab.Tv to tv, SeerrTab.Search to search, SeerrTab.Settings to settings)
        }
    // Settings stays the start tab for now: the hubs are new and the app opens as it always has. Moving the start to Movies is its own change.
    return rememberTabShellState(tabs = SeerrTab.entries, initial = SeerrTab.Settings, stacks = stacks)
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

private fun List<NavKey>.startsWithSection(): Boolean = getOrNull(0)?.hubSection() != null

private fun NavKey.isDiscoveryRoute(): Boolean = this is TitleDetailRoute || this is TitleCreditsRoute || this is DiscoverGridRoute

/**
 * Keeps a section where the window can show it. A wide window shows Settings' hub beside its sections inside the tab, so
 * sections the app's stack holds (a link's, or ones opened on a phone-width window) move to Settings' own stack. A narrow window
 * shows a section over the whole window, so the ones Settings holds move back on to the app's stack. Only a stack that is the
 * shell and sections is moved: a title page or a grid above them stays where it was.
 */
internal fun TabShellState<SeerrTab>.settleSections(
    appStack: NavBackStack<NavKey>,
    wide: Boolean,
) {
    val settings = stack(SeerrTab.Settings)
    if (wide) {
        val above = appStack.drop(1)
        if (above.startsWithSection() && above.none { it.isDiscoveryRoute() }) {
            settings.replaceAll(listOf(HubRoute) + above)
            appStack.popToRoot()
            show(SeerrTab.Settings)
        }
    } else if (settings.size > 1 && appStack.size == 1 && selected == SeerrTab.Settings) {
        appStack.addAll(settings.drop(1))
        settings.popToRoot()
    }
}

@Composable
private fun SeerrTab.label(): String =
    stringResource(
        when (this) {
            SeerrTab.Movies -> R.string.shell_tab_movies
            SeerrTab.Tv -> R.string.shell_tab_tv
            SeerrTab.Search -> R.string.shell_tab_search
            SeerrTab.Settings -> R.string.shell_tab_settings
        },
    )

/** The bar's destinations, labelled in the user's language. */
@Composable
internal fun seerrTabItems(): List<BingeNavSuiteItem> =
    SeerrTab.entries.map { BingeNavSuiteItem(key = it, label = it.label(), icon = it.icon, testTag = it.testTag) }

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
    settings: @Composable () -> Unit,
) {
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
