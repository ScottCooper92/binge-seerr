package com.cooper.binge.companion.seerr.ui.discover.title

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.DetailStat
import com.binge.designsystem.formatVoteCount
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import java.util.Locale

private const val MINUTES_PER_HOUR = 60

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
    certification?.let { DetailStat(Icons.Filled.Shield, it, stringResource(R.string.title_page_stat_rated)) }
