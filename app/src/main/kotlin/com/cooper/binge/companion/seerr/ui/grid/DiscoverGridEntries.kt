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
import com.cooper.binge.companion.seerr.ui.discover.title.LocalOpenTitle
import com.cooper.binge.companion.seerr.ui.popIfTop
import com.cooper.binge.companion.seerr.ui.replaceTop
import java.time.LocalDate

/** The grid a "see all" opens. It has no pane metadata: it covers the tab bar, and Back returns to the hub it came from. */
fun EntryProviderScope<NavKey>.discoverGridEntries(backStack: NavBackStack<NavKey>) {
    entry<DiscoverGridRoute> { route -> DiscoverGridEntry(route, onBack = { backStack.popIfTop(route) }, backStack = backStack) }
}

/** A card opens the title's page, or with the flag off the hand-off [LocalOpenTitle] falls back to. */
@Composable
internal fun DiscoverGridEntry(
    route: DiscoverGridRoute,
    onBack: () -> Unit,
    backStack: NavBackStack<NavKey>,
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
    DiscoverGridScreen(
        kind = route.kind,
        title = route.title,
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
