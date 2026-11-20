package com.cooper.binge.companion.seerr.ui.grid

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.binge.designsystem.component.LocalTabReselectSignal
import com.cooper.binge.companion.seerr.discover.TitleAvailability
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val CARDS = 60
private const val WAIT_MILLIS = 5_000L

/**
 * What tapping the Discover tab again does to its grid (#2450). The grid draws a plain list of cards, so it needs no paging harness:
 * the tab's own key scrolls it to the top and another tab's leaves it where it is.
 */
@RunWith(RobolectricTestRunner::class)
class DiscoverGridReselectTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val signal = MutableSharedFlow<Any>(extraBufferCapacity = 1)

    private fun showScrolledToEnd(reselectKey: Any?) {
        rule.setContent {
            CompositionLocalProvider(LocalTabReselectSignal provides signal) {
                SeerrTheme {
                    DiscoverGridScreen(
                        kind = TitleKind.Movie,
                        title = null,
                        state =
                            DiscoverGridUiState.Ready(
                                controls = GridControls(sort = null, chip = null),
                                cards = (0 until CARDS).map(::card),
                                hasMore = false,
                                appending = false,
                                appendFailed = false,
                            ),
                        actions = DiscoverGridActions({}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _, _ -> }, FilterLoads({}, {}, {})),
                        reselectKey = reselectKey,
                    )
                }
            }
        }
        rule.onNodeWithText("Title 0").assertIsDisplayed()
        rule.onNode(hasScrollAction()).performScrollToIndex(CARDS - 1)
        rule.waitForIdle()
        rule.onNodeWithText("Title 0").assertDoesNotExist()
    }

    private fun reselect(tab: SeerrTab) {
        runBlocking { signal.emit(tab) }
        rule.waitForIdle()
    }

    @Test
    fun `reselecting the Discover tab scrolls its grid back to the top`() {
        showScrolledToEnd(SeerrTab.Discover)

        reselect(SeerrTab.Discover)
        rule.waitUntil(WAIT_MILLIS) { rule.onAllNodesWithText("Title 0").fetchSemanticsNodes().isNotEmpty() }

        rule.onNodeWithText("Title 0").assertIsDisplayed()
    }

    @Test
    fun `another tab's reselect leaves the grid where it is`() {
        showScrolledToEnd(SeerrTab.Discover)

        reselect(SeerrTab.Movies)

        rule.onNodeWithText("Title 0").assertDoesNotExist()
    }

    @Test
    fun `a grid opened from a row has no tab to listen for`() {
        showScrolledToEnd(null)

        reselect(SeerrTab.Discover)

        rule.onNodeWithText("Title 0").assertDoesNotExist()
    }

    private fun card(index: Int) =
        HubCard(TitleKind.Movie, index, "Title $index", "2020", null, TitleAvailability.None, "https://server.example/m/$index")
}
