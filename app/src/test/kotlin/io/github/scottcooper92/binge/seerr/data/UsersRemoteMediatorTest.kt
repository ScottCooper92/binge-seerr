package io.github.scottcooper92.binge.seerr.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator.MediatorResult
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.ui.users.toEntity
import io.github.scottcooper92.binge.seerr.ui.users.toUserItem
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
{ "pageInfo": { "pages": 2, "results": 21 }, "results": [
  { "id": 1, "displayName": "Scott", "email": "s@example.com", "permissions": 2, "userType": 1, "requestCount": 12, "avatar": "https://img/1" },
  { "id": 2, "permissions": 32 },
  { "id": 3, "jellyfinUsername": "ana", "permissions": 32, "userType": 3, "requestCount": 3, "avatar": "/avatar.png" }
] }
"""

private const val LAST_PAGE = """
{ "pageInfo": { "pages": 2, "results": 21 }, "results": [ { "id": 4, "email": "bo@example.com", "userType": 2 } ] }
"""

/** The mediator over real sockets: the rows it keeps and drops, the cursor it records, and the sort it asks for. */
@OptIn(ExperimentalPagingApi::class)
class UsersRemoteMediatorTest {
    private val server = MockWebServer()
    private val received = mutableListOf<RecordedRequest>()
    private var pageBody: (Int) -> MockResponse = { skip -> json(if (skip == 0) PAGE0 else LAST_PAGE) }

    private val pagingState =
        PagingState<Int, UserEntity>(
            pages = emptyList(),
            anchorPosition = null,
            config = PagingConfig(pageSize = USERS_PAGE_SIZE),
            leadingPlaceholderCount = 0,
        )

    private fun start() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    return if (request.url.encodedPath ==
                        "/api/v1/user"
                    ) {
                        pageBody(request.url.queryParameter("skip")?.toInt() ?: 0)
                    } else {
                        MockResponse(code = 500)
                    }
                }
            }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    private fun api(): SeerrApi = SeerrApiFactory(logRequests = false).cached(server.url("/").toString(), SeerrAuth.ApiKey("k3y"))

    private fun mediator(
        store: UserStore,
        sort: String = "created",
    ) = UsersRemoteMediator(sort = sort, api = ::api, store = store) { dto, key, index -> dto.toUserItem()?.toEntity(key, index) }

    @Test
    fun `a refresh replaces the list with page zero, dropping a user with no name, and keeps the next cursor`() =
        runTest {
            start()
            val store = FakeUserStore()

            val result = mediator(store, sort = "requests").load(LoadType.REFRESH, pagingState)

            assertFalse((result as MediatorResult.Success).endOfPaginationReached)
            val (rows, nextSkip) = store.refreshed.single()
            assertEquals(listOf(1, 3), rows.map { it.id })
            assertEquals(listOf(0, 2), rows.map { it.orderIndex })
            assertEquals(USERS_PAGE_SIZE, nextSkip)
            val scott = rows.first()
            assertEquals("Scott", scott.name)
            assertEquals("Plex", scott.origin)
            assertEquals("https://img/1", scott.avatarUrl)
            assertEquals(12, scott.requestCount)
            val ana = rows.last()
            assertEquals("ana", ana.name)
            assertEquals("ana", ana.handle)
            assertEquals("Jellyfin", ana.origin)
            assertNull(ana.avatarUrl)
            assertEquals("requests", received.single().url.queryParameter("sort"))
        }

    @Test
    fun `an append reads the next page off the stored cursor and ends pagination on the last`() =
        runTest {
            start()
            val store = FakeUserStore()
            mediator(store).load(LoadType.REFRESH, pagingState)

            val result = mediator(store).load(LoadType.APPEND, pagingState)

            assertTrue((result as MediatorResult.Success).endOfPaginationReached)
            val (rows, nextSkip) = store.appended.single()
            assertEquals("bo", rows.single().name)
            assertEquals("Local", rows.single().origin)
            assertNull(nextSkip)
            assertEquals("20", received.last().url.queryParameter("skip"))

            pageBody = { MockResponse(code = 503) }
            assertTrue(mediator(store).load(LoadType.REFRESH, pagingState) is MediatorResult.Error)
        }

    private fun json(body: String) = MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)
}
