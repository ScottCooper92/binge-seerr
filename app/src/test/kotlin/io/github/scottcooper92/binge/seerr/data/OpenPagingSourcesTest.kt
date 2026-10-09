package io.github.scottcooper92.binge.seerr.data

import androidx.paging.PagingSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.concurrent.thread

private const val LIST = "all:added:all"

/**
 * A store's writes reach the paging sources it handed out, whenever they commit (#900). The cache is
 * the real one, in memory, because the race is in Room's own paging source.
 */
@RunWith(RobolectricTestRunner::class)
class OpenPagingSourcesTest {
    @get:Rule
    val cache = CacheDatabaseRule()

    /**
     * The cold-open race, held open. The first load starts and suspends on Room's thread. This test
     * then holds the event loop, so the load cannot get back and tell Room it finished. Meanwhile the
     * refresh commits, the way a mediator's first write does. Room's tracker drops that write, because
     * the source has not finished its first load. Without the store's own invalidation, the source
     * keeps its empty first read for good.
     */
    @Test
    fun `a write that commits while the first load is returning still invalidates the source`() =
        runBlocking {
            val store = RoomRequestStore(cache.db)
            val source = store.pagingSource(LIST)
            val load = async(start = CoroutineStart.UNDISPATCHED) { source.load(PagingSource.LoadParams.Refresh(null, 20, false)) }

            thread { runBlocking { store.refresh(LIST, listOf(row(1), row(2)), nextSkip = null) } }.join()

            assertTrue("the write did not invalidate the source", source.invalid)
            load.await()
            Unit
        }

    @Test
    fun `a write that fails invalidates nothing`() =
        runBlocking {
            val sources = OpenPagingSources<Int, RequestEntity>()
            val source = sources.track(RoomRequestStore(cache.db).pagingSource(LIST))

            runCatching { sources.afterWrite { error("the write failed") } }

            assertFalse(source.invalid)
        }

    private fun row(id: Int) =
        RequestEntity(
            listKey = LIST,
            id = id,
            tmdbId = id,
            mediaType = "MOVIE",
            title = null,
            posterUrl = null,
            year = null,
            requestedBy = null,
            requestedById = null,
            requestedAtMillis = null,
            status = 1,
            mediaStatus = null,
            downloadFraction = null,
            downloadEtaMinutes = null,
            downloading = false,
            seasonNumbers = "",
            is4k = false,
            orderIndex = id,
        )
}
