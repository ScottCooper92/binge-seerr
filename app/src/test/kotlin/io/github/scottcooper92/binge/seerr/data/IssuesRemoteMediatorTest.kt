package io.github.scottcooper92.binge.seerr.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator.MediatorResult
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.ui.issues.toIssueEntity
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

private const val PAGE0 = """
{ "pageInfo": { "pages": 2, "results": 25 }, "results": [
  { "id": 31, "issueType": 1, "status": 1, "createdAt": "2026-06-01T10:00:00.000Z", "createdBy": { "displayName": "scott" },
    "media": { "tmdbId": 100, "mediaType": "movie" } },
  { "id": 32, "issueType": 1 },
  { "id": 33, "issueType": 2, "status": 2, "problemSeason": 2, "problemEpisode": 5,
    "media": { "tmdbId": 200, "mediaType": "tv" },
    "comments": [ { "id": 1, "message": "Audio desync after the intro" }, { "id": 2, "message": "still broken" } ] }
] }
"""

private const val LAST_PAGE = """
{ "pageInfo": { "pages": 2, "results": 25 }, "results": [
  { "id": 34, "issueType": 4, "status": 1, "media": { "tmdbId": 300, "mediaType": "movie" } }
] }
"""

/** The mediator over real sockets: page arithmetic, the rows it keeps and drops, and the cursor it records. */
@OptIn(ExperimentalPagingApi::class)
class IssuesRemoteMediatorTest {
    private val server = MockWebServer()
    private val received = mutableListOf<RecordedRequest>()
    private var pageBody: (Int) -> MockResponse = { skip -> json(if (skip == 0) PAGE0 else LAST_PAGE) }

    private val pagingState =
        PagingState<Int, IssueEntity>(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = ISSUES_PAGE_SIZE),
            leadingPlaceholderCount = 0,
        )

    private fun start() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    return when {
                        request.url.encodedPath == "/api/v1/issue" -> pageBody(request.url.queryParameter("skip")?.toInt() ?: 0)
                        request.url.encodedPath.startsWith("/api/v1/movie/") -> json("""{"title":"Heat","releaseDate":"1995-12-15"}""")
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
        store: IssueStore,
        query: IssueListQuery = IssueListQuery("open", "added", requestedBy = null),
    ) = IssuesRemoteMediator(query = query, api = ::api, store = store) { dto, api, key, index ->
        dto.toIssueEntity(api, { a, type, id -> if (type == "movie") titleOf(a, id) else null }, key, index)
    }

    private suspend fun titleOf(
        api: SeerrApi,
        id: Int,
    ) = api.movieDetails(id).let {
        io.github.scottcooper92.binge.seerr.seerr
            .HydratedTitle(it.displayTitle, null, it.year)
    }

    @Test
    fun `a refresh replaces the list with page zero, titled, dropping what it cannot show, and keeps the next cursor`() =
        runTest {
            start()
            val store = FakeIssueStore()

            val result = mediator(store).load(LoadType.REFRESH, pagingState)

            assertFalse((result as MediatorResult.Success).endOfPaginationReached)
            val (rows, nextSkip) = store.refreshed.single()
            assertEquals(listOf(31, 33), rows.map { it.id })
            assertEquals(listOf(0, 2), rows.map { it.orderIndex })
            assertEquals(ISSUES_PAGE_SIZE, nextSkip)
            val heat = rows.first()
            assertEquals("Heat", heat.title)
            assertEquals("1995", heat.year)
            assertEquals("scott", heat.reportedBy)
            assertEquals("Open", heat.status)
            assertEquals("open:added", heat.listKey)
            val severance = rows.last()
            assertEquals("Resolved", severance.status)
            assertEquals("Audio", severance.issueType)
            assertEquals("Audio desync after the intro", severance.problem)
            assertEquals(2, severance.problemSeason)
            assertEquals(5, severance.problemEpisode)
            assertEquals(2, severance.commentCount)
            val query = received.first { it.url.encodedPath == "/api/v1/issue" }.url
            assertEquals("open", query.queryParameter("filter"))
            assertEquals("added", query.queryParameter("sort"))
            assertNull(query.queryParameter("requestedBy"))
        }

    @Test
    fun `an append reads the next page off the stored cursor and ends pagination on the last`() =
        runTest {
            start()
            val store = FakeIssueStore()
            mediator(store).load(LoadType.REFRESH, pagingState)

            val result = mediator(store).load(LoadType.APPEND, pagingState)

            assertTrue((result as MediatorResult.Success).endOfPaginationReached)
            val (rows, nextSkip) = store.appended.single()
            assertEquals(listOf(34), rows.map { it.id })
            assertEquals(ISSUES_PAGE_SIZE, rows.single().orderIndex)
            assertNull(nextSkip)
            assertEquals("20", received.last { it.url.encodedPath == "/api/v1/issue" }.url.queryParameter("skip"))

            val again = mediator(store).load(LoadType.APPEND, pagingState)
            assertTrue((again as MediatorResult.Success).endOfPaginationReached)
            assertEquals(2, received.count { it.url.encodedPath == "/api/v1/issue" })
        }

    @Test
    fun `a narrowed list sends the user, and a failing server is a retryable error, not a crash`() =
        runTest {
            start()
            val store = FakeIssueStore()
            mediator(store, IssueListQuery("all", "modified", requestedBy = 7)).load(LoadType.REFRESH, pagingState)
            assertEquals("7", received.last { it.url.encodedPath == "/api/v1/issue" }.url.queryParameter("requestedBy"))

            pageBody = { MockResponse(code = 503) }
            val result = mediator(store).load(LoadType.REFRESH, pagingState)

            assertTrue(result is MediatorResult.Error)
            assertEquals(1, store.refreshed.size)
        }

    private fun json(body: String) = MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)
}
