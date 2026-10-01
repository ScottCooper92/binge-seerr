package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.util.FakeResponse
import io.github.scottcooper92.binge.seerr.util.FakeSeerrServer
import io.github.scottcooper92.binge.seerr.util.FakeTitleDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY = 24L * 60 * 60 * 1000

class TitleCacheTest {
    private val seerr = FakeSeerrServer()
    private val dao = FakeTitleDao()
    private var healthy = true
    private var now = 1_000_000_000_000L

    init {
        seerr.dispatcher = {
            if (healthy) {
                FakeResponse(body = """{"title":"Heat","posterPath":"/heat.jpg","releaseDate":"1995-12-15"}""")
            } else {
                FakeResponse(code = 500)
            }
        }
    }

    private fun api() =
        SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher)
            .cached(seerr.url(), SeerrAuth.ApiKey("k3y"))

    private fun cache() = TitleCache(dao) { now }

    @Test
    fun `a persisted title answers a cold start without the network`() =
        runTest {
            val first = cache().get(api(), SEERR_MEDIA_TYPE_MOVIE, 100)
            assertEquals("Heat", first?.title)
            assertEquals(1, seerr.requestCount)

            val restarted = cache().get(api(), SEERR_MEDIA_TYPE_MOVIE, 100)
            assertEquals(first, restarted)
            assertEquals(1, seerr.requestCount)
        }

    @Test
    fun `a failed lookup is not persisted and is retried`() =
        runTest {
            healthy = false
            val cache = cache()
            assertNull(cache.get(api(), SEERR_MEDIA_TYPE_MOVIE, 100))
            assertTrue(dao.rows.isEmpty())

            healthy = true
            assertEquals("Heat", cache.get(api(), SEERR_MEDIA_TYPE_MOVIE, 100)?.title)
        }

    @Test
    fun `a persisted title past its age limit is fetched again`() =
        runTest {
            cache().get(api(), SEERR_MEDIA_TYPE_MOVIE, 100)
            now += 31 * DAY
            cache().get(api(), SEERR_MEDIA_TYPE_MOVIE, 100)
            assertEquals(2, seerr.requestCount)
        }

    @Test
    fun `clear empties both the memory and the persisted layer`() =
        runTest {
            val cache = cache()
            cache.get(api(), SEERR_MEDIA_TYPE_MOVIE, 100)
            cache.clear()
            assertTrue(dao.rows.isEmpty())

            cache.get(api(), SEERR_MEDIA_TYPE_MOVIE, 100)
            assertEquals(2, seerr.requestCount)
        }
}
