package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.binge.designsystem.LocalNavOverlayInsets
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val CARDS = 60
private val OVERLAY = 80.dp

/** The grid fills the window and scrolls under the bar and the navigation bar, as Binge's gallery does, and still clears them at its ends. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class DiscoverGridInsetsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun show(overlay: PaddingValues = PaddingValues()) {
        val state =
            DiscoverGridUiState.Ready(
                GridControls(DiscoverSort.PopularityDesc, ActiveFilterChip(ChipKind.Genre, "Drama")),
                List(
                    CARDS,
                ) {
                    HubCard(
                        TitleKind.Movie,
                        it,
                        "Movie $it",
                        "2021",
                        null,
                        TitleAvailability.None,
                        "https://server.example/movie/$it",
                        7.5f,
                    )
                },
                hasMore = false,
                appending = false,
                appendFailed = false,
            )
        rule.setContent {
            SeerrTheme {
                CompositionLocalProvider(LocalNavOverlayInsets provides overlay) {
                    DiscoverGridScreen(
                        TitleKind.Movie,
                        "Drama",
                        state,
                        DiscoverGridActions({}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, FilterLoads({}, {}, {})),
                    )
                }
            }
        }
    }

    /** The one node that scrolls vertically: the filter chips above the grid scroll sideways. */
    private val verticalGrid =
        hasScrollToIndexAction() and
            SemanticsMatcher("scrolls vertically") { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }

    private fun gridBounds() = rule.onNode(verticalGrid).fetchSemanticsNode().boundsInRoot

    @Test
    fun `the grid's viewport runs from the top of the window to the bottom, under the bar`() {
        show()

        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val grid = gridBounds()

        assertEquals("under the bar, not below it", root.top, grid.top, 0.5f)
        assertEquals("down to the window's edge", root.bottom, grid.bottom, 0.5f)
    }

    @Test
    fun `the last row scrolls clear of what covers the bottom of the window`() {
        show(PaddingValues(bottom = OVERLAY))

        rule.onNode(verticalGrid).performScrollToIndex(CARDS - 1)
        rule.waitForIdle()

        val last = rule.onNodeWithText("Movie ${CARDS - 1}").getBoundsInRoot()
        val window = rule.onRoot().getBoundsInRoot()
        assertTrue("the last title ends above the overlay: ${last.bottom} of ${window.bottom}", last.bottom <= window.bottom - OVERLAY)
    }
}
