package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.SectionHeader
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.discover.request.seasonLabel
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import com.binge.designsystem.R as DesR

/** How many seasons past the last one on screen are read ahead, so a scroll rarely meets a placeholder. */
private const val SEASONS_AHEAD = 1

/** Every season of a show under the show's name, each under a header with its episode count. An episode does not open anything. */
@Composable
internal fun SeriesEpisodesScreen(
    title: String,
    state: SeriesEpisodesUiState,
    openAtSeason: Int,
    onSeasonNear: (Int) -> Unit,
    onRetrySeason: (Int) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BingeScreenScaffold(title = title, modifier = modifier, bar = ScreenBar.Small, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding.screenOuterPadding())) {
            val inner = padding.screenInnerPadding()
            when (state) {
                SeriesEpisodesUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                is SeriesEpisodesUiState.Error -> ErrorScreen(state.error, Modifier.padding(inner), onRetry)
                is SeriesEpisodesUiState.Ready -> SeasonList(state.seasons, openAtSeason, onSeasonNear, onRetrySeason, inner)
            }
        }
    }
}

@Composable
private fun SeasonList(
    seasons: List<SeasonSection>,
    openAtSeason: Int,
    onSeasonNear: (Int) -> Unit,
    onRetrySeason: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = openAtIndex(seasons, openAtSeason))
    ReadAhead(listState, seasons.map { it.season.number }, onSeasonNear)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        seasons.forEach { seasonItems(it, onRetrySeason) }
    }
}

/**
 * Where the list opens: past every item of the seasons before [number]. A fresh page has only the chosen season read, so each
 * earlier one is a header and a placeholder; one that is read already counts its rows. A season above that loads later holds
 * the place by key, so what the reader opened at stays under them.
 */
private fun openAtIndex(
    seasons: List<SeasonSection>,
    number: Int,
): Int =
    seasons
        .takeWhile { it.season.number != number }
        .sumOf { 1 + ((it.episodes as? SeasonEpisodesState.Loaded)?.episodes?.size?.coerceAtLeast(1) ?: 1) }

private fun LazyListScope.seasonItems(
    section: SeasonSection,
    onRetry: (Int) -> Unit,
) {
    val number = section.season.number
    item(key = seasonKey(number)) { SeasonHeader(section) }
    when (val episodes = section.episodes) {
        SeasonEpisodesState.Loading -> item(key = "${seasonKey(number)}-state") { LoadingScreen() }
        is SeasonEpisodesState.Failed ->
            item(
                key = "${seasonKey(number)}-state",
            ) { ErrorScreen(episodes.error, onRetry = { onRetry(number) }) }
        is SeasonEpisodesState.Loaded ->
            if (episodes.episodes.isEmpty()) {
                item(key = "${seasonKey(number)}-state") { EmptySeason() }
            } else {
                items(episodes.episodes, key = { "${seasonKey(number)}-ep-${it.number}" }) {
                    EpisodeRow(it, Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)), showOverview = true)
                }
            }
    }
}

private fun seasonKey(number: Int) = "season-$number"

@Composable
private fun SeasonHeader(section: SeasonSection) {
    val season = section.season
    val count = pluralStringResource(R.plurals.season_episodes, season.episodeCount, season.episodeCount)
    SectionHeader(
        title = listOf(seasonLabel(season.number, season.name), count).joinToString(stringResource(R.string.series_season_separator)),
    )
}

@Composable
private fun EmptySeason() {
    Text(
        stringResource(R.string.season_episodes_empty),
        Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Asks for each season that is on screen and for the one after the last, once each: the view model ignores a repeat. */
@Composable
private fun ReadAhead(
    listState: LazyListState,
    order: List<Int>,
    onSeasonNear: (Int) -> Unit,
) {
    LaunchedEffect(listState, order) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .mapNotNull { seasonOf(it.key) }
                .distinct()
        }.distinctUntilChanged()
            .collect { visible ->
                val last = visible.maxOfOrNull { order.indexOf(it) } ?: return@collect
                visible.forEach(onSeasonNear)
                order.drop(last + 1).take(SEASONS_AHEAD).forEach(onSeasonNear)
            }
    }
}

private fun seasonOf(key: Any): Int? = (key as? String)?.removePrefix("season-")?.substringBefore('-')?.toIntOrNull()
