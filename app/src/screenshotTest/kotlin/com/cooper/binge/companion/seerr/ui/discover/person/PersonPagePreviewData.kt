package com.cooper.binge.companion.seerr.ui.discover.person

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.person.PersonCredit
import com.cooper.binge.companion.seerr.discover.person.PersonCredits
import com.cooper.binge.companion.seerr.discover.person.PersonDetails

private const val FIRST_YEAR = 2024
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
        photoUrl = null,
    )

/** A person of whom the server knows only the name. */
internal fun sparsePerson() = PersonDetails(5, "Sam Example", null, null, null, null, null, emptyList(), null)

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
    date = "${FIRST_YEAR - index}-01-01",
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
    filter: CreditsFilter = CreditsFilter.All,
) = PersonPageUiState.Ready(person, credits, filter)
