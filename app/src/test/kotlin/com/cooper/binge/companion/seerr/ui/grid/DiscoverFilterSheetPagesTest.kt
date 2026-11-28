package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverFilters
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.grid.FilterCapabilities
import com.cooper.binge.companion.seerr.discover.grid.FilterOption
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The filter sheet at the phone's width: a filter and a long-list pick are pages of the one sheet, not a second sheet over it, so
 * a pick lands back on the rows and a page's Back returns to them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverFilterSheetPagesTest {
    @get:Rule
    val rule: ComposeContentTestRule = createSeerrComposeRule()

    private val words =
        FilterVocabulary(
            genres = listOf(FilterOption("28", "Action"), FilterOption("35", "Comedy")),
            languages = listOf(FilterOption("en", "English"), FilterOption("ja", "Japanese")),
            capabilities = FilterCapabilities(),
        )
    private var applied: DiscoverFilters? = null

    /** The sheet is part-way up a small window, so a row can sit below its fold; the click is the row's own action, not a touch on it. */
    private fun SemanticsNodeInteraction.click() = performSemanticsAction(SemanticsActions.OnClick)

    private fun show() {
        rule.setContent {
            BingeExpressiveTheme {
                DiscoverFilterSheet(
                    kind = TitleKind.Movie,
                    sort = DiscoverSort.PopularityDesc,
                    filters = DiscoverFilters(),
                    vocabulary = words,
                    today = LocalDate.of(2026, 11, 26),
                    loads = FilterLoads({}, {}, {}),
                    onApply = { _, _, filters -> applied = filters },
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `picking a language lands back on the rows with it shown and applied by Show results`() {
        show()

        rule.onNodeWithText("Original language").click()
        rule.onNodeWithText("Japanese").click()

        rule.onNodeWithText("Filter by").assertExists()
        rule.onNodeWithText("Japanese").assertExists()
        rule.onNodeWithText("Show results").click()
        assertEquals("ja", applied?.language)
    }

    @Test
    fun `a filter's Back arrow returns to the rows`() {
        show()

        rule.onNodeWithText("Genres").click()
        rule.onNodeWithText("Action").assertExists()
        rule.onNodeWithContentDescription("Back").click()

        rule.onNodeWithText("Filter by").assertExists()
    }
}
