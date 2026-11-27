package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.compose.foundation.layout.PaddingValues
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
import com.binge.designsystem.template.ScreenBody
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.feature.discover.R
import com.cooper.binge.companion.seerr.ui.discover.request.seasonLabel
import com.cooper.binge.companion.seerr.ui.discover.title.toDisplayDate
import com.cooper.binge.companion.seerr.ui.state.ErrorScreen
import com.cooper.binge.companion.seerr.ui.state.LoadingScreen
import com.cooper.binge.companion.seerr.ui.state.messageRes
import java.util.Locale
import com.binge.designsystem.R as DesR

/**
 * Every season of a show under the show's name, each under a header with its episode count. An episode opens its own page through
 * [onEpisode], given its season and episode numbers. The bar is the small one: a single line that slides away as the list scrolls
 * down and comes back as it scrolls up. The rows pass under it behind its scrim.
 */
@Composable
fun SeriesEpisodesScreen(
    title: String,
    state: SeriesEpisodesUiState,
    openAtSeason: Int,
    onSeasonNear: (Int) -> Unit,
    onRetrySeason: (Int) -> Unit,
    onEpisode: (seasonNumber: Int, episodeNumber: Int) -> Unit,
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
        ScreenBody(padding) { inner ->
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
                        onEpisode,
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
    onEpisode: (seasonNumber: Int, episodeNumber: Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val sections = seasons.toSectionUis(onRetrySeason)
    val openAt = seasonKey(openAtSeason)
    SeasonSectionsList(
        sections = sections,
        listState = listState,
        openAt = openAt,
        onSeasonNeeded = { key -> seasonOf(key)?.let(onSeasonNear) },
        onEpisodeClick = { key, episodeNumber -> seasonOf(key)?.let { onEpisode(it, episodeNumber) } },
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

internal fun SeasonEpisode.toBrowseUi(locale: Locale) =
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
