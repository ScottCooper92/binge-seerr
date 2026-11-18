package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.discover.title.TitleCompany
import com.cooper.binge.companion.seerr.discover.title.TitleExtras
import com.cooper.binge.companion.seerr.discover.title.TitlePerson
import com.cooper.binge.companion.seerr.discover.title.TitleRatings
import com.cooper.binge.companion.seerr.ui.discover.title.TitlePageActions
import com.cooper.binge.companion.seerr.ui.discover.title.TitlePageScreen
import com.cooper.binge.companion.seerr.ui.discover.title.TitlePageUiState
import com.cooper.binge.companion.seerr.ui.discover.title.movieFixture
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val RATING_CRITICS = 83
private const val RATING_AUDIENCE = 85
private const val RATING_IMDB = 8.7

private fun worstCase(): TitlePageUiState.Ready {
    val movie =
        movieFixture().copy(
            tagline = "One ring to rule them all, one ring to find them, one ring to bring them all and in the darkness bind them",
            genres = listOf("Adventure", "Fantasy", "Action", "Epic Historical Drama"),
            studios =
                listOf(
                    TitleCompany(1, "New Line Cinema"),
                    TitleCompany(2, "WingNut Films"),
                    TitleCompany(3, "The Saul Zaentz Company"),
                ),
            cast = listOf(TitlePerson(1, "Elijah Wood-Montgomery-Fitzgerald", "Frodo Baggins of the Shire", null)),
        )
    val extras =
        TitleExtras(
            TitleRatings(RATING_CRITICS, RATING_AUDIENCE, RATING_IMDB),
            listOf(TitleResult(TitleKind.Movie, 2, "Related", "2003", null, null, null, null, TitleAvailability.None, null)),
            emptyList(),
        )
    return TitlePageUiState.Ready(movie, extras)
}

private val NO_ACTIONS = TitlePageActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

/** The film page with long facts and every rating, at font scales 1.3 and 2.0, through the design system's clipping check. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitlePageClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(fontScale: Float) = rule.assertNoClippingAt(fontScale) { TitlePageScreen(worstCase(), emptyFlow(), NO_ACTIONS) }

    @Test fun `the film page at 1_3`() = check(FONT_SCALE_13)

    @Test fun `the film page at 2_0`() = check(FONT_SCALE_20)
}

/** The same page in Spanish. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitlePageSpanishClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(fontScale: Float) = rule.assertNoClippingAt(fontScale) { TitlePageScreen(worstCase(), emptyFlow(), NO_ACTIONS) }

    @Test fun `the film page at 1_3 in Spanish`() = check(FONT_SCALE_13)

    @Test fun `the film page at 2_0 in Spanish`() = check(FONT_SCALE_20)
}
