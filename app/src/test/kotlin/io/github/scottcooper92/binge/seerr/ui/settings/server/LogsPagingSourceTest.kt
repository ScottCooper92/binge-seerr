package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.paging.PagingSource
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

private const val PAGE =
    """{"pageInfo":{"pages":3,"pageSize":25,"results":60,"page":1},
        "results":[{"label":"server","level":"info","message":"Server ready on port 5055","timestamp":"2026-09-13T05:00:00.000Z"},
                   {"label":"Radarr","level":"ERROR","message":"Request failed","timestamp":"2026-09-13T04:59:00.000Z","data":{"status":500,"path":"/api/v3/movie"}},
                   {"level":"warn","message":"No label","timestamp":"2026-09-13T04:58:00.000Z","data":{}}]}"""

private const val BARE_ARRAY = """[{"label":"server","level":"debug","message":"Bare","timestamp":"2026-09-13T05:00:00.000Z"}]"""

class LogsPagingSourceTest {
    private val seerr = MockWebServer()
    private val received = CopyOnWriteArrayList<RecordedRequest>()
    private var body = PAGE

    @Before
    fun setUp() {
        seerr.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    return MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)
                }
            }
        seerr.start()
    }

    @After
    fun tearDown() = seerr.close()

    private fun source(
        level: LogLevel = LogLevel.Info,
        search: String? = null,
    ): LogsPagingSource {
        val api = SeerrApiFactory(logRequests = false).cached(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y"))
        return LogsPagingSource(api = { api }, level = level, search = search)
    }

    private suspend fun LogsPagingSource.page(key: Int?): PagingSource.LoadResult.Page<Int, LogEntry> =
        load(
            PagingSource.LoadParams.Refresh(key = key, loadSize = LOGS_PAGE_SIZE, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

    @Test
    fun `a page is read with each line's level, label and data, and the next page follows`() =
        runTest {
            val page = source().page(null)
            assertEquals(listOf(LogLevel.Info, LogLevel.Error, LogLevel.Warn), page.data.map { it.level })
            assertEquals("Radarr", page.data[1].label)
            assertNull(page.data[2].label)
            assertTrue(page.data[1].data?.contains("\"status\": 500") == true)
            assertNull(page.data[2].data)
            assertEquals(1_789_275_600_000L, page.data[0].timestampMillis)
            assertEquals("2026-09-13T04:59:00.000Z [error] [Radarr] Request failed", page.data[1].copyText.substringBefore(" {"))
            assertEquals(1, page.nextKey)
            assertNull(page.prevKey)

            val request = received.last()
            assertEquals("25", request.url.queryParameter("take"))
            assertEquals("0", request.url.queryParameter("skip"))
            assertEquals("info", request.url.queryParameter("filter"))
            assertNull(request.url.queryParameter("search"))
        }

    @Test
    fun `the level and the search go on the query, and a later page skips ahead`() =
        runTest {
            val page = source(level = LogLevel.Error, search = "plex").page(2)
            assertNull(page.nextKey)
            assertEquals(1, page.prevKey)
            val request = received.last()
            assertEquals("error", request.url.queryParameter("filter"))
            assertEquals("plex", request.url.queryParameter("search"))
            assertEquals("50", request.url.queryParameter("skip"))
        }

    @Test
    fun `a bare array is read as one page`() =
        runTest {
            body = BARE_ARRAY
            val page = source(level = LogLevel.Debug).page(null)
            assertEquals(listOf("Bare"), page.data.map { it.message })
            assertEquals(LogLevel.Debug, page.data[0].level)
            assertNull(page.nextKey)
        }
}
