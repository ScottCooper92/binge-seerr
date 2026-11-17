package com.cooper.binge.companion.seerr.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.binge.designsystem.component.BingeNavPresentation
import com.binge.designsystem.component.BingeNavSuiteItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.ui.HomeUiState
import com.cooper.binge.companion.seerr.ui.HomeViewModel
import com.cooper.binge.companion.seerr.ui.SeerrNavHost
import com.cooper.binge.companion.seerr.ui.SeerrRoute
import com.cooper.binge.companion.seerr.ui.rememberExitOnce
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

/** The root of a tab that has no screen yet; each is replaced by the screen that lands for it. */
@Serializable
data object MoviesRoot : NavKey

@Serializable
data object TvRoot : NavKey

@Serializable
data object SearchRoot : NavKey

/**
 * The shell's state. The Settings tab's stack is [settingsStack], the one the app has always had, so the hub, its sections and
 * every link keep working over it unchanged.
 */
@Composable
internal fun rememberSeerrTabs(settingsStack: NavBackStack<NavKey>): TabShellState<SeerrTab> {
    val movies = rememberNavBackStack(MoviesRoot)
    val tv = rememberNavBackStack(TvRoot)
    val search = rememberNavBackStack(SearchRoot)
    return rememberTabShellState(
        tabs = SeerrTab.entries,
        initial = SeerrTab.Settings,
        stacks =
            mapOf(
                SeerrTab.Movies to movies,
                SeerrTab.Tv to tv,
                SeerrTab.Search to search,
                SeerrTab.Settings to settingsStack,
            ),
    )
}

/** A link's routes go on the Settings stack, and Settings is what shows, so Back from a linked page is the hub. */
internal fun TabShellState<SeerrTab>.openLink(routes: List<SeerrRoute>) {
    stack(SeerrTab.Settings).replaceAll(routes)
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
 * What the app shows. With the discovery flag off, in a window wide enough for panes, or while no server is connected, it is
 * [SeerrNavHost] as it has always been. Only a connected phone-width window with the flag on gets the tabs, because a wider
 * window keeps today's hub beside its sections and the tabs would crowd it. Setup and a rejected sign-in need the whole window.
 */
internal fun showsTabs(
    discoveryEnabled: Boolean,
    compact: Boolean,
    state: HomeUiState,
): Boolean = discoveryEnabled && compact && (state == HomeUiState.Connected || state == HomeUiState.Disconnecting)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun SeerrRoot(
    settingsStack: NavBackStack<NavKey>,
    tabs: TabShellState<SeerrTab>,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    home: HomeViewModel = hiltViewModel(),
) {
    val state by home.uiState.collectAsStateWithLifecycle()
    val compact = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()).maxHorizontalPartitions <= 1
    if (showsTabs(discoveryEnabled = LocalDiscoveryEnabled.current, compact = compact, state = state)) {
        SeerrTabShell(settingsStack, tabs, onExit, modifier)
    } else {
        SeerrNavHost(settingsStack, onExit, modifier)
    }
}

@Composable
private fun SeerrTabShell(
    settingsStack: NavBackStack<NavKey>,
    tabs: TabShellState<SeerrTab>,
    onExit: () -> Unit,
    modifier: Modifier,
) {
    val exit = rememberExitOnce(onExit)
    // Settings has its own stack, whose navigation takes Back and leaves from its root; the others leave from theirs.
    BackHandler(enabled = tabs.selected != SeerrTab.Settings) { exit() }
    TabShell(
        state = tabs,
        items = seerrTabItems(),
        // One place to choose how the bar looks: the plain bottom bar, or the design system's floating pill.
        presentation = BingeNavPresentation.BottomBar,
        modifier = modifier,
    ) { tab ->
        when (tab) {
            SeerrTab.Settings -> SeerrNavHost(settingsStack, exit)
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
