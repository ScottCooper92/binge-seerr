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
import com.binge.designsystem.component.BingeNavPresentation
import com.binge.designsystem.component.BingeNavSuiteItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.SeerrRoute
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

/** The root of each tab's own stack. Anything a tab opens goes on the app's stack above the shell, not on these. */
@Serializable
data object MoviesRoot : NavKey

@Serializable
data object TvRoot : NavKey

@Serializable
data object SearchRoot : NavKey

@Serializable
data object SettingsRoot : NavKey

/** The shell's state: the selected tab, and a stack for each tab holding its root. */
@Composable
internal fun rememberSeerrTabs(): TabShellState<SeerrTab> {
    val movies = rememberNavBackStack(MoviesRoot)
    val tv = rememberNavBackStack(TvRoot)
    val search = rememberNavBackStack(SearchRoot)
    val settings = rememberNavBackStack(SettingsRoot)
    val stacks =
        remember(movies, tv, search, settings) {
            mapOf(SeerrTab.Movies to movies, SeerrTab.Tv to tv, SeerrTab.Search to search, SeerrTab.Settings to settings)
        }
    // Settings is the start tab while the others are placeholders; it becomes the first tab once the Movies hub lands.
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
 * The tab bar over each tab's root: the Settings tab is the hub, [settings], and the others are placeholders until their
 * screens land. Only a root is drawn here. What a tab opens is an entry on the app's stack above this one, so it takes the
 * whole window and the bar is hidden until Back pops it. Back at another tab's root returns to the start tab, and Back at the
 * start tab's root leaves the app.
 */
@Composable
internal fun SeerrTabShell(
    tabs: TabShellState<SeerrTab>,
    modifier: Modifier = Modifier,
    settings: @Composable () -> Unit,
) {
    TabShell(
        state = tabs,
        items = seerrTabItems(),
        // One place to choose how the bar looks: the floating pill, or the plain bottom bar.
        presentation = BingeNavPresentation.FloatingBar,
        modifier = modifier,
    ) { tab ->
        when (tab) {
            SeerrTab.Settings -> settings()
            else -> TabPlaceholder(tab)
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
