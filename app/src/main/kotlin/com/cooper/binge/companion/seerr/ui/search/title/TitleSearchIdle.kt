package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.CarouselSkeleton
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.ui.discover.title.TitleRow
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.binge.designsystem.R as DesR

/**
 * What the Search tab offers before anything is searched: the recent queries as chips, then the trending rails. With neither,
 * the plain prompt to search.
 */
@Composable
internal fun IdleBody(
    state: TitleSearchUiState.Idle,
    actions: TitleSearchActions,
    contentPadding: PaddingValues,
) {
    val rails = state.trending as? Trending.Ready
    val hasRails = rails != null && (rails.movies.isNotEmpty() || rails.series.isNotEmpty())
    if (state.recents.isEmpty() && !hasRails && state.trending !is Trending.Loading) {
        EmptyScreen(
            message = stringResource(R.string.title_search_idle_message),
            title = stringResource(R.string.title_search_idle_title),
            icon = Icons.Filled.Search,
            modifier = Modifier.padding(contentPadding),
        )
        return
    }
    val direction = LocalLayoutDirection.current
    val listPadding =
        PaddingValues(
            start = contentPadding.calculateStartPadding(direction),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(direction),
            bottom = contentPadding.calculateBottomPadding() + dimensionResource(DesR.dimen.padding_m),
        )
    LazyColumn(contentPadding = listPadding) {
        if (state.recents.isNotEmpty()) {
            item(key = "recents") { RecentSearches(state.recents, onSelect = actions.onRecent, onClear = actions.onForgetRecents) }
        }
        when (val trending = state.trending) {
            Trending.Loading -> item(key = "loading") { CarouselSkeleton(description = stringResource(R.string.hubs_loading_row)) }
            Trending.Unavailable -> Unit
            is Trending.Ready -> {
                rail("movies", R.string.title_search_trending_movies, trending.movies, actions.onOpenTitle)
                rail("series", R.string.title_search_trending_series, trending.series, actions.onOpenTitle)
            }
        }
    }
}

private fun LazyListScope.rail(
    key: String,
    title: Int,
    titles: List<TitleResult>,
    onOpen: (TitleResult) -> Unit,
) {
    if (titles.isEmpty()) return
    item(key = key) { TitleRow(title = stringResource(title), titles = titles, onOpen = onOpen) }
}
