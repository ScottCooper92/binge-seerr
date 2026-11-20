package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.CarouselSkeleton
import com.binge.designsystem.component.DiscoverEntryRow
import com.binge.designsystem.navOverlayPadding
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.sliders.HubGrid
import com.cooper.binge.companion.seerr.ui.discover.title.TitleRow
import com.binge.designsystem.R as DesR

/** What the Search tab offers under the recents: the trending rails, or the skeleton that stands in for them while they load. */
internal fun LazyListScope.trendingRails(
    trending: Trending,
    onOpen: (TitleResult) -> Unit,
) {
    when (trending) {
        Trending.Loading -> item(key = "loading") { CarouselSkeleton(description = stringResource(R.string.hubs_loading_row)) }
        Trending.Unavailable -> Unit
        is Trending.Ready -> {
            rail("movies", R.string.title_search_trending_movies, trending.movies, onOpen)
            rail("series", R.string.title_search_trending_series, trending.series, onOpen)
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

/**
 * The way into the unfiltered grid, under a query that found nothing, as Binge's search has it. The row opens the popular grid for the
 * chosen type; People, which has no grid of its own, opens movies.
 */
@Composable
internal fun BrowseAllRow(
    state: TitleSearchUiState.NoResults,
    onOpenGrid: (HubGrid, String) -> Unit,
) {
    val series = state.type == SearchType.Series
    val grid = HubGrid(if (series) TitleKind.Series else TitleKind.Movie, GridPreset.Popular)
    val gridTitle = stringResource(if (series) R.string.hubs_row_popular_tv else R.string.hubs_row_popular_movies)
    DiscoverEntryRow(
        title = stringResource(if (series) R.string.hubs_browse_all_tv else R.string.hubs_browse_all_movies),
        subtitle = stringResource(if (series) R.string.hubs_browse_subtitle_tv else R.string.hubs_browse_subtitle_movies),
        onClick = { onOpenGrid(grid, gridTitle) },
        modifier = Modifier.padding(navOverlayPadding(PaddingValues(vertical = dimensionResource(DesR.dimen.list_row_spacing)))),
        startPadding = dimensionResource(DesR.dimen.screen_content_inset),
        endPadding = dimensionResource(DesR.dimen.screen_content_inset),
    )
}
