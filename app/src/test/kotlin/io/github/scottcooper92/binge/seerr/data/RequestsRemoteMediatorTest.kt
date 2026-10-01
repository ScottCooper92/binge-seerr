package io.github.scottcooper92.binge.seerr.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator.MediatorResult
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.ui.requests.REQUESTS_PAGE_SIZE
import io.github.scottcooper92.binge.seerr.ui.requests.toRequestEntity
import io.github.scottcooper92.binge.seerr.ui.requests.toRequestItem
import io.github.scottcooper92.binge.seerr.util.FakeTitleDao
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

private const val PAGE0 = """
{
  "pageInfo": { "pages": 2, "results": 25 },
  "results": [
    { "id": 11, "status": 2, "createdAt": "2026-06-01T10:00:00.000Z", "requestedBy": { "displayName": "scott" },
      "media": { "tmdbId": 100, "mediaType": "movie", "status": 3,
        "downloadStatus": [ { "title": "Heat.1995.mkv", "size": 100.0, "sizeLeft": 25.0, "status": "downloading", "timeLeft": "00:30:00" } ] } },
    { "id": 12, "status": 1, "createdAt": "2026-06-02T10:00:00.000Z", "requestedBy": { "email": "user@example.com" },
      "seasons": [ { "seasonNumber": 1 }, { "seasonNumber": 2 } ], "media": { "tmdbId": 200, "mediaType": "tv", "status": 5 } },
    { "id": 13, "status": 2, "is4k": true,
      "media": { "tmdbId": 400, "mediaType": "movie", "status": 2, "status4k": 5 } },
    { "id": 14, "status": 1, "media": { "tmdbId": 300, "mediaType": "person" } }
  ]
}
"""

private const val LAST_PAGE = """
{ "pageInfo": { "pages": 2, "results": 25 }, "results": [
  { "id": 15, "status": 1, "media": { "tmdbId": 500, "mediaType": "movie" } }
] }
"""

/** The mediator over real sockets: page arithmetic, the rows it keeps and drops, and the cursor it records. */
@OptIn(ExperimentalPagingApi::class)
class RequestsRemoteMediatorTest {
    private val server = MockWebServer()
    private val received = CopyOnWriteArrayList<RecordedRequest>()
    private var pageBody: (Int) -> MockResponse = { skip -> json(if (skip == 0) PAGE0 else LAST_PAGE) }
    private var movieResponse: () -> MockResponse = {
        json("""{ "title": "Heat", "posterPath": "/heat.jpg", "releaseDate": "1995-12-15" }""")
    }

