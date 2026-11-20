package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.TitleResult
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val TITLES =
    listOf(
        TitleResult(TitleKind.Movie, 1, "Alpha Film", null, null, null, null, null, TitleAvailability.None, null, emptyList()),
        TitleResult(TitleKind.Series, 2, "Beta Show", null, null, null, null, null, TitleAvailability.None, null, emptyList()),
        TitleResult(TitleKind.Person, 3, "Gamma Person", null, null, null, null, null, TitleAvailability.None, null, emptyList()),
    )

private val LONG_LIST =
    (1..40).map {
        TitleResult(TitleKind.Movie, it, "Film $it", null, null, null, null, null, TitleAvailability.None, null, emptyList())
    }

private fun ready(
    type: SearchType,
    titles: List<TitleResult> = TITLES,
) = TitleSearchUiState.Ready(
    text = "x",
    results = titles.filter(type::keeps),
    loaded = titles.size,
    type = type,
    counts = titles.typeCounts(),
    genreNames = emptyMap(),
    hasMore = false,
    appending = false,
    appendFailed = false,
    hasStudios = false,
)

/** The type chips sit over a pager: a swipe moves the chip, a chip tap moves the page, and each page lists its own type. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class TitleSearchPagerTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private var type by mutableStateOf(SearchType.All)
    private val asked = mutableListOf<SearchType>()

    private fun show(titles: List<TitleResult> = TITLES) {
        rule.setContent {
            SeerrTheme {
                TitleSearchScreen(
                    ready(type, titles),
                    TitleSearchActions({}, {}, {}, {}, {}, {}, {}, {}, { selected ->
                        asked += selected
                        type = selected
                    }, { _, _ -> }, {}),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `the first page lists every title`() {
        show()

        rule.onNodeWithText("Alpha Film").assertIsDisplayed()
        rule.onNodeWithText("Beta Show").assertIsDisplayed()
        rule.onNodeWithText("Gamma Person").assertIsDisplayed()
    }

    @Test
    fun `a chip tap moves the page to that type's results`() {
        show()

        rule.onNodeWithText("People").performClick()
        rule.waitForIdle()

        assertEquals(listOf(SearchType.People), asked)
        rule.onNodeWithText("Gamma Person").assertIsDisplayed()
        rule.onNodeWithText("Alpha Film").assertDoesNotExist()
    }

    @Test
    fun `a swipe left moves the chip to the next type and a swipe right brings it back`() {
        show()

        rule.onRoot().performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(SearchType.Movies, type)
        rule.onNodeWithText("Beta Show").assertDoesNotExist()

        rule.onRoot().performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(SearchType.All, type)
        rule.onNodeWithText("Beta Show").assertIsDisplayed()
    }
}
