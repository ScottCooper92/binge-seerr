package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.EpisodeBrowseUi
import com.binge.designsystem.component.SeasonSectionState
import com.binge.designsystem.component.SeasonSectionUi
import com.binge.designsystem.component.SeasonSectionsList
import com.binge.designsystem.template.BingeScreenScaffold
import com.binge.designsystem.template.ScreenBar
import com.binge.designsystem.template.screenInnerPadding
import com.binge.designsystem.template.screenOuterPadding
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.ui.discover.request.seasonLabel
import com.cooper.binge.companion.seerr.ui.discover.title.toDisplayDate
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.cooper.binge.companion.seerr.ui.state.messageRes
import java.util.Locale
import com.binge.designsystem.R as DesR

/**
 * Every season of a show under the show's name, each under a header with its episode count. An episode does not open anything.
 * The bar is the collapsing one Binge's episodes page has: it stays pinned as the list scrolls, and the rows pass under it behind
 * its scrim. A page that opens part way down arrives with the bar already collapsed over the rows.
 */
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
    val listState = rememberLazyListState()
    BingeScreenScaffold(
        title = title,
        modifier = modifier,
        onBack = onBack,
        bar = ScreenBar.Small,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding.screenOuterPadding())) {
            val inner = padding.screenInnerPadding()
            when (state) {
                SeriesEpisodesUiState.Loading -> LoadingScreen(Modifier.padding(inner))
                is SeriesEpisodesUiState.Error -> ErrorScreen(state.error, Modifier.padding(inner), onRetry)
                is SeriesEpisodesUiState.Ready ->
                    SeasonList(
                        state.seasons,
                        openAtSeason,
                        listState,
                        onSeasonNear,
                        onRetrySeason,
                        inner,
                    )
            }
        }
    }
}

@Composable
private fun SeasonList(
    seasons: List<SeasonSection>,
    openAtSeason: Int,
    listState: LazyListState,
    onSeasonNear: (Int) -> Unit,
    onRetrySeason: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val sections = seasons.toSectionUis(onRetrySeason)
    val openAt = seasonKey(openAtSeason)
    SeasonSectionsList(
        sections = sections,
        listState = listState,
        openAt = openAt,
        onSeasonNeeded = { key -> seasonOf(key)?.let(onSeasonNear) },
        contentPadding = contentPadding,
    )
}

@Composable
private fun List<SeasonSection>.toSectionUis(onRetry: (Int) -> Unit): List<SeasonSectionUi> {
    val locale = LocalConfiguration.current.locales[0]
    val retry = stringResource(DesR.string.error_kind_retry)
    val empty = stringResource(R.string.season_episodes_empty)
    return map { section ->
        val season = section.season
        SeasonSectionUi(
            key = seasonKey(season.number),
            title = seasonLabel(season.number, season.name),
            subtitle = pluralStringResource(R.plurals.season_episodes, season.episodeCount, season.episodeCount),
            state =
                when (val episodes = section.episodes) {
                    SeasonEpisodesState.Loading -> SeasonSectionState.Loading(season.episodeCount.coerceAtLeast(1))
                    is SeasonEpisodesState.Failed ->
                        SeasonSectionState.Failed(stringResource(episodes.error.messageRes()), retry) { onRetry(season.number) }
                    is SeasonEpisodesState.Loaded ->
                        if (episodes.episodes.isEmpty()) {
                            SeasonSectionState.Failed(empty, retry)
                        } else {
                            SeasonSectionState.Loaded(episodes.episodes.map { it.toBrowseUi(locale) })
                        }
                },
        )
    }
}

private fun SeasonEpisode.toBrowseUi(locale: Locale) =
    EpisodeBrowseUi(
        number = number,
        name = name,
        overview = overview,
        stillUrl = stillUrl,
        rating = rating,
        airDate = airDate.toDisplayDate(locale),
    )

private fun seasonKey(number: Int) = "season-$number"

private fun seasonOf(key: String): Int? = key.removePrefix("season-").toIntOrNull()
