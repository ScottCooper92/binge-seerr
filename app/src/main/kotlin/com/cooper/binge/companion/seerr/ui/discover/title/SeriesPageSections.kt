package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
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
import com.cooper.binge.companion.seerr.discover.title.SeasonInfo
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import com.cooper.binge.companion.seerr.discover.title.trailerUrl
import com.cooper.binge.companion.seerr.ui.discover.request.seasonLabel
import java.util.Locale
import com.binge.designsystem.R as DesR

private const val MINUTES_PER_HOUR = 60

/** The line under the title in the request sheet: first air year and season count, whichever the server gave. */
@Composable
internal fun SeriesDetails.requestMeta(): String {
    val seasons = seasonCount?.let { pluralStringResource(R.plurals.request_sheet_season_count, it, it) }
    return listOfNotNull(firstAirYear, seasons).joinToString(stringResource(R.string.request_sheet_meta_separator))
}

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
        SeasonsGroup(series.seasons)
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

/**
 * Each season with its episode count, year and, where the server holds or has been asked for it, its status. The status is
 * text in the detail line rather than a chip, so a long name at a large font still has the row's width.
 */
@Composable
private fun SeasonsGroup(seasons: List<SeasonInfo>) {
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
                clickable = false,
            )
        }
    ItemGroup(
        title = stringResource(R.string.series_page_seasons),
        rows = rows,
        modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
    )
}
