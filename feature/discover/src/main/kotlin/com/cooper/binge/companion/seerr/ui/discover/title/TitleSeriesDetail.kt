package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.EpisodeRow
import com.binge.designsystem.component.SeasonRow
import com.binge.designsystem.template.SeriesDetail
import com.binge.designsystem.template.SeriesDetailCallbacks
import com.cooper.binge.companion.seerr.discover.title.SeasonEpisode
import com.cooper.binge.companion.seerr.discover.title.SeasonInfo
import com.cooper.binge.companion.seerr.feature.discover.R
import com.cooper.binge.companion.seerr.ui.discover.request.seasonLabel
import com.cooper.binge.companion.seerr.ui.state.messageRes
import java.util.Locale
import com.binge.designsystem.template.EpisodesState as DesEpisodesState

/** What the season list does: select a season, read its episodes again, and open every season's episodes at the chosen one. */
class SeasonActions(
    val onSelect: (Int) -> Unit,
    val onRetry: () -> Unit,
    val onViewAll: (seasonNumber: Int, seriesName: String) -> Unit,
)

/** The series' page as plain values, drawn from the same sections as a film's. */
@Composable
fun seriesDetailModel(
    state: SeriesPageUiState.Ready,
    actions: TitlePageActions,
): TitleDetailModel {
    val series = state.series
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val input =
        TitleDetailInput(
            title = series.name,
            backdropUrl = series.backdropUrl,
            posterUrl = series.posterUrl,
            tagline = series.tagline,
            genres = series.genres,
            overview = series.overview,
            stats = series.titleStats(locale),
            scores = state.extras?.ratings.toExternalScores(context.resources, locale),
            cast = series.cast,
            crew = series.keyCrew,
            keywords = series.keywords,
            keywordsOpen = series.keywordsOpen,
            entries =
                series.detailEntries(
                    context.resources,
                    locale,
                    actions.onOpenCompany.takeIf { series.companiesOpen },
                    actions.onOpenPerson,
                ),
            moreLikeThis = state.extras?.moreLikeThis.orEmpty(),
        )
    return titleDetailModel(input, actions)
}

/** The seasons and the shown season's episodes, as the template takes them; null where there is no season to show. */
@Composable
fun seriesEpisodes(state: SeriesPageUiState.Ready): SeriesDetail? {
    val number = state.selectedSeason ?: return null
    val season = state.series.seasons.firstOrNull { it.number == number } ?: return null
    val locale = LocalConfiguration.current.locales[0]
    val episodes =
        when (val read = state.episodes[number]) {
            null, EpisodesState.Loading -> DesEpisodesState.Loading
            is EpisodesState.Error -> DesEpisodesState.Failed(stringResource(read.error.messageRes()))
            is EpisodesState.Ready -> DesEpisodesState.Ready(read.episodes.map { it.toRow(locale) })
        }
    return SeriesDetail(
        seasons = state.series.seasons.map { it.toRow() },
        selectedSeason = number,
        episodes = episodes,
        episodesTitle = stringResource(R.string.series_page_episodes),
        seasonSubtitle = season.subtitle(),
        viewAllLabel = stringResource(R.string.series_page_view_all_episodes),
    )
}

fun SeasonActions.callbacks(
    seasonNumber: Int?,
    seriesName: String,
) = SeriesDetailCallbacks(
    onSelectSeason = onSelect,
    onRetryEpisodes = onRetry,
    onViewAllEpisodes = { seasonNumber?.let { onViewAll(it, seriesName) } },
)

/** An episode on the page: plain content, since an episode here opens nothing. */
private fun SeasonEpisode.toRow(locale: Locale) =
    EpisodeRow(number = number, name = name, stillUrl = stillUrl, rating = rating, airDate = airDate.toDisplayDate(locale))

/** A season in the picker: its name, and its episode count, year and, where the server holds or has been asked for it, its status. */
@Composable
private fun SeasonInfo.toRow(): SeasonRow {
    val status = standard.label()?.first
    val episodes = pluralStringResource(R.plurals.season_episodes, episodeCount, episodeCount)
    val detail =
        listOfNotNull(airYear, status).fold(episodes) { text, part -> stringResource(R.string.series_season_subtitle, text, part) }
    return SeasonRow(number, seasonLabel(number, name), detail)
}

/** "Season 1 · 9 episodes · 2022": the season's number, its episode count and, where the server gave it, its first air year. */
@Composable
private fun SeasonInfo.subtitle(): String {
    val episodes = pluralStringResource(R.plurals.season_episodes, episodeCount, episodeCount)
    return listOfNotNull(seasonLabel(number, name), episodes, airYear).joinToString(stringResource(R.string.series_season_separator))
}
