package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.paging.PagingSource
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
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
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

private const val PAGE_JSON = """
{
  "pageInfo": { "pages": 2, "results": 25 },
  "results": [
    { "id": 11, "status": 2, "createdAt": "2026-06-01T10:00:00.000Z", "requestedBy": { "displayName": "scott" },
      "media": { "tmdbId": 100, "mediaType": "movie", "status": 3,
        "downloadStatus": [ { "title": "Heat.1995.mkv", "size": 100.0, "sizeLeft": 25.0, "status": "downloading", "timeLeft": "00:30:00" } ] } },
    { "id": 12, "status": 1, "createdAt": "2026-06-02T10:00:00.000Z", "requestedBy": { "email": "user@example.com" },
      "seasons": [ { "seasonNumber": 1 }, { "seasonNumber": 2 } ], "media": { "tmdbId": 200, "mediaType": "tv", "status": 5 } },
    { "id": 14, "status": 1, "media": { "tmdbId": 300, "mediaType": "person" } }
  ]
}
"""

/** The request list over real sockets: page arithmetic, concurrent titling, degradation and the query the server sees. */
class RequestsPagingSourceTest {
    private val server = MockWebServer()
    private val received = CopyOnWriteArrayList<RecordedRequest>()
    private var movieResponse: () -> MockResponse = {
        json(
            """{ "title": "Heat", "posterPath": "/heat.jpg", "releaseDate": "1995-12-15" }""",
        )
    }

    private fun start() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    val path = request.url.encodedPath
                    return when {
                        path == "/api/v1/request" -> json(PAGE_JSON)
                        path.startsWith("/api/v1/movie/") -> movieResponse()
                        path.startsWith(
                            "/api/v1/tv/",
                        ) -> json("""{ "name": "Severance", "posterPath": "/sev.jpg", "firstAirDate": "2022-02-18" }""")
                        else -> MockResponse(code = 500)
                    }
                }
            }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun api(): SeerrApi = SeerrApiFactory(logRequests = false).cached(server.url("/").toString(), SeerrAuth.ApiKey("k3y"))

    private fun source(
        filter: RequestFilter = RequestFilter.All,
        sort: RequestSort = RequestSort.Added,
        requestedBy: Int? = null,
        titles: TitleCache = TitleCache(),
    ) = RequestsPagingSource(api = ::api, filter = filter, sort = sort, requestedBy = requestedBy, hydrate = titles::get, now = { 0L })

    private suspend fun RequestsPagingSource.page(key: Int? = null) = load(PagingSource.LoadParams.Refresh(key, REQUESTS_PAGE_SIZE, false))

    @Test
    fun `a page is titled row by row, drops what it cannot show, and keys the next page`() =
        runTest {
            start()

            val result = source().page() as PagingSource.LoadResult.Page

            assertEquals(listOf(11, 12), result.data.map { it.id })
            val heat = result.data[0]
            assertEquals("Heat", heat.title)
            assertEquals("https://image.tmdb.org/t/p/w342/heat.jpg", heat.posterUrl)
            assertEquals("1995", heat.year)
            assertEquals("scott", heat.requestedBy)
            assertEquals(SeerrRequestStatusCode.Approved, heat.status)
            assertEquals(SeerrMediaStatusCode.Processing, heat.mediaStatus)
            assertEquals(0.75f, checkNotNull(heat.download).fraction)
            assertTrue(checkNotNull(heat.download).downloading)
            assertEquals(30, checkNotNull(heat.download).etaMinutes)
            val severance = result.data[1]
            assertEquals("Severance", severance.title)
            assertEquals("user", severance.requestedBy)
            assertEquals(listOf(1, 2), severance.seasonNumbers)
            assertNull(severance.download)
            assertNull(result.prevKey)
            assertEquals(1, result.nextKey)
        }

    @Test
    fun `the last page has no next key, and a failed title lookup degrades its row only`() =
        runTest {
            movieResponse = { MockResponse(code = 500) }
            start()

            val result = source().page(key = 1) as PagingSource.LoadResult.Page

            assertEquals(0, result.prevKey)
            assertNull(result.nextKey)
            assertNull(result.data[0].title)
            assertEquals("Severance", result.data[1].title)
        }

    @Test
    fun `the query carries the filter, sort, offset and the user scope, and titles are fetched once`() =
        runTest {
            start()
            val titles = TitleCache()

            source(RequestFilter.Pending, RequestSort.Modified, requestedBy = 7, titles = titles).page(key = 1)
            source(RequestFilter.Pending, RequestSort.Modified, requestedBy = 7, titles = titles).page(key = 1)

            val list = received.first { it.url.encodedPath == "/api/v1/request" }.url
            assertEquals("pending", list.queryParameter("filter"))
            assertEquals("modified", list.queryParameter("sort"))
            assertEquals("20", list.queryParameter("skip"))
            assertEquals("7", list.queryParameter("requestedBy"))
            assertEquals(1, received.count { it.url.encodedPath == "/api/v1/movie/100" })
        }

    @Test
    fun `a list that fails is a load error the list can retry from`() =
        runTest {
            server.dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = MockResponse(code = 503)
                }
            server.start()

            assertTrue(source().page() is PagingSource.LoadResult.Error)
        }

    private fun json(body: String) = MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)
}
