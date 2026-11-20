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

internal const val PHONE = "w400dp-h800dp"
internal const val WAIT_MILLIS = 5_000L
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
     * The node reading exactly [text] that is lowest on screen, or null when none shows. A page's button and a sheet's or dialog's
     * share a label, and a click that reaches the page's through a scrim only dismisses what is on top, so the one pinned lowest is wanted.
     */
    fun lowest(text: String): SemanticsNodeInteraction? {
        val nodes = host.compose.onAllNodes(hasText(text))
        val lowest =
            nodes
                .fetchSemanticsNodes()
                .withIndex()
                .maxByOrNull { it.value.boundsInRoot.top }
                ?.index
        return lowest?.let { nodes[it] }
    }

    /** Opens [FILM]'s page from [open], which finds its card or row. */
    fun openPage(
        open: () -> SemanticsNodeInteraction = { card().also { it.performScrollTo() } },
        shows: String,
    ) = host.clickUntil("the page of $FILM", target = open, reached = { host.isShowing(shows, exactly = true) })

    /** Opens [FILM]'s page from its card, requests it from the sheet, and comes back with Back. */
    fun requestFromTheCardAndGoBack(open: () -> SemanticsNodeInteraction = { card().also { it.performScrollTo() } }) {
        openPage(open, shows = request)
        host.clickUntil(
            "the request sheet",
            target = { host.compose.onAllNodes(hasText(request)).onFirst() },
            reached = { host.isShowing(host.string(R.string.request_sheet_status_new)) },
        )
        // The page's button can still read Request once the film is asked for, when its 4K version is open, so the sheet going is the sign.
        host.await("$FILM requested and the sheet closed") {
            val closed = !host.isShowing(host.string(R.string.request_sheet_status_new))
            if (!closed) lowest(request)?.performClick()
            closed
        }
        host.back()
    }
}
