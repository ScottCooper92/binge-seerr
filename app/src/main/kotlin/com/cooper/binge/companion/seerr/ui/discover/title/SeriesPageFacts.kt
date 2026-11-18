package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.res.Resources
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.binge.companion.sdk.attempt
import com.binge.designsystem.component.ListItem
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.SeriesDetails
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The show's facts as the rows of one group: each has an icon, and none opens anything. A fact the server did not give is left out. */
internal fun SeriesDetails.factRows(
    resources: Resources,
    locale: Locale,
): List<ListItem> {
    fun row(
        icon: ImageVector,
        label: Int,
        value: String?,
    ) = value?.takeIf { it.isNotBlank() }?.let { ListItem(icon = icon, label = resources.getString(label), detail = it, clickable = false) }

    fun language(code: String) = Locale(code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    val statusText = status?.let { seriesStatusRes(it)?.let(resources::getString) ?: it }
    val next = nextEpisode
    return listOfNotNull(
        row(Icons.Filled.Info, R.string.title_page_fact_status, statusText),
        row(Icons.Filled.LiveTv, R.string.series_fact_networks, networks.joinToString(", ")),
        row(Icons.Filled.Person, R.string.series_fact_creators, creators.joinToString(", ")),
        row(Icons.Filled.VideoLibrary, R.string.series_fact_episodes, episodeCount?.let { locale.formatNumber(it) }),
        row(Icons.Filled.CalendarMonth, R.string.series_fact_first_aired, firstAirDate.toDisplayDate(locale)),
        row(Icons.Filled.CalendarMonth, R.string.series_fact_last_aired, lastAirDate.toDisplayDate(locale).takeUnless { next != null }),
        row(
            Icons.Filled.Event,
            R.string.series_fact_next_episode,
            next?.let { resources.getString(R.string.series_next_episode, it.name, it.seasonNumber, it.episodeNumber) },
        ),
        row(Icons.Filled.Event, R.string.series_fact_next_airs, next?.airDate.toDisplayDate(locale)),
        row(Icons.Filled.Language, R.string.title_page_fact_original_language, originalLanguage?.let(::language)),
        row(
            Icons.Filled.Translate,
            R.string.title_page_fact_spoken_languages,
            spokenLanguageCodes.joinToString(", ", transform = ::language),
        ),
    )
}

/** A `yyyy-mm-dd` date written the way the reader's own language writes it; null for one that does not parse. */
internal fun String?.toDisplayDate(locale: Locale): String? =
    this?.let { attemptDate(it) }?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

private fun attemptDate(text: String): LocalDate? = attempt { LocalDate.parse(text.take(ISO_DATE_LENGTH)) }.getOrNull()

private const val ISO_DATE_LENGTH = 10

private fun Locale.formatNumber(value: Int): String =
    java.text.NumberFormat
        .getIntegerInstance(this)
        .format(value)

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
