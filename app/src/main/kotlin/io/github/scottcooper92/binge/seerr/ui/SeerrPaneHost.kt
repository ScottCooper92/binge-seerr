package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.LocalPaneDepth
import com.binge.designsystem.paneShowsBack

/**
 * What an entry asks of the layout it is in, as providers rather than values. Navigation 3 builds an entry once for its
 * key and keeps it, content and metadata both, for as long as the key is on the stack. A value captured when it is
 * built is the value from that frame. So what changes later is read inside the content, where reading the state is
 * what recomposes it.
 *
 * [hubBeside]: the hub is on screen beside the pane. [showBack]: the pane's Back arrow, by the design system's
 * [paneShowsBack], the same rule its `paneBackOrNull` reads (#1083).
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
