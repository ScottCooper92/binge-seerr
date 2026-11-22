package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.res.Resources
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import com.binge.designsystem.component.DetailInfoEntry
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.discover.title.TitleCompany
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * The film's facts for the Details grid at the end of the page: who directed it, when it came out, its languages and studios, its
 * status and its money. The scores are in the header, and the collection has its own rail. A fact the server did not give is left out.
 */
internal fun MovieDetails.detailEntries(
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
    // TMDB money is dollars, written the way the reader's own language writes them.
    val money =
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance("USD")
            maximumFractionDigits = 0
        }
    return listOf(
        peopleEntry(Icons.Filled.Videocam, resources.getString(R.string.title_page_fact_director), keyCrew.directors(), onOpenPerson),
        entry(Icons.Filled.CalendarMonth, R.string.title_page_fact_release_date, releaseDate.toDisplayDate(locale)),
        entry(Icons.Filled.Language, R.string.title_page_fact_original_language, originalLanguage?.let { languageName(it, locale) }),
        entry(
            Icons.Filled.Translate,
            R.string.title_page_fact_spoken_languages,
            spokenLanguageCodes.joinToString(", ") {
                languageName(it, locale)
            },
        ),
        companyEntry(Icons.Filled.Business, resources.getString(R.string.title_page_fact_studios), studios, onOpenCompany),
        entry(Icons.Filled.Info, R.string.title_page_fact_status, status?.let { statusLabelRes(it)?.let(resources::getString) ?: it }),
        entry(Icons.Filled.Payments, R.string.title_page_fact_budget, budget?.let(money::format)),
        entry(Icons.Filled.TrendingUp, R.string.title_page_fact_revenue, revenue?.let(money::format)),
    )
}

/** The people whose billed job includes directing; the server lists a job as plain text, as TMDB writes it. */
private fun List<TitlePerson>.directors(): List<TitlePerson> =
    filter { person ->
        person.role
            .orEmpty()
            .split(", ")
            .contains(DIRECTOR_JOB)
    }

private const val DIRECTOR_JOB = "Director"

/** The server's film status in the user's language; null for a status this app does not know, which is shown as the server wrote it. */
private fun statusLabelRes(status: String): Int? =
    when (status) {
        "Released" -> R.string.title_page_status_released
        "Post Production" -> R.string.title_page_status_post_production
        "In Production" -> R.string.title_page_status_in_production
        "Planned" -> R.string.title_page_status_planned
        "Rumored" -> R.string.title_page_status_rumored
        "Canceled" -> R.string.title_page_status_canceled
        else -> null
    }
