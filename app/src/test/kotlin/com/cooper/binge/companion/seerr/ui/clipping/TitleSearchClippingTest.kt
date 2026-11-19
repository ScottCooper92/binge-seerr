package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.search.title.SearchType
import com.cooper.binge.companion.seerr.ui.search.title.TitleSearchActions
import com.cooper.binge.companion.seerr.ui.search.title.TitleSearchScreen
import com.cooper.binge.companion.seerr.ui.search.title.TitleSearchUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val DRAMA = 18
private const val MYSTERY = 9648

private val GENRES = mapOf(DRAMA to "Drama", MYSTERY to "Mystery and Suspense")

private fun row(
    id: Int,
    kind: TitleKind,
    name: String,
    availability: TitleAvailability,
) = TitleResult(kind, id, name, "2021", null, null, null, 8.4, availability, null, listOf(DRAMA, MYSTERY))

private val STATES: List<TitleSearchUiState> =
    listOf(
        TitleSearchUiState.Ready(
            text = "a search term that is rather long for a phone",
            results =
                listOf(
                    row(
                        1,
                        TitleKind.Series,
                        "The Extraordinarily Long and Winding Chronicle of a Patient Lighthouse Keeper",
                        TitleAvailability.PartiallyAvailable,
                    ),
                    row(2, TitleKind.Movie, "Sintel", TitleAvailability.Pending),
                    row(3, TitleKind.Person, "Alexandria Fitzgerald-Montgomery", TitleAvailability.None),
                ),
            loaded = 3,
            type = SearchType.All,
            counts = mapOf(SearchType.All to 1234, SearchType.Movies to 12, SearchType.Series to 31, SearchType.People to 7),
            genreNames = GENRES,
            hasMore = true,
            appending = false,
            appendFailed = true,
        ),
        TitleSearchUiState.Idle(
            "",
            listOf("big buck", "a search long enough that it has to be cut short at the end of its row on a phone"),
        ),
        TitleSearchUiState.Error("lighthouse", SeerrError.Unreachable),
    )

private val NO_ACTIONS = TitleSearchActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {})

/** The Search tab's states at their worst, at font scales 1.3 and 2.0, through the design system's `assertNoTextClipped`. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h4000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleSearchClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(
        state: TitleSearchUiState,
        fontScale: Float,
    ) = rule.assertNoClippingAt(fontScale) { TitleSearchScreen(state, NO_ACTIONS) }

    @Test fun `results at 1_3`() = check(STATES[0], FONT_SCALE_13)

    @Test fun `results at 2_0`() = check(STATES[0], FONT_SCALE_20)

    @Test fun `recents at 1_3`() = check(STATES[1], FONT_SCALE_13)

    @Test fun `recents at 2_0`() = check(STATES[1], FONT_SCALE_20)

    @Test fun `a failure at 2_0`() = check(STATES[2], FONT_SCALE_20)
}

/** The same states in Spanish, whose strings are the longer. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-w412dp-h4000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitleSearchSpanishClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(
        state: TitleSearchUiState,
        fontScale: Float,
    ) = rule.assertNoClippingAt(fontScale) { TitleSearchScreen(state, NO_ACTIONS) }

    @Test fun `results at 1_3`() = check(STATES[0], FONT_SCALE_13)

    @Test fun `results at 2_0`() = check(STATES[0], FONT_SCALE_20)

    @Test fun `recents at 2_0`() = check(STATES[1], FONT_SCALE_20)

    @Test fun `a failure at 2_0`() = check(STATES[2], FONT_SCALE_20)
}
