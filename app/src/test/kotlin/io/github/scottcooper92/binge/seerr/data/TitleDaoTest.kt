package io.github.scottcooper92.binge.seerr.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The title cache's SQL, against the real database: the expiry filter, the trim and the replace. */
@RunWith(RobolectricTestRunner::class)
class TitleDaoTest {
    @get:Rule
    val database = CacheDatabaseRule()

    private val dao get() = database.db.titleDao()

    private fun row(
        mediaType: String,
        tmdbId: Int,
        fetchedAtMillis: Long,
        title: String? = "T$tmdbId",
    ) = TitleEntity(mediaType, tmdbId, title, posterUrl = null, year = null, fetchedAtMillis = fetchedAtMillis)

    private suspend fun keys(): Set<Pair<String, Int>> =
        listOf("movie", "tv")
            .flatMap { type -> (1..MAX_ID).mapNotNull { id -> dao.find(type, id, since = 0)?.let { type to id } } }
            .toSet()

    @Test
    fun `find reads a row fetched at or after since and treats an older one as absent`() =
        runBlocking {
            dao.upsert(row("movie", 1, fetchedAtMillis = 100))

            assertNotNull(dao.find("movie", 1, since = 100))
            assertNotNull(dao.find("movie", 1, since = 99))
            assertNull(dao.find("movie", 1, since = 101))
        }

    @Test
    fun `find keys on the media type as well as the id`() =
        runBlocking {
            dao.upsert(row("movie", 1, fetchedAtMillis = 100, title = "A movie"))

            assertNull(dao.find("tv", 1, since = 0))
            assertEquals("A movie", dao.find("movie", 1, since = 0)?.title)
        }

    @Test
    fun `upsert replaces the row with the same key`() =
        runBlocking {
            dao.upsert(row("movie", 1, fetchedAtMillis = 100, title = "Old"))
            dao.upsert(row("movie", 1, fetchedAtMillis = 200, title = "New"))

            assertEquals("New", dao.find("movie", 1, since = 0)?.title)
            assertEquals(setOf("movie" to 1), keys())
        }

    @Test
    fun `deleteOlderThan removes strictly older rows and keeps the boundary`() =
        runBlocking {
            dao.upsert(row("movie", 1, fetchedAtMillis = 99))
            dao.upsert(row("movie", 2, fetchedAtMillis = 100))
            dao.upsert(row("tv", 3, fetchedAtMillis = 101))

            dao.deleteOlderThan(before = 100)

            assertEquals(setOf("movie" to 2, "tv" to 3), keys())
        }

    @Test
    fun `trimTo keeps the most recently fetched rows`() =
        runBlocking {
            dao.upsert(row("movie", 1, fetchedAtMillis = 10))
            dao.upsert(row("movie", 2, fetchedAtMillis = 30))
            dao.upsert(row("tv", 3, fetchedAtMillis = 20))
            dao.upsert(row("tv", 4, fetchedAtMillis = 40))

            dao.trimTo(keep = 2)

            assertEquals(setOf("tv" to 4, "movie" to 2), keys())
        }

    @Test
    fun `trimTo treats the same id under two media types as two rows`() =
        runBlocking {
            dao.upsert(row("movie", 1, fetchedAtMillis = 10))
            dao.upsert(row("tv", 1, fetchedAtMillis = 20))

            dao.trimTo(keep = 1)

            assertEquals(setOf("tv" to 1), keys())
        }

    @Test
    fun `trimTo leaves a table already under the limit alone, and zero empties it`() =
        runBlocking {
            dao.upsert(row("movie", 1, fetchedAtMillis = 10))
            dao.upsert(row("tv", 2, fetchedAtMillis = 20))

            dao.trimTo(keep = 5)
            assertEquals(setOf("movie" to 1, "tv" to 2), keys())

            dao.trimTo(keep = 2)
            assertEquals(setOf("movie" to 1, "tv" to 2), keys())

            dao.trimTo(keep = 0)
            assertEquals(emptySet<Pair<String, Int>>(), keys())
        }

    @Test
    fun `clear empties the table`() =
        runBlocking {
            dao.upsert(row("movie", 1, fetchedAtMillis = 10))
            dao.upsert(row("tv", 2, fetchedAtMillis = 20))

            dao.clear()

            assertEquals(emptySet<Pair<String, Int>>(), keys())
        }

    private companion object {
        const val MAX_ID = 10
    }
}
