package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.SectionHeader
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.SeasonInfo
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

/**
 * The episodes of one season under a header with the season's dropdown, then the first few and a button for the rest. Loading and
 * failure stay in place. A show with one season has nothing to choose, so it has no dropdown.
 */
@Composable
internal fun EpisodesSection(
    state: SeriesPageUiState.Ready,
    actions: SeasonActions,
    modifier: Modifier = Modifier,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val number = state.selectedSeason ?: return
    val season = state.series.seasons.firstOrNull { it.number == number } ?: return
    val gap = dimensionResource(DesR.dimen.padding_m)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s))) {
        SectionHeader(
            title = stringResource(R.string.series_page_episodes),
            trailingContent =
                state.series.seasons
                    .takeIf { it.size > 1 }
                    ?.let { { SeasonDropdown(season, onClick = { picking = true }) } },
        )
        Text(
            season.subtitle(),
            Modifier.padding(horizontal = gap),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (val episodes = state.episodes[number]) {
            null, EpisodesState.Loading -> LoadingScreen()
            is EpisodesState.Error -> ErrorScreen(episodes.error, onRetry = actions.onRetry)
            is EpisodesState.Ready -> {
                episodes.episodes.take(EPISODES_ON_PAGE).forEach { EpisodeRow(it, Modifier.padding(horizontal = gap)) }
                if (episodes.episodes.size > EPISODES_ON_PAGE) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { actions.onViewAll(season.number, season.name) }) {
                            Text(stringResource(R.string.series_page_view_all_episodes), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    if (picking) {
        SeasonPickerSheet(
            seasons = state.series.seasons,
            selected = number,
            onSelect = {
                picking = false
                actions.onSelect(it)
            },
            onDismiss = { picking = false },
        )
    }
}

/** "Season 1 · 9 episodes · 2022": the season's number, its episode count and, where the server gave it, its first air year. */
@Composable
private fun SeasonInfo.subtitle(): String {
    val episodes = pluralStringResource(R.plurals.season_episodes, episodeCount, episodeCount)
    return listOfNotNull(seasonLabel(number, name), episodes, airYear).joinToString(stringResource(R.string.series_season_separator))
}
