package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.data.TitleKey
import com.cooper.binge.companion.seerr.data.TitleType

private const val REQUESTS_TAKEN = 100
internal const val FILM_ID = 133701

/** A demo film, third in the demo's catalog so it is near the start of every list. The demo seeds a declined request for it. */
internal const val FILM = "Tears of Steel"

/** The demo user is an administrator, so a request is approved at once and the film is Processing, not Requested. */
internal const val PROCESSING = "Processing"

/**
 * What the title-status journeys (#2354) share: the film the demo seeds a declined request for, the card that shows its status,
 * and the steps that request it from its page and come back. A card says its status as its state description, not as text.
 */
internal class TitleStatusJourney(
    private val host: RealNavHostRule,
) {
    private val request get() = host.string(R.string.title_page_request)

    /** The request sheet's pinned button, which reads what the page's own button reads when the film can be requested. */
    val submit get() = host.string(R.string.request_sheet_submit)

    /** The first node reading [FILM], a card on a hub or the grid, or a row on Search once the query is shorter than the name. */
    fun card(): SemanticsNodeInteraction = host.compose.onAllNodes(hasText(FILM, substring = true)).onFirst()

    private fun stateReads(status: String) =
        SemanticsMatcher("state reads $status") { node ->
            node.config.getOrNull(SemanticsProperties.StateDescription)?.contains(status, ignoreCase = true) == true
        }

    /** The card for [FILM] reads [status] as its state now. An empty [status] asks for any state at all. */
    fun cardReads(status: String): Boolean =
        host.compose
            .onAllNodes(hasText(FILM, substring = true) and stateReads(status))
            .fetchSemanticsNodes()
            .isNotEmpty()

    /** Takes away the request the demo seeds for the film, which the demo would otherwise refuse a second request on. */
    fun clearFilmRequests() =
        host.api { api ->
            api
                .requests(take = REQUESTS_TAKEN)
                .results
                .filter { it.media.tmdbId == FILM_ID }
                .forEach { api.deleteRequest(it.id) }
        }

    /** The film's requests on the server, as status codes, so a card that does not show one is not for want of the request. */
    fun serverRequests(): List<Int?> =
        host
            .api { it.requests(take = REQUESTS_TAKEN) }
            .results
            .filter { it.media.tmdbId == FILM_ID }
            .map { it.status?.raw }

    /** Waits for the card to read [status], and says what the server and the store held when it did not. */
    fun awaitCard(
        where: String,
        status: String = PROCESSING,
    ) {
        try {
            host.await("the $where's card for $FILM reads $status") { cardReads(status) }
        } catch (timeout: ComposeTimeoutException) {
            val held = host.titleStatus(TitleKey(TitleType.Movie, FILM_ID))
            val saw = "The server's requests for $FILM: ${serverRequests()}\nThe store held: $held"
            throw AssertionError("${timeout.message}\n$saw", timeout)
        }
    }

    /**
     * Clicks a node reading exactly [label], or matching [matcher], on each poll until [reached], and says what the screen showed if none ever did. [last]
     * takes the last match in the tree, which is a dialog's own button over the sheet's or page's behind it, as a dialog's window
     * is composed after what it covers. The poll lets pending recomposition land before it looks, and a node that is gone when
     * the click lands (the page recomposed between the look and the click) is waited out, not an error.
     */
    fun clickUntil(
        what: String,
        label: String,
        last: Boolean = false,
        matcher: SemanticsMatcher = hasText(label),
        reached: () -> Boolean,
    ) = host.await(what) {
        val done = reached()
        if (!done) {
            host.compose.waitForIdle()
            val nodes = host.compose.onAllNodes(matcher)
            val count = nodes.fetchSemanticsNodes().size
            if (count > 0) {
                try {
                    nodes[if (last) count - 1 else 0].performClick()
                } catch (_: AssertionError) {
                    // The node was replaced between the look and the click; the next poll looks again.
                }
            }
        }
        done
    }

    /**
     * Opens [FILM]'s page from [open], which finds its card or row, and waits for the page to show [shows]. A card that is off screen
     * or gone for a frame while the list settles is waited out, as a click that lands on a node that has gone is.
     */
    fun openPage(
        open: () -> SemanticsNodeInteraction = { card().also { it.performScrollTo() } },
        shows: String,
    ) = host.await("the page of $FILM") {
        val done = host.isShowing(shows, exactly = true)
        if (!done) {
            try {
                open().performClick()
            } catch (_: AssertionError) {
                // The node was not there at the instant of the tap; the next poll looks again.
            }
        }
        done
    }

    /** Opens [FILM]'s page from its card, requests it from the sheet, and comes back with Back. */
    fun requestFromTheCardAndGoBack(open: () -> SemanticsNodeInteraction = { card().also { it.performScrollTo() } }) {
        openPage(open, shows = request)
        clickUntil("the request sheet", request) { host.isShowing(host.string(R.string.request_sheet_status_new)) }
        // The page's button can still read Request once the film is asked for, when its 4K version is open, so the sheet going is the sign.
        // The sheet's footer shares the page button's label, and a sheet is a window of its own, so its button is the last match.
        clickUntil("$FILM requested and the sheet closed", request, last = true) {
            !host.isShowing(host.string(R.string.request_sheet_status_new))
        }
        host.back()
    }
}
