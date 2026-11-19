package com.cooper.binge.companion.seerr.ui.search.title

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.discover.sliders.HubGrid
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A search that finds nothing keeps its chips, names the query, and offers the unfiltered grid (#2334). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class TitleSearchNoResultsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var opened: HubGrid? = null

    private fun show(
        type: SearchType,
        browseAll: Boolean = true,
    ) {
        rule.setContent {
            SeerrTheme {
                TitleSearchScreen(
                    state = TitleSearchUiState.NoResults("qzxv", "qzxv", type, browseAll),
                    actions = TitleSearchActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { grid, _ -> opened = grid }, {}),
                )
            }
        }
    }

    @Test
    fun `the chips stay and the message names the query`() {
        show(SearchType.All)

        rule.onNodeWithText("No results for “qzxv”").assertIsDisplayed()
        rule.onNodeWithText("Try another query.").assertExists()
        rule.onNodeWithText("Movies").assertIsDisplayed()
    }

    @Test
    fun `the browse row opens the popular movie grid on All`() {
        show(SearchType.All)

        rule.onNodeWithText("Browse all movies").performClick()

        assertEquals(HubGrid(TitleKind.Movie, GridPreset.Popular), opened)
    }

    @Test
    fun `the browse row opens the popular TV grid on the TV chip`() {
        show(SearchType.Series)

        rule.onNodeWithText("Browse all shows").performClick()

        assertEquals(HubGrid(TitleKind.Series, GridPreset.Popular), opened)
    }

    @Test
    fun `the browse row is absent when the server cannot filter the grid`() {
        show(SearchType.All, browseAll = false)

        rule.onNodeWithText("Browse all movies").assertDoesNotExist()
        rule.onNodeWithText("Try another query.").assertExists()
    }
}
