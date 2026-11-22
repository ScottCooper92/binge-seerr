package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.res.Resources
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.binge.companion.sdk.attempt
import com.binge.designsystem.component.DetailInfoEntry
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import com.cooper.binge.companion.seerr.discover.title.TitleCompany
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The show's facts for the Details grid at the end of the page: who made it, where it airs, when it ran, its status and languages. The
 * scores are in the header. A fact the server did not give is left out.
 */
internal fun SeriesDetails.detailEntries(
    resources: Resources,
    locale: Locale,
    onOpenCompany: ((TitleCompany) -> Unit)? = null,
    onOpenPerson: ((Int) -> Unit)? = null,
): List<DetailInfoEntry> {
    fun entry(
        icon: ImageVector,
        label: Int,
        value: String?,
    ) = DetailInfoEntry(icon, resources.getString(label), value)
    val next = nextEpisode
    return listOf(
        peopleEntry(Icons.Filled.Videocam, resources.getString(R.string.series_fact_creators), creators, onOpenPerson),
        companyEntry(Icons.Filled.LiveTv, resources.getString(R.string.series_fact_networks), networks, onOpenCompany),
        entry(Icons.Filled.Info, R.string.title_page_fact_status, status?.let { seriesStatusRes(it)?.let(resources::getString) ?: it }),
        entry(Icons.Filled.CalendarMonth, R.string.series_fact_first_aired, firstAirDate.toDisplayDate(locale)),
        entry(
            Icons.Filled.EventAvailable,
            R.string.series_fact_last_aired,
            lastAirDate.toDisplayDate(locale).takeUnless { next != null },
        ),
        entry(
            Icons.Filled.SkipNext,
            R.string.series_fact_next_episode,
            next?.let { resources.getString(R.string.series_next_episode, it.name, it.seasonNumber, it.episodeNumber) },
        ),
        entry(Icons.Filled.Event, R.string.series_fact_next_airs, next?.airDate.toDisplayDate(locale)),
        entry(
            Icons.Filled.Schedule,
            R.string.title_page_stat_runtime,
            episodeRuntimeMinutes?.let {
                resources.getString(R.string.series_page_runtime, it)
            },
        ),
        entry(Icons.Filled.Language, R.string.title_page_fact_original_language, originalLanguage?.let { languageName(it, locale) }),
        entry(
            Icons.Filled.Translate,
            R.string.title_page_fact_spoken_languages,
            spokenLanguageCodes.joinToString(", ") {
                languageName(it, locale)
            },
        ),
    )
}

/** A `yyyy-mm-dd` date written the way the reader's own language writes it; null for one that does not parse. */
internal fun String?.toDisplayDate(locale: Locale): String? =
    this?.let { attemptDate(it) }?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

private fun attemptDate(text: String): LocalDate? = attempt { LocalDate.parse(text.take(ISO_DATE_LENGTH)) }.getOrNull()

private const val ISO_DATE_LENGTH = 10

/** The server's show status in the user's language; null for one this app does not know, which is shown as the server wrote it. */
private fun seriesStatusRes(status: String): Int? =
    when (status) {
        "Returning Series" -> R.string.series_status_returning
        "Ended" -> R.string.series_status_ended
        "Canceled" -> R.string.title_page_status_canceled
        "In Production" -> R.string.title_page_status_in_production
        "Planned" -> R.string.title_page_status_planned
        "Pilot" -> R.string.series_status_pilot
        else -> null
    }
