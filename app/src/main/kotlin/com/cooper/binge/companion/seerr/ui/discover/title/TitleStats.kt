package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.component.DetailStatRow
import com.binge.designsystem.formatVoteCount
import com.binge.designsystem.layout.LayoutAnchors
import com.binge.designsystem.layout.layoutAnchor
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import java.util.Locale

private const val MINUTES_PER_HOUR = 60
private const val LARGE_FONT_SCALE = 1.5f

/** The movie's stats, as Binge shows them: rating with its votes, release year, runtime and certification. */
@Composable
internal fun MovieDetails.titleStats(locale: Locale): List<DetailStat> {
    val runtime = runtimeMinutes?.let { stringResource(R.string.title_page_runtime, it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }
    return listOfNotNull(
        ratingStat(voteAverage, voteCount, locale),
        year?.let { DetailStat(Icons.Filled.CalendarMonth, it, stringResource(R.string.title_page_stat_released)) },
        runtime?.let { DetailStat(Icons.Filled.Schedule, it, stringResource(R.string.title_page_stat_runtime)) },
        ratedStat(certification),
    )
}

/** The rating with the count of votes under it, where the server gave one; the plain word where it did not. */
@Composable
internal fun ratingStat(
    voteAverage: Double?,
    voteCount: Int?,
    locale: Locale,
): DetailStat? {
    val votes = voteCount?.let { stringResource(R.string.title_page_stat_votes, it.formatVoteCount(locale)) }
    val label = votes ?: stringResource(R.string.title_page_stat_rating)
    return voteAverage?.let { DetailStat(Icons.Filled.Star, "%.1f".format(locale, it), label) }
}

@Composable
internal fun ratedStat(certification: String?): DetailStat? =
    certification?.let { DetailStat(Icons.Outlined.Shield, it, stringResource(R.string.title_page_stat_rated)) }

/** How many stats the page puts on a row: all of them, or two at a large font. The skeleton lays its cells out by the same rule. */
@Composable
internal fun statsPerRow(): Int = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 2 else Int.MAX_VALUE

/** At a large font a label of four across breaks mid-word, so the stats go two to a row from [LARGE_FONT_SCALE]. */
@Composable
internal fun TitleStatsRows(stats: List<DetailStat>) {
    val perRow = statsPerRow()
    val margin = Modifier.readingMargin()
    stats.chunked(perRow).forEachIndexed { index, row ->
        DetailStatRow(
            stats = row,
            modifier = if (index == 0) margin.layoutAnchor(LayoutAnchors.section(LayoutAnchors.Detail.STATS)) else margin,
        )
    }
}