    private val pagingState =
        PagingState<Int, RequestEntity>(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = REQUESTS_PAGE_SIZE),
            leadingPlaceholderCount = 0,
        )

    private fun start() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    val path = request.url.encodedPath
                    return when {
                        path == "/api/v1/request" -> pageBody(request.url.queryParameter("skip")?.toInt() ?: 0)
                        path.startsWith("/api/v1/movie/") -> movieResponse()
                        path.startsWith("/api/v1/tv/") ->
                            json("""{ "name": "Severance", "posterPath": "/sev.jpg", "firstAirDate": "2022-02-18" }""")
                        else -> MockResponse(code = 500)
                    }
                }
            }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun api(): SeerrApi = SeerrApiFactory(logRequests = false).cached(server.url("/").toString(), SeerrAuth.ApiKey("k3y"))

    private fun mediator(
        store: RequestStore,
        query: RequestListQuery = RequestListQuery("all", "added", requestedBy = null),
        titles: TitleCache = TitleCache(FakeTitleDao()),
    ) = RequestsRemoteMediator(query = query, api = ::api, store = store) { dto, api, key, index ->
        dto.toRequestEntity(api, titles::get, key, index, nowMillis = 0L)
    }

    @Test
    fun `a refresh replaces the list with page zero, titled, dropping what it cannot show, and keeps the next cursor`() =
        runTest {
            start()
            val store = FakeRequestStore()

            val result = mediator(store).load(LoadType.REFRESH, pagingState)

            assertFalse((result as MediatorResult.Success).endOfPaginationReached)
            val (rows, nextSkip) = store.refreshed.single()
            assertEquals(listOf(11, 12, 13), rows.map { it.id })
            assertEquals(listOf(0, 1, 2), rows.map { it.orderIndex })
            assertEquals(REQUESTS_PAGE_SIZE, nextSkip)
            assertEquals("all:added:all", rows.first().listKey)
            val heat = rows[0].toRequestItem()
            assertEquals("Heat", heat.title)
            assertEquals("https://image.tmdb.org/t/p/w342/heat.jpg", heat.posterUrl)
            assertEquals("1995", heat.year)
            assertEquals("scott", heat.requestedBy)
            assertEquals(SeerrRequestStatusCode.Approved, heat.status)
            assertEquals(SeerrMediaStatusCode.Processing, heat.mediaStatus)
            assertEquals(0.75f, checkNotNull(heat.download).fraction)
            assertTrue(checkNotNull(heat.download).downloading)
            assertEquals(30, checkNotNull(heat.download).etaMinutes)
            val severance = rows[1].toRequestItem()
            assertEquals("Severance", severance.title)
            assertEquals("user", severance.requestedBy)
            assertEquals(listOf(1, 2), severance.seasonNumbers)
            assertNull(severance.download)
            val fourK = rows[2].toRequestItem()
            assertTrue(fourK.is4k)
            assertEquals(SeerrMediaStatusCode.Available, fourK.mediaStatus)
            assertEquals(emptyList<Int>(), fourK.seasonNumbers)
        }

    @Test
    fun `an append reads the next page off the stored cursor and ends pagination on the last`() =
        runTest {
            start()
            val store = FakeRequestStore()
            mediator(store).load(LoadType.REFRESH, pagingState)

            val result = mediator(store).load(LoadType.APPEND, pagingState)

            assertTrue((result as MediatorResult.Success).endOfPaginationReached)
            val (rows, nextSkip) = store.appended.single()
            assertEquals(listOf(15), rows.map { it.id })
            assertEquals(REQUESTS_PAGE_SIZE, rows.single().orderIndex)
            assertNull(nextSkip)
            assertEquals("20", received.last { it.url.encodedPath == "/api/v1/request" }.url.queryParameter("skip"))

            val again = mediator(store).load(LoadType.APPEND, pagingState)
            assertTrue((again as MediatorResult.Success).endOfPaginationReached)
            assertEquals(2, received.count { it.url.encodedPath == "/api/v1/request" })
        }

    @Test
    fun `the query carries the filter, sort and user scope, each scope keeps its own list, and titles are fetched once`() =
        runTest {
            start()
            val store = FakeRequestStore()
            val titles = TitleCache(FakeTitleDao())
            val query = RequestListQuery("pending", "modified", requestedBy = 7)

            mediator(store, query, titles).load(LoadType.REFRESH, pagingState)
            mediator(store, query, titles).load(LoadType.REFRESH, pagingState)

            val list = received.first { it.url.encodedPath == "/api/v1/request" }.url
            assertEquals("pending", list.queryParameter("filter"))
            assertEquals("modified", list.queryParameter("sort"))
            assertEquals("0", list.queryParameter("skip"))
            assertEquals("7", list.queryParameter("requestedBy"))
            assertEquals("pending:modified:7", store.rows.first().listKey)
            assertEquals(1, received.count { it.url.encodedPath == "/api/v1/movie/100" })
        }

    @Test
    fun `a failed title lookup degrades its row only`() =
        runTest {
            movieResponse = { MockResponse(code = 500) }
            start()
            val store = FakeRequestStore()

            mediator(store).load(LoadType.REFRESH, pagingState)

            val rows = store.refreshed.single().first
            assertNull(rows[0].title)
            assertEquals("Severance", rows[1].title)
        }

    @Test
    fun `a failing server is a retryable error, not a crash`() =
        runTest {
            start()
            val store = FakeRequestStore()
            pageBody = { MockResponse(code = 503) }

            val result = mediator(store).load(LoadType.REFRESH, pagingState)

            assertTrue(result is MediatorResult.Error)
            assertTrue(store.refreshed.isEmpty())
        }

    @Test
    fun `an app write moves the cached row, and a server change clears every list and cursor`() =
        runTest {
            start()
            val store = FakeRequestStore()
            mediator(store).load(LoadType.REFRESH, pagingState)

            store.updateStatus(12, SeerrRequestStatusCode.Declined.raw)
            assertEquals(
                SeerrRequestStatusCode.Declined,
                store.rows
                    .single { it.id == 12 }
                    .toRequestItem()
                    .status,
            )
            store.delete(11)
            assertEquals(listOf(12, 13), store.rows.map { it.id })

            store.clearAll()

            assertTrue(store.rows.isEmpty())
            assertNull(store.nextSkip("all:added:all"))
        }

    private fun json(body: String) = MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)
}
