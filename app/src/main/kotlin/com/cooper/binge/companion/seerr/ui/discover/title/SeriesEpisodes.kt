package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.SectionHeader
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.discover.request.seasonLabel
import com.cooper.binge.companion.seerr.ui.discover.title.season.EpisodeRow
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.binge.designsystem.R as DesR

/** How many episodes the page shows before it offers the whole season on a page of its own. */
internal const val EPISODES_ON_PAGE = 5

/** What the season list does: select a season, read its episodes again, and open the whole season. */
class SeasonActions(
    val onSelect: (Int) -> Unit,
    val onRetry: () -> Unit,
    val onViewAll: (seasonNumber: Int, seasonName: String) -> Unit,
)

/** The selected season's episodes: the first few, then a row that opens the rest. Loading and failure stay in place. */
@Composable
internal fun EpisodesSection(
    state: SeriesPageUiState.Ready,
    actions: SeasonActions,
) {
    val number = state.selectedSeason ?: return
    val season = state.series.seasons.firstOrNull { it.number == number } ?: return
    val title = seasonLabel(season.number, season.name)
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        SectionHeader(stringResource(R.string.series_page_episodes_of, title))
        when (val episodes = state.episodes[number]) {
            null, EpisodesState.Loading -> LoadingScreen()
            is EpisodesState.Error -> ErrorScreen(episodes.error, onRetry = actions.onRetry)
            is EpisodesState.Ready -> {
                episodes.episodes.take(EPISODES_ON_PAGE).forEach {
                    EpisodeRow(it, Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)))
                }
                if (episodes.episodes.size > EPISODES_ON_PAGE) {
                    ItemGroup(
                        title = null,
                        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
                        rows =
                            listOf(
                                ListItem(
                                    icon = Icons.Filled.ViewList,
                                    label = stringResource(R.string.series_page_view_all_episodes),
                                    detail = null,
                                    onClick = { actions.onViewAll(season.number, season.name) },
                                ),
                            ),
                    )
                }
            }
        }
    }
}
