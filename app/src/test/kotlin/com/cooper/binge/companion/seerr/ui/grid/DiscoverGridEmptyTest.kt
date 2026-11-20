package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.core.grid.PagedListState
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val CONTROLS = GridControls(sort = null, chip = null)

/** An empty Discover grid says why and what to do, and never draws a blank page (#2454). */
@RunWith(RobolectricTestRunner::class)
class DiscoverGridEmptyTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var reset = 0

    private fun show(reason: GridEmptyReason) {
        rule.setContent {
            SeerrTheme {
                DiscoverGridScreen(
                    kind = TitleKind.Movie,
                    title = null,
                    state = DiscoverGridUiState.Empty(CONTROLS, reason),
                    actions =
                        DiscoverGridActions(
                            {},
                            {},
                            {},
                            {},
                            {},
                            {},
                            { _, _ -> },
                            { _, _, _ -> },
                            FilterLoads({}, {}, {}),
                            onResetFilters = { reset++ },
                        ),
                )
            }
        }
    }

    @Test
    fun `an empty watchlist says what fills it and offers nothing to clear`() {
        show(GridEmptyReason.Watchlist)

        rule.onNodeWithText("Nothing on your watchlist yet").assertIsDisplayed()
        rule.onNodeWithText("Titles you add to your watchlist show up here.").assertIsDisplayed()
        rule.onNodeWithText("Clear filters").assertDoesNotExist()
    }

    @Test
    fun `filters that match nothing offer to clear them`() {
        show(GridEmptyReason.Filtered)

        rule.onNodeWithText("Clear filters").performClick()

        assertEquals(1, reset)
    }

    @Test
    fun `a list that is empty by itself says so`() {
        show(GridEmptyReason.Nothing)

        rule.onNodeWithText("There is nothing in this list yet.").assertIsDisplayed()
        rule.onNodeWithText("Clear filters").assertDoesNotExist()
    }

    @Test
    fun `a watchlist whose last title was taken off is empty, not a blank grid`() {
        val ready = PagedListState.Ready(items = emptyList<HubCard>(), hasMore = false)

        assertEquals(DiscoverGridUiState.Empty(CONTROLS, GridEmptyReason.Watchlist), toUiState(ready, CONTROLS, GridEmptyReason.Watchlist))
    }

    @Test
    fun `an empty page with more to load keeps loading rather than saying there is nothing`() {
        val ready = PagedListState.Ready(items = emptyList<HubCard>(), hasMore = true)

        assertTrue(toUiState(ready, CONTROLS, GridEmptyReason.Watchlist) is DiscoverGridUiState.Ready)
    }
}
