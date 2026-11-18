package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
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
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import com.cooper.binge.companion.seerr.discover.title.trailerUrl
import com.cooper.binge.companion.seerr.ui.discover.request.seasonLabel
import java.util.Locale
import com.binge.designsystem.R as DesR

private const val MINUTES_PER_HOUR = 60

/** The series' stats: rating with its votes, first air year, episode runtime and certification, then the server's other ratings. */
@Composable
internal fun SeriesDetails.titleStats(
    locale: Locale,
    ratings: TitleRatings?,
): TitleStats {
    val runtime = episodeRuntimeMinutes?.let { stringResource(R.string.series_page_runtime, it) }
    return titleStats(voteAverage, voteCount, firstAirYear, runtime, certification, ratings, locale)
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
        val context = LocalContext.current
        TitleStatsRows(series.titleStats(LocalConfiguration.current.locales[0], state.extras?.ratings))
        TitleActions(
            model = titleActionsModel(series.standard, series.fourK),
            onRequest = actions.onRequest,
            onTrailer = series.videos.trailerUrl()?.let { url -> { actions.onOpenUrl(url) } },
            onShare = { context.shareTitle(series.name, series.webUrl) },
        )
        series.overview?.let { ExpandableOverview(text = it, modifier = Modifier.padding(horizontal = gap)) }
        NextEpisodeRow(series.nextEpisode)
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
