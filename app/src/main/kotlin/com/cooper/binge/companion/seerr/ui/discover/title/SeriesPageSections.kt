package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
import com.binge.designsystem.isExpandedLayout
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.discover.title.trailerUrl
import com.cooper.binge.companion.seerr.ui.discover.request.joinMeta
import java.text.NumberFormat
import java.util.Locale
import com.binge.designsystem.R as DesR

private const val MINUTES_PER_HOUR = 60

/** The line under the title in the request sheet: first air year and season count, whichever the server gave. */
@Composable
internal fun SeriesDetails.requestMeta(): String? {
    val seasons = seasonCount?.let { pluralStringResource(R.plurals.request_sheet_season_count, it, it) }
    return joinMeta(listOf(firstAirYear, seasons), stringResource(R.string.request_sheet_meta_separator))
}

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
    seasonActions: SeasonActions,
) {
    val series = state.series
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m))) {
        // The expanded header already shows these and the synopsis.
        val expanded = isExpandedLayout()
        if (!expanded) TitleStatsRows(series.titleStats(locale))
        TitleActions(
            model = titleActionsModel(series.standard, series.fourK),
            onRequest = actions.onRequest,
            onTrailer = series.videos.trailerUrl()?.let { url -> { actions.onOpenUrl(url) } },
            modifier = Modifier.readingMargin().layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.ACTIONS)),
        )
        if (!expanded && series.overview != null) Box(Modifier.readingMargin()) { OverviewSection(series.overview) }
        EpisodesSection(state, seasonActions, Modifier.readingMargin())
        InfoRowList(
            series.factEntries(
                context.resources,
                locale,
                state.extras?.ratings,
                actions.onOpenCompany.takeIf {
                    series.companiesOpen
                },
                actions.onOpenPerson,
            ),
            Modifier.readingMargin(),
        )
        PeopleRow(stringResource(R.string.title_page_cast), series.cast, actions.onOpenPerson, actions.onOpenCredits)
        PeopleRow(stringResource(R.string.title_page_crew), series.keyCrew, actions.onOpenPerson, actions.onOpenCredits)
        WhereToWatchSection(series.providers, actions.onOpenUrl, Modifier.readingMargin())
        state.extras?.let { TitleRow(stringResource(R.string.title_page_more_like_this), it.moreLikeThis, actions.onOpenTitle) }
        KeywordsSection(series.keywords, Modifier.readingMargin())
    }
}
