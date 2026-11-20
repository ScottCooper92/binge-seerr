package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.data.TitleKey
import com.cooper.binge.companion.seerr.data.TitleType
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PHONE = "w400dp-h800dp"

private const val WAIT_MILLIS = 5_000L

private const val POPULAR_MOVIES = 5
private const val REQUESTS_TAKEN = 100
private const val FILM_ID = 133701

/** A demo film, third in the demo's catalog so it is near the start of every list. The demo seeds a declined request for it. */
private const val FILM = "Tears of Steel"

/**
 * A title's status follows it across screens (#2354), through the real nav host on the demo server: request a film from the
 * page a card opens, go Back, and the card now shows the status the page gave it, with no refresh in between.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class TitleStatusJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @After
    fun flagOff() {
        NavHostActivity.discoveryEnabled = false
    }

    private val request get() = host.string(R.string.title_page_request)

    /** The demo user is an administrator, so a request is approved at once and the film is Processing, not Requested. */
    private val processing = "Processing"

    private fun awaitStack(vararg stack: NavKey) = host.compose.waitUntil(WAIT_MILLIS) { host.stack() == stack.toList() }

    private fun card(): SemanticsNodeInteraction = host.compose.onAllNodes(hasText(FILM, substring = true)).onFirst()

    /** A card says its status as its state description, not as text, so that is what is read. */
    private fun stateReads(status: String) =
        SemanticsMatcher("state reads $status") { node ->
            node.config.getOrNull(SemanticsProperties.StateDescription)?.contains(status, ignoreCase = true) == true
        }

    /** The card for [FILM] reads [status] as its state now. */
    private fun cardReads(status: String): Boolean =
        host.compose
            .onAllNodes(hasText(FILM, substring = true) and stateReads(status))
            .fetchSemanticsNodes()
            .isNotEmpty()

    /** Takes away the request the demo seeds for the film, which the demo would otherwise refuse a second request on. */
    private fun clearFilmRequests() =
        host.api { api ->
            api
                .requests(take = REQUESTS_TAKEN)
                .results
                .filter { it.media.tmdbId == FILM_ID }
                .forEach { api.deleteRequest(it.id) }
        }

    /** The film's requests on the server, as status codes, so a card that does not show one is not for want of the request. */
    private fun serverRequests(): List<Int?> =
        host
            .api { it.requests(take = REQUESTS_TAKEN) }
            .results
            .filter { it.media.tmdbId == FILM_ID }
            .map { it.status?.raw }

    /** Waits for the card to read [processing], and says what the server and the store held when it did not. */
    private fun awaitCard(where: String) {
        try {
            host.await("the $where's card for $FILM reads $processing") { cardReads(processing) }
        } catch (timeout: ComposeTimeoutException) {
            val held = host.titleStatus(TitleKey(TitleType.Movie, FILM_ID))
            val saw = "The server's requests for $FILM: ${serverRequests()}\nThe store held: $held"
            throw AssertionError("${timeout.message}\n$saw", timeout)
        }
    }

    /**
     * The "Request" that is lowest on screen. The page's own button and the sheet's share a label, and a click that reaches the
     * page's button through the sheet's scrim only dismisses the sheet, so the one pinned in the sheet's footer is the one wanted.
     */
    private fun lowestRequest(): SemanticsNodeInteraction? {
        val nodes = host.compose.onAllNodes(hasText(request))
        val lowest =
            nodes
                .fetchSemanticsNodes()
                .withIndex()
                .maxByOrNull { it.value.boundsInRoot.top }
                ?.index
        return lowest?.let { nodes[it] }
    }

    /** Opens [FILM]'s page from its card, requests it from the sheet, and comes back with Back. */
    private fun requestFromTheCardAndGoBack() {
        host.clickUntil("the page of $FILM", target = { card().performScrollTo() }, reached = { host.isShowing(request, exactly = true) })
        host.clickUntil(
            "the request sheet",
            target = { host.compose.onAllNodes(hasText(request)).onFirst() },
            reached = { host.isShowing(host.string(R.string.request_sheet_status_new)) },
        )
        // The page's button can still read Request once the film is asked for, when its 4K version is open, so the sheet going is the sign.
        host.await("$FILM requested and the sheet closed") {
            val closed = !host.isShowing(host.string(R.string.request_sheet_status_new))
            if (!closed) lowestRequest()?.performClick()
            closed
        }
        host.back()
    }

    @Test
    fun `a film requested from its page shows its new status on the Movies hub's card after Back`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        clearFilmRequests()
        host.api { it.updateDiscoverSliders(popularRow()) }
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
        scrollHubTo(FILM)
        host.await("$FILM on the hub without a status") { host.isShowing(FILM) && !cardReads("") }

        requestFromTheCardAndGoBack()

        awaitStack(ShellRoute)
        assertTrue("the request for $FILM reached the server", serverRequests().isNotEmpty())
        awaitCard("hub")
    }

    @Test
    fun `a film requested from its page shows its new status on the Discover grid's card after Back`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        clearFilmRequests()
        val grid = DiscoverGridRoute(TitleKind.Movie, GridPreset.Popular, host.string(R.string.hubs_row_popular_movies))
        host.launch(HomeRoute, grid)
        host.awaitShowing(FILM)
        host.await("$FILM on the grid without a status") { !cardReads("") }

        requestFromTheCardAndGoBack()

        awaitStack(ShellRoute, grid)
        assertTrue("the request for $FILM reached the server", serverRequests().isNotEmpty())
        awaitCard("grid")
    }

    /** The demo seeds no Popular row, so the server is given the built-in one. */
    private fun popularRow() =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("id", 1)
                    put("type", POPULAR_MOVIES)
                    put("isBuiltIn", true)
                    put("enabled", true)
                },
            )
        }

    /** Rows load one by one and the list composes only what is near, so this scrolls again until the row it wants has arrived. */
    private fun scrollHubTo(text: String) {
        host.compose.waitUntil(WAIT_MILLIS) {
            try {
                host.compose
                    .onAllNodes(hasScrollAction())
                    .onFirst()
                    .performScrollToNode(hasText(text, substring = true))
                true
            } catch (_: AssertionError) {
                false
            }
        }
    }
}
