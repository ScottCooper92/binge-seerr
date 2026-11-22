package com.cooper.binge.companion.seerr.discover

import com.cooper.binge.companion.seerr.data.TitleKey
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.data.TitleStatuses
import com.cooper.binge.companion.seerr.data.TitleType
import com.cooper.binge.companion.seerr.discover.sliders.HubCard
import com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a list's rows write into the status store, and what a card then shows (#2590). */
class SeedRowsTest {
    private val store = TitleStatusStore()
    private val film = TitleKey(TitleType.Movie, 1)

    private fun card() = HubCard(TitleKind.Movie, 1, "T", null, null, TitleAvailability.Available, "https://s/")

    private fun held() = store.current(film)

    @Test
    fun `a row with no media info does not blank a title the store knows`() {
        store.seed(film, TitleStatuses(SeerrMediaStatusCode.Available), store.beginRead())

        store.seedRows(store.beginRead(), listOf(Triple(TitleKind.Movie, 1, null)))

        assertEquals(SeerrMediaStatusCode.Available, held()?.standard)
    }

    @Test
    fun `a card keeps its own status when another list had no media info for the title`() {
        store.seedRows(store.beginRead(), listOf(Triple(TitleKind.Movie, 1, null)))

        assertNull("nothing is recorded for a title no list knew anything of", held())
        assertEquals(TitleAvailability.Available, card().overlaid(held()?.let { mapOf(film to it) }.orEmpty()).availability)
    }

    @Test
    fun `a row with media info still replaces what is held, so a removal shows`() {
        store.seed(film, TitleStatuses(SeerrMediaStatusCode.Available), store.beginRead())

        store.seedRows(store.beginRead(), listOf(Triple(TitleKind.Movie, 1, SeerrMediaInfoDto(status = SeerrMediaStatusCode.Unknown))))

        assertEquals(TitleAvailability.None, cardAvailability(held()?.standard, held()?.fourK))
    }

    @Test
    fun `a watchlist row with no media info marks a title the store holds and leaves its status`() {
        store.seed(film, TitleStatuses(SeerrMediaStatusCode.Available), store.beginRead())

        store.seedRows(store.beginRead(), listOf(Triple(TitleKind.Movie, 1, null)), onWatchlist = true)

        assertEquals(TitleStatuses(SeerrMediaStatusCode.Available, onWatchlist = true), held())
    }

    @Test
    fun `a row's 4K status reaches the store and the card shows it`() {
        val info = SeerrMediaInfoDto(id = 9, status = SeerrMediaStatusCode.Unknown, status4k = SeerrMediaStatusCode.Available)

        store.seedRows(store.beginRead(), listOf(Triple(TitleKind.Movie, 1, info)))

        assertEquals(TitleAvailability.Available, cardAvailability(held()?.standard, held()?.fourK))
        val bare = card().copy(availability = TitleAvailability.None)
        assertEquals(TitleAvailability.Available, bare.overlaid(held()?.let { mapOf(film to it) }.orEmpty()).availability)
    }

    @Test
    fun `the media and request lists seed both versions of each title`() {
        val rows =
            listOf(
                SeerrRequestMediaDto(id = 5, tmdbId = 1, mediaType = "movie", status = SeerrMediaStatusCode.Available),
                SeerrRequestMediaDto(id = 6, tmdbId = 1, mediaType = "tv", status4k = SeerrMediaStatusCode.Processing),
                SeerrRequestMediaDto(id = 7, tmdbId = 2, mediaType = "person"),
            )

        store.seedMedia(store.beginRead(), rows)

        assertEquals(TitleStatuses(SeerrMediaStatusCode.Available, null, 5), held())
        assertEquals(TitleStatuses(null, SeerrMediaStatusCode.Processing, 6), store.current(TitleKey(TitleType.Tv, 1)))
        assertNull(store.current(TitleKey(TitleType.Movie, 2)))
    }
}
