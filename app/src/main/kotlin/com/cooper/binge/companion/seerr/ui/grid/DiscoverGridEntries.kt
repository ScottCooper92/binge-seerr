package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.flags.LocalFlags
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.openTitle
import com.cooper.binge.companion.seerr.ui.popIfTop
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType
import java.time.LocalDate

/** The grid a "see all" opens. It has no pane metadata: it covers the tab bar, and Back returns to the hub it came from. */
fun EntryProviderScope<NavKey>.discoverGridEntries(backStack: NavBackStack<NavKey>) {
    entry<DiscoverGridRoute> { route -> DiscoverGridEntry(route, onBack = { backStack.popIfTop(route) }) }
}

/** A title opens in Binge where it is installed and on the server where it is not, until titles have pages of their own. */
@Composable
internal fun DiscoverGridEntry(
    route: DiscoverGridRoute,
    onBack: () -> Unit,
) {
    val tomorrow = remember { LocalDate.now().plusDays(1).toString() }
    val model =
        hiltViewModel<DiscoverGridViewModel, DiscoverGridViewModel.Factory>(
            key = route.toString(),
            creationCallback = { it.create(route.kind, route.preset, route.title, tomorrow) },
        )
    val state by model.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val promoted = LocalFlags.current.bingePromotion
    LaunchedEffect(model) {
        model.events.collect { event ->
            when (event) {
                is DiscoverGridEvent.Open -> {
                    val type = if (event.card.kind == TitleKind.Series) RequestMediaType.Tv else RequestMediaType.Movie
                    context.openTitle(promoted, type, event.card.tmdbId, event.card.webUrl)
                }
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
                onSort = model::onSort,
                onClearFilter = model::onClearFilter,
                onRetry = model::onRetry,
                onLoadMore = model::onLoadMore,
                onOpenTitle = model::onOpenTitle,
            ),
    )
}
