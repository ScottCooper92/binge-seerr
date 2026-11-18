package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ExpandableOverview
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.SectionHeader
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.NextEpisode
import com.cooper.binge.companion.seerr.discover.title.SeasonInfo
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.ui.discover.request.seasonLabel
import com.binge.designsystem.R as DesR

private const val MINUTES_PER_HOUR = 60

/** The line under the title: first air year, episode runtime, season count and certification, whichever the server gave. */
@Composable
internal fun SeriesDetails.metaLine(): String {
    val runtime = episodeRuntimeMinutes?.let { stringResource(R.string.series_page_runtime, it) }
    val seasonsText = seasonCount?.let { pluralStringResource(R.plurals.series_page_season_count, it, it) }
    return listOfNotNull(firstAirYear, runtime, seasonsText, certification).joinToString(" · ")
}

/** What the page says below its hero. A section the server gave nothing for is not drawn. */
@Composable
internal fun SeriesPageBody(
    state: SeriesPageUiState.Ready,
    actions: TitlePageActions,
    seasonActions: SeasonActions,
) {
    val series = state.series
    val gap = dimensionResource(DesR.dimen.padding_m)
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        StatusRow(series.standard, series.fourK)
        StatsRow(series.voteAverage, state.extras?.ratings)
        series.tagline?.let { Text(it, Modifier.padding(horizontal = gap), style = MaterialTheme.typography.titleSmall) }
        series.overview?.let { ExpandableOverview(text = it, modifier = Modifier.padding(horizontal = gap)) }
        if (series.genres.isNotEmpty()) TagRow(series.genres)
        NextEpisodeRow(series.nextEpisode)
        SeasonsGroup(series.seasons, state.selectedSeason, seasonActions.onSelect)
        EpisodesSection(state, seasonActions)
        ItemGroup(
            title = null,
            rows = series.factRows(LocalContext.current.resources, LocalConfiguration.current.locales[0]),
            modifier = Modifier.padding(horizontal = gap),
        )
        PeopleRow(stringResource(R.string.title_page_cast), series.cast, actions.onOpenPerson, actions.onOpenCredits)
        PeopleRow(stringResource(R.string.title_page_crew), series.keyCrew, actions.onOpenPerson, actions.onOpenCredits)
        VideosGroup(series.videos, actions.onOpenUrl)
        series.providers?.takeIf { it.streaming.isNotEmpty() }?.let { providers ->
            SectionHeader(stringResource(R.string.title_page_where_to_watch))
            TagRow(providers.streaming.map { it.name })
        }
        state.extras?.let { extras ->
            TitleRow(stringResource(R.string.title_page_recommended), extras.recommendations, actions.onOpenTitle)
            TitleRow(stringResource(R.string.title_page_similar), extras.similar, actions.onOpenTitle)
        }
        if (series.keywords.isNotEmpty()) {
            SectionHeader(stringResource(R.string.title_page_keywords))
            TagRow(series.keywords)
        }
    }
}

/** The next episode to air, where the server returned one: its title, where it falls in the show and when it airs. */
@Composable
private fun NextEpisodeRow(next: NextEpisode?) {
    if (next == null) return
    val locale = LocalConfiguration.current.locales[0]
    val place = stringResource(R.string.series_next_episode, next.name, next.seasonNumber, next.episodeNumber)
    val detail = listOfNotNull(place, next.airDate.toDisplayDate(locale)).joinToString(" · ")
    ItemGroup(
        title = null,
        rows =
            listOf(
                ListItem(
                    icon = Icons.Filled.Event,
                    label = stringResource(R.string.series_fact_next_episode),
                    detail = detail,
                    supportingMaxLines = 3,
                    clickable = false,
                ),
            ),
        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
    )
}

/**
 * Each season with its episode count, year and, where the server holds or has been asked for it, its status. The status is
 * text in the detail line rather than a chip, so a long name at a large font still has the row's width. A tap selects the
 * season whose episodes show below; the selected one carries a check, not a chevron, since nothing opens.
 */
@Composable
private fun SeasonsGroup(
    seasons: List<SeasonInfo>,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    if (seasons.isEmpty()) return
    val rows =
        seasons.map { season ->
            val status = season.standard.label()?.first
            val episodes = pluralStringResource(R.plurals.season_episodes, season.episodeCount, season.episodeCount)
            val detail =
                listOfNotNull(
                    season.airYear,
                    status,
                ).fold(episodes) { text, part -> stringResource(R.string.series_season_subtitle, text, part) }
            ListItem(
                icon = Icons.Filled.Tv,
                label = seasonLabel(season.number, season.name),
                detail = detail,
                supportingMaxLines = 3,
                selected = season.number == selected,
                onClick = { onSelect(season.number) },
                trailingContent =
                    if (season.number ==
                        selected
                    ) {
                        ({ Icon(Icons.Filled.CheckCircle, contentDescription = null) })
                    } else {
                        ({ Spacer(Modifier) })
                    },
            )
        }
    ItemGroup(
        title = stringResource(R.string.series_page_seasons),
        rows = rows,
        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
    )
}
