package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.HomeRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PHONE = "w400dp-h800dp"

/** The request status the demo gives a declined request, as the server's code. */
private const val DECLINED = 3

/**
 * The rest of the title-status journeys (#2354), beside [TitleStatusJourneyTest]'s hub and grid: Search, a block, a removed
 * request and a request the server refuses. Each goes through the real nav host on the demo server, acts on the film's own page,
 * goes Back, and looks at what the list underneath shows with no refresh in between.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class TitleStatusMoreJourneysTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @After
    fun flagOff() {
        NavHostActivity.discoveryEnabled = false
    }

    private val journey get() = TitleStatusJourney(host)
    private val declined get() = host.string(R.string.request_state_declined)
    private val block get() = host.string(R.string.title_page_action_block)
    private val remove get() = host.string(R.string.request_remove)

    private fun gridRoute() = DiscoverGridRoute(TitleKind.Movie, GridPreset.Popular, host.string(R.string.hubs_row_popular_movies))

    /** The grid the film sits on, the card without a status yet: its seeded request was declined, which shows no chip. */
    private fun launchGrid() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, gridRoute())
        host.awaitShowing(FILM)
        host.await("$FILM on the grid without a status") { !journey.cardReads("") }
    }

    /** Clicks the lowest node reading [label] until [done]: the sheet's or the dialog's button, not the page's behind it. */
    private fun clickLowestUntil(
        what: String,
        label: String,
        done: () -> Boolean,
    ) = host.await(what) {
        val finished = done()
        if (!finished) journey.lowest(label)?.performClick()
        finished
    }

    @Test
    fun `a film requested from its page shows its new status on Search's row after Back`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        journey.clearFilmRequests()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
        host.compose.onNodeWithTag("tab_search").performClick()
        host.awaitShowing(host.string(R.string.title_search_placeholder))
        // A query shorter than the name, so the field's own text is not taken for the row.
        host.compose.onNode(hasSetTextAction()).performTextInput("Tears")
        host.await("$FILM in the results") { host.isShowing(FILM) }
        assertFalse("the row shows no status chip yet", host.isShowing(PROCESSING, exactly = true))

        journey.requestFromTheCardAndGoBack()

        assertTrue("the request for $FILM reached the server", journey.serverRequests().isNotEmpty())
        host.await("the Search row for $FILM shows $PROCESSING") { host.isShowing(PROCESSING, exactly = true) }
    }

    @Test
    fun `a film blocked from its page shows blocklisted on the grid's card after Back`() {
        launchGrid()
        journey.openPage(shows = declined)
        host.clickUntil(
            "the block confirm",
            target = { host.compose.onAllNodes(hasText(block)).onFirst() },
            reached = { host.isShowing(host.string(R.string.title_block_confirm_title, FILM)) },
        )
        // The page's button and the dialog's confirm share a label, so the dialog's is the lower one.
        clickLowestUntil("$FILM blocked and the dialog closed", block) {
            !host.isShowing(host.string(R.string.title_block_confirm_title, FILM))
        }

        host.back()

        journey.awaitCard("grid", status = host.string(R.string.hubs_card_blocklisted))
        assertEquals("blocking takes the film's requests with it", emptyList<Int?>(), journey.serverRequests())
    }

    @Test
    fun `a request removed from its page clears the status on the grid's card after Back`() {
        launchGrid()
        journey.clearFilmRequests()
        journey.requestFromTheCardAndGoBack()
        journey.awaitCard("grid")
        journey.openPage(shows = PROCESSING)
        host.clickUntil(
            "the manage sheet",
            target = { host.compose.onAllNodes(hasText(PROCESSING)).onFirst() },
            reached = { host.isShowing(remove, exactly = true) },
        )
        host.clickUntil(
            "the remove confirm",
            target = { host.compose.onAllNodes(hasText(remove)).onFirst() },
            reached = { host.isShowing(host.string(R.string.request_remove_confirm_title)) },
        )
        // The sheet's row and the dialog's confirm share a label, so the dialog's is the lower one.
        clickLowestUntil("the request removed and the dialog closed", remove) {
            !host.isShowing(host.string(R.string.request_remove_confirm_title))
        }

        host.back()

        host.await("the card for $FILM shows no status again") { !journey.cardReads("") }
        assertEquals("the request is gone from the server", emptyList<Int?>(), journey.serverRequests())
    }

    @Test
    fun `a request the server refuses leaves the grid's card unchanged after Back`() {
        launchGrid()
        // The film still has the request the demo seeded, so the demo refuses a second one and the store takes its guess back.
        journey.openPage(shows = declined)
        host.clickUntil(
            "the request sheet",
            target = { host.compose.onAllNodes(hasText(declined)).onFirst() },
            reached = { host.isShowing(journey.submit, exactly = true) },
        )
        clickLowestUntil("the sheet closed on the refused request", journey.submit) { !host.isShowing(journey.submit, exactly = true) }
        host.await("the page says it was already requested") { host.isShowing(host.string(R.string.request_sheet_done_already)) }

        host.back()

        host.await("the card for $FILM still shows no status") { !journey.cardReads("") }
        assertEquals("only the seeded request is on the server", listOf(DECLINED), journey.serverRequests())
    }
}
