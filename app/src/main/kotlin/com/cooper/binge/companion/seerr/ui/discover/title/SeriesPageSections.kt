package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.component.InfoRowList
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.SectionHeader
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.SeasonInfo
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.discover.title.trailerUrl
import com.cooper.binge.companion.seerr.ui.discover.request.seasonLabel
import java.text.NumberFormat
import java.util.Locale
import com.binge.designsystem.R as DesR

private const val MINUTES_PER_HOUR = 60

/** The series' stats, as Binge shows them: rating with its votes, first air year with the season count, episodes and certification. */
@Composable
internal fun SeriesDetails.titleStats(locale: Locale): List<DetailStat> {
    val seasonsLabel = seasonCount?.let { pluralStringResource(R.plurals.series_stat_seasons, it, it) }
    return listOfNotNull(
        ratingStat(voteAverage, voteCount, locale),
        firstAirYear?.let { DetailStat(Icons.Filled.CalendarMonth, it, seasonsLabel ?: stringResource(R.string.title_page_stat_released)) },
        episodeCount?.let {
            DetailStat(
                Icons.Filled.VideoLibrary,
                NumberFormat.getIntegerInstance(locale).format(it),
                stringResource(R.string.series_stat_episodes),
            )
        },
        ratedStat(certification),
    )
}

/** What the page says below its hero. A section the server gave nothing for is not drawn. */
@Composable
internal fun SeriesPageBody(
    state: SeriesPageUiState.Ready,
    actions: TitlePageActions,
) {
    val series = state.series
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
        TitleStatsRows(series.titleStats(locale))
        TitleActions(
            model = titleActionsModel(series.standard, series.fourK),
            onRequest = actions.onRequest,
            onTrailer = series.videos.trailerUrl()?.let { url -> { actions.onOpenUrl(url) } },
            onShare = { context.shareTitle(series.name, series.webUrl) },
        )
        OverviewSection(series.overview)
        SeasonsGroup(series.seasons)
        InfoRowList(series.factEntries(context.resources, locale, state.extras?.ratings))
        PeopleRow(stringResource(R.string.title_page_cast), series.cast, actions.onOpenPerson, actions.onOpenCredits)
        PeopleRow(stringResource(R.string.title_page_crew), series.keyCrew, actions.onOpenPerson, actions.onOpenCredits)
        series.providers?.takeIf { it.streaming.isNotEmpty() }?.let { providers ->
            SectionHeader(stringResource(R.string.title_page_where_to_watch))
            TagRow(providers.streaming.map { it.name })
        }
        state.extras?.let { TitleRow(stringResource(R.string.title_page_more_like_this), it.moreLikeThis, actions.onOpenTitle) }
        KeywordsSection(series.keywords)
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
    Column {
        SectionHeader(stringResource(R.string.series_page_seasons))
        ItemGroup(
            title = null,
            rows = rows,
            modifier = Modifier.padding(horizontal = dimensionResource(DesR.dimen.padding_m)),
        )
    }
}
