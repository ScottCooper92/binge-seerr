package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.person.PersonCredit
import com.cooper.binge.companion.seerr.discover.person.PersonCredits
import com.cooper.binge.companion.seerr.discover.person.PersonDetails
import com.cooper.binge.companion.seerr.discover.person.PersonGender
import com.cooper.binge.companion.seerr.ui.discover.person.PersonCreditsState
import com.cooper.binge.companion.seerr.ui.discover.person.PersonPageActions
import com.cooper.binge.companion.seerr.ui.discover.person.PersonPageScreen
import com.cooper.binge.companion.seerr.ui.discover.person.PersonPageUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val PERSON_LONG_TITLE = "The Chronicles of the Extraordinarily Long-Winded Winter Kingdom and Its Many Peculiar Inhabitants"
private const val PERSON_LONG_ROLE = "Frodo Baggins of the Shire, The Ringbearer, and Sméagol's Unfortunate Cousin"
private const val PERSON_CREDITS = 6

private fun worstCase(): PersonPageUiState.Ready {
    val person =
        PersonDetails(
            personId = 5,
            name = "Alexandria Wolfeschlegelsteinhausenbergerdorff-Montgomery",
            knownFor = "Acting and Directing and Producing",
            biography = "A long biography that runs on for a good while. ".repeat(20),
            birthday = "1980-02-14",
            deathday = "2020-12-24",
            placeOfBirth = "Llanfairpwllgwyngyllgogerychwyrndrobwllllantysiliogogogoch, Wales, United Kingdom",
            alsoKnownAs = listOf("Alexandria Montgomery-Fitzgerald", "A. W. Montgomery", "Lex Wolfe"),
            gender = PersonGender.NonBinary,
            popularity = 123.456,
            imdbId = "nm0000123",
            homepage = "https://a-very-long-homepage-address.example.com/people/alexandria",
            photoUrl = null,
            webUrl = "https://seerr.example/person/5",
        )
    val credits =
        (1..PERSON_CREDITS).map {
            PersonCredit(TitleKind.Movie, it, PERSON_LONG_TITLE, 7.5f, null, PERSON_LONG_ROLE, TitleAvailability.Processing)
        }
    return PersonPageUiState.Ready(
        person,
        PersonCreditsState.Ready(
            PersonCredits(
                credits,
                credits.map {
                    it.copy(
                        tmdbId =
                            it.tmdbId + PERSON_CREDITS,
                    )
                },
            ),
        ),
    )
}

private val NO_ACTIONS = PersonPageActions({}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> })

/** The person page with the longest names, a long biography and a credit in every row, at font scales 1.3 and 2.0. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonPageClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(fontScale: Float) =
        rule.assertNoClippingAt(fontScale) {
            PersonPageScreen(worstCase(), NO_ACTIONS)
        }

    @Test fun `the person page at 1_3`() = check(FONT_SCALE_13)

    @Test fun `the person page at 2_0`() = check(FONT_SCALE_20)
}

/** The same page in Spanish. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonPageSpanishClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(fontScale: Float) =
        rule.assertNoClippingAt(fontScale) {
            PersonPageScreen(worstCase(), NO_ACTIONS)
        }

    @Test fun `the person page at 1_3 in Spanish`() = check(FONT_SCALE_13)

    @Test fun `the person page at 2_0 in Spanish`() = check(FONT_SCALE_20)
}
