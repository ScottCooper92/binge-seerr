package com.cooper.binge.companion.seerr.ui.discover.title.season

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
 * [onEpisode], given its season and episode numbers. The bar is the small one, and it stays put as the list scrolls: it names the
 * show and the season at the top of the list, so a person deep in a long show always knows which season they are reading. The
 * rows pass under it behind its scrim.
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
    var currentKey by rememberSaveable { mutableStateOf<String?>(null) }
    val seasonName =
        (state as? SeriesEpisodesUiState.Ready)
            ?.seasons
            ?.firstOrNull { seasonKey(it.season.number) == currentKey }
            ?.season
            ?.let { seasonLabel(it.number, it.name) }
    BingeScreenScaffold(
        title = seasonName?.let { stringResource(R.string.series_season_subtitle, title, it) } ?: title,
        modifier = modifier,
        onBack = onBack,
        bar = ScreenBar.Small,
        pinned = true,
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
                        onCurrentSeason = { currentKey = it },
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
    onCurrentSeason: (String) -> Unit,
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
        onCurrentSectionChanged = onCurrentSeason,
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
