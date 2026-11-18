package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.res.Resources
import com.binge.designsystem.component.InfoRowEntry
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.title.MovieDetails
import com.cooper.binge.companion.seerr.discover.title.TitleCompany
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * The film's facts as the label and value rows Binge shows: who directed it, when it came out, its languages and studios,
 * its collection, its status, and the money and ratings the server holds. A fact the server did not give is left out.
 */
internal fun MovieDetails.factEntries(
    resources: Resources,
    locale: Locale,
    ratings: TitleRatings?,
    onOpenCompany: ((TitleCompany) -> Unit)? = null,
    onOpenPerson: ((Int) -> Unit)? = null,
): List<InfoRowEntry> {
    fun entry(
        label: Int,
        value: String?,
    ) = InfoRowEntry(resources.getString(label), value)
    // TMDB money is dollars, written the way the reader's own language writes them.
    val money =
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance("USD")
            maximumFractionDigits = 0
        }
    return listOf(
        peopleEntry(resources.getString(R.string.title_page_fact_director), keyCrew.directors(), onOpenPerson),
        entry(R.string.title_page_fact_release_date, releaseDate.toDisplayDate(locale)),
        entry(R.string.title_page_fact_original_language, originalLanguage?.let { languageName(it, locale) }),
        entry(R.string.title_page_fact_spoken_languages, spokenLanguageCodes.joinToString(", ") { languageName(it, locale) }),
        companyEntry(resources.getString(R.string.title_page_fact_studios), studios, onOpenCompany),
        entry(R.string.title_page_collection, collection?.name),
        entry(R.string.title_page_fact_status, status?.let { statusLabelRes(it)?.let(resources::getString) ?: it }),
        entry(R.string.title_page_fact_budget, budget?.let(money::format)),
        entry(R.string.title_page_fact_revenue, revenue?.let(money::format)),
    ) + ratingEntries(resources, locale, ratings)
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
