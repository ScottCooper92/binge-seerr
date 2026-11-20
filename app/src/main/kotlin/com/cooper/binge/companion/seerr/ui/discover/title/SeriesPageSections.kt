package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailStat
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.ui.discover.request.joinMeta
import java.text.NumberFormat
import java.util.Locale

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
