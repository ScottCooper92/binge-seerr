package com.cooper.binge.companion.seerr.ui.discover.person

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.person.PersonCredit
import com.cooper.binge.companion.seerr.discover.person.PersonCredits
import com.cooper.binge.companion.seerr.discover.person.PersonDetails
import com.cooper.binge.companion.seerr.discover.person.PersonGender

private const val FIRST_YEAR = 2024
private const val RATING_DIVISOR = 270f
private const val CAST_COUNT = 8
private const val CREW_COUNT = 3
private const val LONG_TITLE = "The Chronicles of the Extraordinarily Long-Winded Winter Kingdom and Its Many Peculiar Inhabitants"
private const val LONG_ROLE = "Frodo Baggins of the Shire, The Ringbearer, and Sméagol's Unfortunate Cousin"
private const val BIOGRAPHY =
    "A made-up actor who began on the stage and found their way to film. They are known for playing quiet, stubborn people " +
        "who keep a small town alive, and for refusing, in every interview, to say what happens next. They live between two cities " +
        "and have been on screen for more than twenty years, in comedies and in dramas, and once as a talking bird."

/** A person with everything the server gives. */
internal fun fullPerson() =
    PersonDetails(
        personId = 5,
        name = "Sam Example",
        knownFor = "Acting",
        biography = BIOGRAPHY,
        birthday = "1980-02-14",
        deathday = null,
        placeOfBirth = "Demo City, Exampleland",
        alsoKnownAs = listOf("The Demo", "S. Example"),
        gender = PersonGender.Female,
        popularity = 12.4,
        imdbId = "nm0000123",
        homepage = "https://sam.example.com",
        photoUrl = null,
        webUrl = "https://seerr.example/person/5",
    )

/** A person of whom the server knows only the name. */
internal fun sparsePerson() =
    PersonDetails(
        personId = 5,
        name = "Sam Example",
        knownFor = null,
        biography = null,
        birthday = null,
        deathday = null,
        placeOfBirth = null,
        alsoKnownAs = emptyList(),
        gender = null,
        popularity = null,
        imdbId = null,
        homepage = null,
        photoUrl = null,
        webUrl = "https://seerr.example/person/5",
    )

/** A person who has died, with no links, so the died-aged stat and the missing links row both show. */
internal fun lateWithoutLinks() = fullPerson().copy(deathday = "2020-12-24", imdbId = null, homepage = null)

/** The longest names the page has to hold. */
internal fun longPerson() =
    fullPerson().copy(
        name = "Alexandria Wolfeschlegelsteinhausenbergerdorff-Montgomery",
        placeOfBirth = "Llanfairpwllgwyngyllgogerychwyrndrobwllllantysiliogogogoch, Wales, United Kingdom",
        alsoKnownAs = listOf("Alexandria Montgomery-Fitzgerald", "A. W. Montgomery", "Lex Wolfe"),
        deathday = "2020-12-24",
    )

private fun credit(
    index: Int,
    kind: TitleKind,
    longNames: Boolean,
    availability: TitleAvailability,
) = PersonCredit(
    kind = kind,
    tmdbId = index,
    title = if (longNames) LONG_TITLE else "Title $index",
    rating = (FIRST_YEAR - index) / RATING_DIVISOR,
    posterUrl = null,
    role = if (longNames) LONG_ROLE else "Role $index",
    availability = availability,
)

/** A mixed filmography: films and series, one in each state a title can be in. */
internal fun fullCredits(longNames: Boolean = false): PersonCredits {
    val states = TitleAvailability.entries
    return PersonCredits(
        cast =
            (1..CAST_COUNT).map {
                credit(
                    it,
                    if (it % 2 ==
                        0
                    ) {
                        TitleKind.Series
                    } else {
                        TitleKind.Movie
                    },
                    longNames,
                    states[it % states.size],
                )
            },
        crew = (CAST_COUNT + 1..CAST_COUNT + CREW_COUNT).map { credit(it, TitleKind.Movie, longNames, TitleAvailability.None) },
    )
}

internal fun readyPerson(
    person: PersonDetails = fullPerson(),
    credits: PersonCreditsState = PersonCreditsState.Ready(fullCredits()),
    refreshing: Boolean = false,
) = PersonPageUiState.Ready(person, credits, refreshing)
