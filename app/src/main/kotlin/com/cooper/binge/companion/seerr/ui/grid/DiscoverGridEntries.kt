package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.NavMotionSpecs
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle
import com.cooper.binge.companion.seerr.ui.hub.AccountBarState
import com.cooper.binge.companion.seerr.ui.hub.AccountBarViewModel
import com.cooper.binge.companion.seerr.ui.popIfTop
import com.cooper.binge.companion.seerr.ui.replaceTop
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab
import java.time.LocalDate

/** The grid a "see all" opens. It has no pane metadata: it covers the tab bar, and Back returns to the hub it came from. */
internal fun EntryProviderScope<NavKey>.discoverGridEntries(
    backStack: NavBackStack<NavKey>,
    motion: NavMotionSpecs,
    onOpenAccount: (Int) -> Unit,
) {
    entry<DiscoverGridRoute>(metadata = motion.detail) { route ->
        DiscoverGridEntry(route, onBack = {
            backStack.popIfTop(route)
        }, backStack = backStack, onOpenAccount = onOpenAccount)
    }
}

/** A card opens the title's page, or with the flag off the hand-off [LocalOpenTitle] falls back to. */
@Composable
internal fun DiscoverGridEntry(
    route: DiscoverGridRoute,
    onBack: (() -> Unit)?,
    backStack: NavBackStack<NavKey>,
    onOpenAccount: (Int) -> Unit,
    title: String? = route.title,
    reselectKey: Any? = null,
) {
    val tomorrow = remember { LocalDate.now().plusDays(1).toString() }
    val model =
        hiltViewModel<DiscoverGridViewModel, DiscoverGridViewModel.Factory>(
            key = route.toString(),
            creationCallback = { it.create(route.kind, route.preset, route.title, tomorrow, route.filters, route.sort) },
        )
    val state by model.uiState.collectAsStateWithLifecycle()
    val openTitle by rememberUpdatedState(LocalOpenTitle.current)
    LaunchedEffect(model) {
        model.events.collect { event ->
            when (event) {
                is DiscoverGridEvent.Open -> openTitle(event.card.kind, event.card.tmdbId, event.card.webUrl)
            }
        }
    }
    val bar = hiltViewModel<AccountBarViewModel>()
    val account by bar.uiState.collectAsStateWithLifecycle()
    DiscoverGridScreen(
        accountBar = (account as? AccountBarState.Ready)?.bar,
        onOpenAccount = onOpenAccount,
        kind = route.kind,
        title = title,
        reselectKey = reselectKey,
        state = state,
        actions =
            DiscoverGridActions(
                onBack = onBack,
                onClearFilter = model::onClearFilter,
                onClearGroup = model::onClearGroup,
                onRetry = model::onRetry,
                onLoadMore = model::onLoadMore,
                onOpenTitle = model::onOpenTitle,
                onApply = model::onApply,
                onSwitchKind = { kind, sort, filters ->
                    backStack.replaceTop(route, route.carriedTo(kind, sort, filters))
                },
                loads = FilterLoads(model::onProvidersNeeded, model::onKeywordQuery, model::onKeywordsNeeded),
            ),
    )
}

/**
 * The Discover tab: the grid at its root, as a tab of its own with no Back arrow. The tab's stack holds the one route, and the
 * Content choice in the filter sheet reopens it as the other kind in place, so the tab picks Movies or TV shows as Binge's does.
 */
@Composable
internal fun DiscoverTabEntry(
    stack: NavBackStack<NavKey>,
    onOpenAccount: (Int) -> Unit,
) {
    val route = stack.lastOrNull() as? DiscoverGridRoute ?: return
    DiscoverGridEntry(
        route,
        onBack = null,
        backStack = stack,
        onOpenAccount = onOpenAccount,
        title = null,
        reselectKey = SeerrTab.Discover,
    )
}
