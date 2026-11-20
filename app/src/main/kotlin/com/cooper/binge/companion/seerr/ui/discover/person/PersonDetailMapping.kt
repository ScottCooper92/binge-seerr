package com.cooper.binge.companion.seerr.ui.discover.person

import android.content.res.Resources
import com.binge.designsystem.component.MediaCardStatus
import com.binge.designsystem.component.PersonFact
import com.binge.designsystem.template.CreditSection
import com.binge.designsystem.template.CreditsState
import com.binge.designsystem.template.GridItem
import com.binge.designsystem.template.PersonDetail
import com.binge.designsystem.template.PersonDetailState
import com.binge.designsystem.template.ScreenAction
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.person.PersonCredit
import com.cooper.binge.companion.seerr.discover.person.PersonDetails
import com.cooper.binge.companion.seerr.discover.person.PersonGender
import com.cooper.binge.companion.seerr.ui.AllowLocalNetwork
import com.cooper.binge.companion.seerr.ui.discover.title.statusStyle
import com.cooper.binge.companion.seerr.ui.discover.title.toDisplayDate
import com.cooper.binge.companion.seerr.ui.state.messageRes
import com.cooper.binge.companion.seerr.ui.state.titleRes
import com.cooper.binge.companion.seerr.ui.state.toErrorKind
import java.text.NumberFormat
import java.util.Locale

private const val CAST_SECTION = "cast"
private const val CREW_SECTION = "crew"

/** The person page's state as the shared template takes it: plain values, with every word already in the user's language. */
internal fun PersonPageUiState.toDetailState(
    resources: Resources,
    locale: Locale,
    allow: AllowLocalNetwork? = null,
): PersonDetailState =
    when (this) {
        PersonPageUiState.Loading -> PersonDetailState.Loading()
        is PersonPageUiState.Error ->
            PersonDetailState.Failed(
                kind = error.toErrorKind(),
                title = resources.getString(error.titleRes()),
                message = resources.getString(error.messageRes()),
                retryable = allow == null,
                secondary = allow?.let { ScreenAction(resources.getString(it.label), it.run) },
            )
        is PersonPageUiState.Ready ->
            PersonDetailState.Content(
                PersonDetail(
                    name = person.name,
                    photoUrl = person.photoUrl,
                    eyebrow = person.knownFor,
                    facts = person.facts(resources, locale),
                    biography = person.biography,
                    biographyHeading = resources.getString(R.string.person_page_biography),
                    credits = credits.toCreditsState(resources),
                    refreshing = refreshing,
                ),
            )
    }

private fun PersonCreditsState.toCreditsState(resources: Resources): CreditsState =
    when (this) {
        PersonCreditsState.Loading -> CreditsState.Loading
        is PersonCreditsState.Error -> CreditsState.Failed(message = resources.getString(error.messageRes()))
        is PersonCreditsState.Ready ->
            CreditsState.Ready(
                listOf(
                    CreditSection(
                        CAST_SECTION,
                        resources.getString(R.string.person_page_known_for),
                        credits.cast.map { it.toGridItem(resources) },
                    ),
                    CreditSection(
                        CREW_SECTION,
                        resources.getString(R.string.title_page_crew),
                        credits.crew.map { it.toGridItem(resources) },
                    ),
                ),
            )
    }

private fun PersonCredit.toGridItem(resources: Resources) =
    GridItem(
        key = key,
        posterUrl = posterUrl,
        title = title,
        rating = rating,
        status = availability.statusStyle()?.let { MediaCardStatus(resources.getString(it.label), it.sentiment) },
        subtitle = role,
    )

/** The label and value of each fact the server gave, in reading order. A fact it did not give is left out. */
private fun PersonDetails.facts(
    resources: Resources,
    locale: Locale,
): List<PersonFact> {
    val born = birthday.toDisplayDate(locale)
    val died =
        deathday.toDisplayDate(locale)?.let { date -> ageAtDeath?.let { resources.getString(R.string.person_died_aged, date, it) } ?: date }
    val genderText =
        when (gender) {
            PersonGender.Female -> resources.getString(R.string.person_gender_female)
            PersonGender.Male -> resources.getString(R.string.person_gender_male)
            PersonGender.NonBinary -> resources.getString(R.string.person_gender_non_binary)
            null -> null
        }
    val popularityText = popularity?.let { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(it) }
    return listOfNotNull(
        alsoKnownAs.takeIf { it.isNotEmpty() }?.let {
            PersonFact(
                it.joinToString(", "),
                resources.getString(R.string.person_page_also_known_as),
            )
        },
        born?.let { PersonFact(it, resources.getString(R.string.person_page_born)) },
        died?.let { PersonFact(it, resources.getString(R.string.person_page_died)) },
        placeOfBirth?.takeIf { it.isNotBlank() }?.let { PersonFact(it, resources.getString(R.string.person_stat_from)) },
        genderText?.let { PersonFact(it, resources.getString(R.string.person_stat_gender)) },
        popularityText?.let { PersonFact(it, resources.getString(R.string.person_stat_popularity)) },
    )
}
