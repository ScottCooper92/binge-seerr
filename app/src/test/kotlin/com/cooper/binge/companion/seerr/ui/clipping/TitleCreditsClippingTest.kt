package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.discover.title.CreditsDepartment
import com.cooper.binge.companion.seerr.discover.title.TitleCredits
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.ui.discover.title.credits.TitleCreditsScreen
import com.cooper.binge.companion.seerr.ui.discover.title.credits.TitleCreditsUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_NAME_ROLE = "Frodo Baggins of the Shire, bearer of the one ring, son of Drogo"

private fun worstCase() =
    TitleCreditsUiState.Ready(
        TitleCredits(
            cast = listOf(TitlePerson(1, "Elijah Wood-Montgomery-Fitzgerald", LONG_NAME_ROLE, null)),
            departments =
                listOf(
                    CreditsDepartment(
                        "Costume & Make-Up",
                        listOf(TitlePerson(2, "Ngila Dickson-Featherstonehaugh", "Costume Designer", null)),
                    ),
                    CreditsDepartment(
                        "Visual Effects",
                        listOf(TitlePerson(3, "Jim Rygiel", "Visual Effects Supervisor, Digital Compositor", null)),
                    ),
                ),
        ),
    )

/** The cast and crew list at its worst: long names and roles, at font scales 1.3 and 2.0. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleCreditsClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(fontScale: Float) = rule.assertNoClippingAt(fontScale) { TitleCreditsScreen(worstCase(), {}, {}) }

    @Test fun `the credits list at 1_3`() = check(FONT_SCALE_13)

    @Test fun `the credits list at 2_0`() = check(FONT_SCALE_20)
}

/** The same list in Spanish. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleCreditsSpanishClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(fontScale: Float) = rule.assertNoClippingAt(fontScale) { TitleCreditsScreen(worstCase(), {}, {}) }

    @Test fun `the credits list at 1_3 in Spanish`() = check(FONT_SCALE_13)

    @Test fun `the credits list at 2_0 in Spanish`() = check(FONT_SCALE_20)
}
