package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.core.grid.PagedListState
import com.cooper.binge.companion.seerr.data.TitleKey
import com.cooper.binge.companion.seerr.data.TitleStatuses
import com.cooper.binge.companion.seerr.data.TitleType
import com.cooper.binge.companion.seerr.discover.collection.CollectionDetails
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleStatusOverlayTest {
    private fun result(
        kind: TitleKind = TitleKind.Movie,
        tmdbId: Int = 1,
        availability: TitleAvailability = TitleAvailability.None,
    ) = TitleResult(kind, tmdbId, "T", null, null, null, null, null, availability, null)

    private fun card(
        kind: TitleKind = TitleKind.Movie,
        tmdbId: Int = 1,
    ) = HubCard(kind, tmdbId, "T", null, null, TitleAvailability.None, "https://s/")

    private val pendingFilm = mapOf(TitleKey(TitleType.Movie, 1) to TitleStatuses(SeerrMediaStatusCode.Pending))

    @Test
    fun `a held status replaces the one a card carries`() {
        assertEquals(TitleAvailability.Pending, result().overlaid(pendingFilm).availability)
        assertEquals(TitleAvailability.Pending, card().overlaid(pendingFilm).availability)
    }

    @Test
    fun `a held status that clears the title shows it as not requested`() {
        val cleared = mapOf(TitleKey(TitleType.Movie, 1) to TitleStatuses())

        assertEquals(TitleAvailability.None, result(availability = TitleAvailability.Blocklisted).overlaid(cleared).availability)
    }

    @Test
    fun `a show and a film with one id are told apart, and a person is left alone`() {
        assertEquals(TitleAvailability.None, result(kind = TitleKind.Series).overlaid(pendingFilm).availability)
        assertEquals(TitleAvailability.None, result(kind = TitleKind.Person).overlaid(pendingFilm).availability)
    }

    @Test
    fun `a title the store has not seen keeps what its card says`() {
        val card = result(tmdbId = 2, availability = TitleAvailability.Available)

        assertSame(card, card.overlaid(pendingFilm))
    }

    @Test
    fun `a list nothing changed in is the same list`() {
        val items = listOf(card(tmdbId = 7))

        assertSame(items, items.overlaidBy(pendingFilm) { it.overlaid(pendingFilm) })
        val ready = PagedListState.Ready(items, hasMore = false)
        assertSame(ready, ready.overlaid(pendingFilm))
    }

    @Test
    fun `a collection's films follow the store, so the page's counts do`() {
        val details = CollectionDetails(1, "C", null, null, null, parts = listOf(result(), result(tmdbId = 2)))

        val overlaid = details.overlaid(pendingFilm)

        assertEquals(1, overlaid.toRequest)
        assertTrue(details.toRequest == 2)
    }

    private val offTheList = mapOf(TitleKey(TitleType.Movie, 1) to TitleStatuses(onWatchlist = false))

    @Test
    fun `a watchlist loses a title taken off it, and a title nothing is known of stays`() {
        val cards = listOf(card(tmdbId = 1), card(tmdbId = 2))

        assertEquals(listOf(2), cards.withoutOffWatchlist(offTheList).map { it.tmdbId })
        assertSame(cards, cards.withoutOffWatchlist(emptyMap()))
    }

    @Test
    fun `a title on the watchlist, or one the store has no word on, is not hidden`() {
        val onIt = mapOf(TitleKey(TitleType.Movie, 1) to TitleStatuses(onWatchlist = true))
        val unknown = mapOf(TitleKey(TitleType.Movie, 1) to TitleStatuses(onWatchlist = null))
        val cards = listOf(card(tmdbId = 1))

        assertSame(cards, cards.withoutOffWatchlist(onIt))
        assertSame(cards, cards.withoutOffWatchlist(unknown))
    }

    @Test
    fun `a film and a show with one id are told apart on the watchlist`() {
        val show = listOf(card(kind = TitleKind.Series, tmdbId = 1))

        assertSame(show, show.withoutOffWatchlist(offTheList))
    }
}
