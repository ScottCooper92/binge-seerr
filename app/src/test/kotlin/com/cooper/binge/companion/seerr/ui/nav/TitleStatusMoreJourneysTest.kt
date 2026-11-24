package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
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
import com.cooper.binge.companion.seerr.core.ui.R as UiR

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
    private val declined get() = host.string(UiR.string.request_state_declined)
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
        val blockTitle = host.string(R.string.title_block_confirm_title, FILM)
        // The page's Block is an icon button the screen reader announces by its description; its label is not a text node.
        journey.clickUntil("the block confirm", block, matcher = hasContentDescription(block)) { host.isShowing(blockTitle) }
        // The page's button and the dialog's confirm share a label, so the dialog's is the last.
        journey.clickUntil("$FILM blocked and the dialog closed", block, last = true) { !host.isShowing(blockTitle) }

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
        val removeTitle = host.string(R.string.request_remove_confirm_title)
        // The page's status opens the title's sheet, which lists the request as a card showing the request's own status, and the card opens
        // the request's level where Remove is. The card's status is the last of its kind on screen.
        val approved = host.string(UiR.string.request_state_approved)
        val pending = host.string(UiR.string.request_state_pending)
        val cardStatus = hasText(approved) or hasText(pending)
        journey.clickUntil("the title's sheet", PROCESSING) {
            host.isShowing(approved, exactly = true) ||
                host.isShowing(pending, exactly = true)
        }
        journey.clickUntil("the request's level", approved, last = true, matcher = cardStatus) { host.isShowing(remove, exactly = true) }
        journey.clickUntil("the remove confirm", remove) { host.isShowing(removeTitle) }
        // The sheet's row and the dialog's confirm share a label, so the dialog's is the last.
        journey.clickUntil("the request removed and the dialog closed", remove, last = true) { !host.isShowing(removeTitle) }

        host.back()

        host.await("the card for $FILM shows no status again") { !journey.cardReads("") }
        assertEquals("the request is gone from the server", emptyList<Int?>(), journey.serverRequests())
    }

    @Test
    fun `a request the server refuses leaves the grid's card unchanged after Back`() {
        launchGrid()
        // The film still has the request the demo seeded, so the demo refuses a second one and the store takes its guess back.
        journey.openPage(shows = declined)
        journey.clickUntil("the request sheet", declined) { host.isShowing(journey.submit, exactly = true) }
        journey.clickUntil("the sheet closed on the refused request", journey.submit, last = true) {
            !host.isShowing(journey.submit, exactly = true)
        }
        host.await("the page says it was already requested") { host.isShowing(host.string(R.string.request_sheet_done_already)) }

        host.back()

        host.await("the card for $FILM still shows no status") { !journey.cardReads("") }
        assertEquals("only the seeded request is on the server", listOf(DECLINED), journey.serverRequests())
    }
}
