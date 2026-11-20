package com.cooper.binge.companion.seerr.data

import com.cooper.binge.companion.seerr.seerr.SeerrMediaInfoDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleStatusStoreTest {
    private val store = TitleStatusStore()
    private val film = TitleKey(TitleType.Movie, 603)

    private fun statuses(
        standard: SeerrMediaStatusCode? = null,
        fourK: SeerrMediaStatusCode? = null,
    ) = TitleStatuses(standard, fourK)

    @Test
    fun `a read seeds a title nothing has written`() {
        store.seed(film, statuses(SeerrMediaStatusCode.Available), store.beginRead())

        assertEquals(SeerrMediaStatusCode.Available, store.current(film)?.standard)
    }

    @Test
    fun `a read that began before an action cannot undo it`() {
        val read = store.beginRead()
        store.confirm(film, statuses(SeerrMediaStatusCode.Pending))

        store.seed(film, statuses(null), read)

        assertEquals(SeerrMediaStatusCode.Pending, store.current(film)?.standard)
    }

    @Test
    fun `a read that began after an action replaces it`() {
        store.confirm(film, statuses(SeerrMediaStatusCode.Pending))

        store.seed(film, statuses(SeerrMediaStatusCode.Processing), store.beginRead())

        assertEquals(SeerrMediaStatusCode.Processing, store.current(film)?.standard)
    }

    @Test
    fun `a newer read wins over an older one that lands later`() {
        val older = store.beginRead()
        val newer = store.beginRead()
        store.seed(film, statuses(SeerrMediaStatusCode.Available), newer)

        store.seed(film, statuses(SeerrMediaStatusCode.Pending), older)

        assertEquals(SeerrMediaStatusCode.Available, store.current(film)?.standard)
    }

    @Test
    fun `one read may seed a title twice`() {
        val read = store.beginRead()
        store.seed(film, statuses(SeerrMediaStatusCode.Pending), read)
        store.seed(film, statuses(SeerrMediaStatusCode.Pending, SeerrMediaStatusCode.Processing), read)

        assertEquals(SeerrMediaStatusCode.Processing, store.current(film)?.fourK)
    }

    @Test
    fun `the 4K status moves apart from the standard one`() {
        store.confirm(film, statuses(SeerrMediaStatusCode.Available))

        store.applyOptimistic(film) { it.with(is4k = true, SeerrMediaStatusCode.Pending) }

        assertEquals(SeerrMediaStatusCode.Available, store.current(film)?.standard)
        assertEquals(SeerrMediaStatusCode.Pending, store.current(film)?.fourK)
    }

    @Test
    fun `a failed action restores the previous status`() {
        store.confirm(film, statuses(SeerrMediaStatusCode.Available))
        val undo = store.applyOptimistic(film) { it.with(false, SeerrMediaStatusCode.Blocklisted) }

        store.rollback(undo)

        assertEquals(SeerrMediaStatusCode.Available, store.current(film)?.standard)
    }

    @Test
    fun `rolling back a title nothing knew forgets it`() {
        val undo = store.applyOptimistic(film) { it.with(false, SeerrMediaStatusCode.Pending) }

        store.rollback(undo)

        assertNull(store.current(film))
    }

    @Test
    fun `a rollback does not undo a newer write`() {
        val undo = store.applyOptimistic(film) { it.with(false, SeerrMediaStatusCode.Pending) }
        store.confirm(film, statuses(SeerrMediaStatusCode.Processing))

        store.rollback(undo)

        assertEquals(SeerrMediaStatusCode.Processing, store.current(film)?.standard)
    }

    @Test
    fun `a movie and a show with one TMDB id are two titles`() {
        store.confirm(film, statuses(SeerrMediaStatusCode.Available))

        assertNull(store.current(TitleKey(TitleType.Tv, 603)))
    }

    @Test
    fun `clear forgets everything`() {
        store.confirm(film, statuses(SeerrMediaStatusCode.Available))

        store.clear()

        assertEquals(emptyMap<TitleKey, TitleStatuses>(), runBlocking { store.statuses.first() })
    }

    @Test
    fun `media info reads into statuses and an unknown title has none`() {
        val info = SeerrMediaInfoDto(id = 7, status = SeerrMediaStatusCode.Available, status4k = SeerrMediaStatusCode.Pending)

        assertEquals(TitleStatuses(SeerrMediaStatusCode.Available, SeerrMediaStatusCode.Pending, 7), info.toTitleStatuses())
        assertEquals(TitleStatuses(), (null as SeerrMediaInfoDto?).toTitleStatuses())
    }

    @Test
    fun `a read that says nothing of the watchlist leaves what an earlier one said`() {
        store.seed(film, TitleStatuses(onWatchlist = true), store.beginRead())

        store.seed(film, statuses(SeerrMediaStatusCode.Available), store.beginRead())

        assertEquals(true, store.current(film)?.onWatchlist)
        assertEquals(SeerrMediaStatusCode.Available, store.current(film)?.standard)
    }

    @Test
    fun `a later read that says the title is off the watchlist replaces what was held`() {
        store.seed(film, TitleStatuses(onWatchlist = true), store.beginRead())

        store.seed(film, TitleStatuses(onWatchlist = false), store.beginRead())

        assertEquals(false, store.current(film)?.onWatchlist)
    }

    @Test
    fun `a watchlist change is put back when its action fails`() {
        store.seed(film, TitleStatuses(onWatchlist = true), store.beginRead())

        val undo = store.applyOptimistic(film) { it.copy(onWatchlist = false) }
        store.rollback(undo)

        assertEquals(true, store.current(film)?.onWatchlist)
    }
}
