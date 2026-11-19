package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DiscoverEntryRow
import com.binge.designsystem.navOverlayPadding
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.sliders.HubGrid
import com.cooper.binge.companion.seerr.ui.state.EmptyScreen
import com.binge.designsystem.R as DesR

/**
 * A query that found nothing: what was searched and a next step, then the way into the unfiltered grid, as Binge's search
 * has it. The row opens the popular grid for the chosen type; People, which has no grid of its own, opens movies.
 */
@Composable
internal fun NoResultsBody(
    state: TitleSearchUiState.NoResults,
    onOpenGrid: (HubGrid, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val series = state.type == SearchType.Series
    val grid = HubGrid(if (series) TitleKind.Series else TitleKind.Movie, GridPreset.Popular)
    val gridTitle = stringResource(if (series) R.string.hubs_row_popular_tv else R.string.hubs_row_popular_movies)
    Column(modifier = modifier) {
        EmptyScreen(
            title = stringResource(R.string.title_search_none_title, state.query),
            message = stringResource(R.string.title_search_none_message),
            modifier = Modifier.weight(1f),
        )
        DiscoverEntryRow(
            title = stringResource(if (series) R.string.hubs_browse_all_tv else R.string.hubs_browse_all_movies),
            subtitle = stringResource(if (series) R.string.hubs_browse_subtitle_tv else R.string.hubs_browse_subtitle_movies),
            onClick = { onOpenGrid(grid, gridTitle) },
            modifier = Modifier.padding(navOverlayPadding(PaddingValues(vertical = dimensionResource(DesR.dimen.list_row_spacing)))),
            startPadding = dimensionResource(DesR.dimen.screen_content_inset),
            endPadding = dimensionResource(DesR.dimen.screen_content_inset),
        )
    }
}
