package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.NavMotionSpecs
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle
import com.cooper.binge.companion.seerr.ui.popIfTop
import com.cooper.binge.companion.seerr.ui.replaceTop
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab
import java.time.LocalDate

/** The grid a "see all" opens. It has no pane metadata: it covers the tab bar, and Back returns to the hub it came from. */
internal fun EntryProviderScope<NavKey>.discoverGridEntries(
    backStack: NavBackStack<NavKey>,
    motion: NavMotionSpecs,
) {
    entry<DiscoverGridRoute>(metadata = motion.detail) { route ->
        DiscoverGridEntry(route, onBack = {
            backStack.popIfTop(route)
        }, backStack = backStack)
    }
}

/** A card opens the title's page, or with the flag off the hand-off [LocalOpenTitle] falls back to. */
@Composable
internal fun DiscoverGridEntry(
    route: DiscoverGridRoute,
    onBack: (() -> Unit)?,
    backStack: NavBackStack<NavKey>,
    title: String? = route.title,
    reselectKey: Any? = null,
    modelKey: String = route.toString(),
    onQueryChanged: ((DiscoverFilters, DiscoverSort) -> Unit)? = null,
    onKindSwitched: () -> Unit = {},
) {
    val tomorrow = remember { LocalDate.now().plusDays(1).toString() }
    val model =
        hiltViewModel<DiscoverGridViewModel, DiscoverGridViewModel.Factory>(
            key = modelKey,
            creationCallback = { it.create(route.kind, route.preset, route.title, tomorrow, route.filters, route.sort) },
        )
    val state by model.uiState.collectAsStateWithLifecycle()
    val controls = state.controls
    LaunchedEffect(controls.filters, controls.sort) {
        val sort = controls.sort
        if (sort != null) onQueryChanged?.invoke(controls.filters, sort)
    }
    val openTitle by rememberUpdatedState(LocalOpenTitle.current)
    LaunchedEffect(model) {
        model.events.collect { event ->
            when (event) {
                is DiscoverGridEvent.Open -> openTitle(event.card.kind, event.card.tmdbId, event.card.webUrl)
            }
        }
    }
    DiscoverGridScreen(
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
                    onKindSwitched()
                    backStack.replaceTop(route, route.carriedTo(kind, sort, filters))
                },
                loads = FilterLoads(model::onProvidersNeeded, model::onKeywordQuery, model::onKeywordsNeeded),
                onResetFilters = model::onResetFilters,
            ),
    )
}

/**
 * The Discover tab: the grid at its root, as a tab of its own with no Back arrow. The tab's stack holds the one route, and the
 * Content choice in the filter sheet reopens it as the other kind in place, so the tab picks Movies or TV shows as Binge's does.
 * The route is kept in step with the order and filters chosen, so a fold that takes the tab away and an unfold reopen it as left.
 * The grid's ViewModel is keyed by how many times the kind was switched: a write-back keeps it, and every switch starts a fresh one.
 */
@Composable
internal fun DiscoverTabEntry(stack: NavBackStack<NavKey>) {
    val route = stack.lastOrNull() as? DiscoverGridRoute ?: return
    var switches by rememberSaveable { mutableIntStateOf(0) }
    DiscoverGridEntry(
        route,
        onBack = null,
        backStack = stack,
        title = null,
        reselectKey = SeerrTab.Discover,
        modelKey = "discover-tab-$switches",
        onKindSwitched = { switches++ },
        onQueryChanged = { filters, sort ->
            if (route.filters != filters || route.sort != sort) stack.replaceTop(route, route.copy(filters = filters, sort = sort))
        },
    )
}
