package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.grid.DiscoverSort
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A tablet-width window: a smallest width of 600dp or more. */
private const val TABLET = "w900dp-h800dp"

/** What the bar says once the order is Highest rated. */
private const val RATED = "Movies · Highest rated"

/** The narrowest tablet window: few columns, so the demo's grid scrolls. */
private const val NARROW_TABLET = "w600dp-h600dp"

/** Titles on the demo's Discover grid, any of which is there once its cards have loaded. */
private val DEMO_CARDS = listOf("Big Buck Bunny", "Metropolis", "Sintel", "Nosferatu", "Spirited Away", "Tears of Steel", "The Matrix")

/** The last card the demo's Discover grid holds, to scroll to. */
private const val LAST_ROW = 7

/** A phone. */
private const val PHONE = "w400dp-h800dp"

/**
 * The Discover tab through the real nav host: it is in the bar on a tablet-width window only, it hosts the grid with no Back
 * arrow, and a window that stops being that wide leaves it for the start tab.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = TABLET)
class DiscoverTabJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @After
    fun flagOff() {
        NavHostActivity.discoveryEnabled = false
    }

    @Test
    fun `the Discover tab hosts the grid under the bar`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_row_recently_added))

        host.compose.onNodeWithTag("tab_discover").performClick()

        host.awaitShowing(host.string(R.string.grid_filters_button))
        host.compose.onNodeWithTag("tab_discover").assertIsDisplayed()
        assertEquals("the tab opens nothing on the app's stack", listOf<NavKey>(ShellRoute), host.stack())
    }

    @Test
    fun `a window that is no longer tablet width takes the tab out and lands on the start tab`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_row_recently_added))
        host.compose.onNodeWithTag("tab_discover").performClick()
        host.awaitShowing(host.string(R.string.grid_filters_button))

        host.rotate(PHONE)

        host.compose.onAllNodes(hasTestTag("tab_discover")).assertCountEquals(0)
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
    }

    @Test
    fun `the order chosen on the tab survives a fold to a phone and an unfold`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_row_recently_added))
        host.compose.onNodeWithTag("tab_discover").performClick()
        host.awaitShowing(host.string(R.string.grid_filters_button))
        host.clickUntilShowing(host.string(R.string.grid_filters_button), host.string(R.string.grid_sort_rating_desc))
        host.compose.onNodeWithText(host.string(R.string.grid_sort_rating_desc)).performClick()
        host.clickUntil(
            "the sheet closed",
            target = { host.compose.onNodeWithText(host.string(R.string.grid_filters_show_results)) },
            reached = { !host.isShowing(host.string(R.string.grid_filters_show_results)) },
        )
        host.awaitShowing(RATED)

        host.rotate(PHONE)
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
        host.rotate(TABLET)
        host.compose.onNodeWithTag("tab_discover").performClick()

        host.awaitShowing(RATED)
    }

    @Test
    fun `the tab's route carries the order chosen in the sheet`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_row_recently_added))
        host.compose.onNodeWithTag("tab_discover").performClick()
        host.awaitShowing(host.string(R.string.grid_filters_button))

        applyInSheet(host.string(R.string.grid_sort_rating_desc))

        host.await("the tab's route carries Highest rated") {
            (host.tabStack(SeerrTab.Discover).lastOrNull() as? DiscoverGridRoute)?.sort == DiscoverSort.RatingDesc
        }
    }

    @Test
    fun `switching to TV shows and back to movies starts the grid from what the sheet chose`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_row_recently_added))
        host.compose.onNodeWithTag("tab_discover").performClick()
        host.awaitShowing(host.string(R.string.grid_filters_button))

        applyInSheet(host.string(R.string.grid_content_tv))
        host.awaitShowing("TV · Most popular")

        applyInSheet(host.string(R.string.grid_content_movies), host.string(R.string.grid_sort_rating_desc))

        host.awaitShowing(RATED)
    }

    @Test
    fun `the grid's scroll position survives a fold to a phone and an unfold`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_row_recently_added))
        host.rotate(NARROW_TABLET)
        host.awaitShowing(host.string(R.string.hubs_row_recently_added))
        host.compose.onNodeWithTag("tab_discover").performClick()
        host.awaitShowing(host.string(R.string.grid_filters_button))
        awaitCards()
        val atTop = displayedCards()
        host.compose.onNode(hasScrollAction()).performScrollToIndex(LAST_ROW)
        host.compose.waitForIdle()
        val scrolled = displayedCards()
        assertNotEquals("the grid did not scroll, so the test proves nothing", atTop, scrolled)

        host.rotate(PHONE)
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
        host.rotate(NARROW_TABLET)
        host.awaitShowing(host.string(R.string.hubs_row_recently_added))
        host.compose.onNodeWithTag("tab_discover").performClick()
        host.awaitShowing(host.string(R.string.grid_filters_button))
        host.await("the same cards are on screen") { displayedCards() == scrolled }
    }

    /** Waits until the grid shows a card of the demo's, so a scroll is not asked of a skeleton or of cards still loading. */
    private fun awaitCards() = host.await("a demo card in the grid") { displayedCards().isNotEmpty() }

    /** The demo's cards that are on screen now, which says where the grid is scrolled to without a pixel in it. */
    private fun displayedCards(): Set<String> =
        DEMO_CARDS
            .filter { title ->
                val nodes = host.compose.onAllNodesWithText(title)
                nodes.fetchSemanticsNodes().isNotEmpty() && nodes[0].isDisplayed()
            }.toSet()

    private fun applyInSheet(vararg choices: String) {
        host.clickUntilShowing(host.string(R.string.grid_filters_button), host.string(R.string.grid_filters_show_results))
        choices.forEach { choice ->
            host.compose
                .onAllNodesWithText(choice)
                .onLast()
                .performClick()
        }
        host.clickUntil(
            "the sheet closed",
            target = { host.compose.onNodeWithText(host.string(R.string.grid_filters_show_results)) },
            reached = { !host.isShowing(host.string(R.string.grid_filters_show_results)) },
        )
    }
}
