package com.cooper.binge.companion.seerr.ui.discover.title

import android.content.res.Resources
import androidx.compose.ui.graphics.vector.ImageVector
import com.binge.designsystem.component.DetailInfoEntry
import com.binge.designsystem.component.ExternalScore
import com.binge.designsystem.component.ExternalScoreKind
import com.binge.designsystem.component.InfoLink
import com.binge.designsystem.component.InfoValue
import com.cooper.binge.companion.seerr.discover.title.TitleCompany
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import com.cooper.binge.companion.seerr.feature.discover.R
import java.util.Locale

/** A language code written in the reader's own language, capitalised: "es" reads "Spanish" or "Español". */
fun languageName(
    code: String,
    locale: Locale,
): String = Locale(code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }

/** The critics' score at or above this reads as fresh (a tomato), below it as rotten (a splat), as Rotten Tomatoes draws it. */
private const val FRESH_THRESHOLD_PERCENT = 60

/**
 * The server's other ratings as the header's scores, in the order Binge lists them: critics, audience, IMDb. A score the server did
 * not give is left out. Each carries the words a screen reader reads for it, in the reader's language.
 */
fun TitleRatings?.toExternalScores(
    resources: Resources,
    locale: Locale,
): List<ExternalScore> =
    listOfNotNull(
        this?.criticsPercent?.let { percent ->
            ExternalScore(
                if (percent >= FRESH_THRESHOLD_PERCENT) ExternalScoreKind.CriticsFresh else ExternalScoreKind.CriticsRotten,
                "$percent%",
                resources.getString(R.string.title_page_score_critics, percent),
            )
        },
        this?.audiencePercent?.let { percent ->
            ExternalScore(ExternalScoreKind.Audience, "$percent%", resources.getString(R.string.title_page_score_audience, percent))
        },
        this?.imdbScore?.let { score ->
            val value = "%.1f".format(locale, score)
            ExternalScore(ExternalScoreKind.Imdb, value, resources.getString(R.string.title_page_score_imdb, value))
        },
    )

/** A studio or network row: each name opens its grid where [onOpenCompany] is given, and is plain text where it is not. */
fun companyEntry(
    icon: ImageVector,
    label: String,
    companies: List<TitleCompany>,
    onOpenCompany: ((TitleCompany) -> Unit)?,
): DetailInfoEntry =
    if (onOpenCompany == null || companies.isEmpty()) {
        DetailInfoEntry(icon, label, companies.joinToString(", ") { it.name })
    } else {
        DetailInfoEntry(icon, label, InfoValue.Links(companies.map { company -> InfoLink(company.name) { onOpenCompany(company) } }))
    }

/** A directors or creators row: each name opens that person's page where [onOpenPerson] is given, and is plain text where it is not. */
fun peopleEntry(
    icon: ImageVector,
    label: String,
    people: List<TitlePerson>,
    onOpenPerson: ((Int) -> Unit)?,
): DetailInfoEntry =
    if (onOpenPerson == null || people.isEmpty()) {
        DetailInfoEntry(icon, label, people.joinToString(", ") { it.name })
    } else {
        DetailInfoEntry(icon, label, InfoValue.Links(people.map { person -> InfoLink(person.name) { onOpenPerson(person.personId) } }))
    }
