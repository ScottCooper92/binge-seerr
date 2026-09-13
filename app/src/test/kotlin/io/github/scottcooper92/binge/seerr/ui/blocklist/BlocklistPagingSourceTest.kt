package io.github.scottcooper92.binge.seerr.ui.blocklist

import androidx.paging.PagingSource
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
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
    """{"pageInfo":{"pages":2,"results":25},"results":[
       {"id":11,"tmdbId":100,"mediaType":"movie","title":"Inline Heat","createdAt":"2026-06-01T10:00:00.000Z",
        "user":{"displayName":"scott"},"blocklistedTags":"Anime, Reality TV , "},
       {"id":12,"tmdbId":200,"mediaType":"tv","title":"Inline Severance","user":{"email":"user@example.com"}},
       {"id":13,"title":"No media"},
       {"id":14,"tmdbId":300,"mediaType":"person","title":"Someone"}]}"""

/** The paging source over real sockets: the page, the titling, what is dropped, and what the server is asked. */
class BlocklistPagingSourceTest {
    private val seerr = MockWebServer()
    private val received = CopyOnWriteArrayList<RecordedRequest>()
    private var hydrate = true

    @Before
    fun setUp() {
        seerr.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    val path = request.url.encodedPath
                    return when {
                        path == "/api/v1/blocklist" || path == "/api/v1/blacklist" -> json(PAGE)
                        !hydrate -> MockResponse(code = 500)
                        path.startsWith(
                            "/api/v1/movie/",
                        ) -> json("""{"title":"Heat","posterPath":"/heat.jpg","releaseDate":"1995-12-15"}""")
                        path.startsWith("/api/v1/tv/") -> json("""{"name":"Severance","firstAirDate":"2022-02-18"}""")
                        else -> MockResponse(code = 404)
                    }
                }
            }
        seerr.start()
    }

    @After
    fun tearDown() = seerr.close()

    private fun source(
        path: String = "blocklist",
        filter: BlocklistFilter = BlocklistFilter.All,
        search: String? = null,
    ): BlocklistPagingSource {
        val api = SeerrApiFactory(logRequests = false).cached(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y"))
        val titles = TitleCache()
        return BlocklistPagingSource(
            api = { api },
            path = path,
            filter = filter,
            search = search,
        ) { a, type, id -> titles.get(a, type, id) }
    }

    private suspend fun BlocklistPagingSource.firstPage(): PagingSource.LoadResult.Page<Int, BlocklistItem> =
        load(PagingSource.LoadParams.Refresh(key = null, loadSize = BLOCKLIST_PAGE_SIZE, placeholdersEnabled = false))
            as PagingSource.LoadResult.Page

    @Test
    fun `a page is titled through the lookup, keeps the tags, masks the blocker, and drops what cannot show`() =
        runTest {
            val page = source().firstPage()

            assertEquals(listOf(11, 12), page.data.map { it.id })
            val heat = page.data.first()
            assertEquals("Heat", heat.title)
            assertEquals("1995", heat.year)
            assertEquals(RequestMediaType.Movie, heat.mediaType)
            assertEquals("scott", heat.addedBy)
            assertEquals(listOf("Anime", "Reality TV"), heat.tags)
            assertTrue(heat.addedAtMillis != null)
            val severance = page.data.last()
            assertEquals("user", severance.addedBy)
            assertTrue(severance.tags.isEmpty())
            assertEquals(1, page.nextKey)
            assertNull(page.prevKey)
        }

    @Test
    fun `a failed lookup keeps the title the blocker gave`() =
        runTest {
            hydrate = false
            val page = source().firstPage()
            assertEquals("Inline Heat", page.data.first().title)
            assertNull(page.data.first().posterUrl)
        }

    @Test
    fun `the path, the filter and the search go to the server as given, with a blank search and All left out`() =
        runTest {
            source(path = "blacklist").firstPage()
            val legacy = received.last { it.url.encodedPath == "/api/v1/blacklist" }.url
            assertEquals("20", legacy.queryParameter("take"))
            assertEquals("0", legacy.queryParameter("skip"))
            assertNull(legacy.queryParameter("filter"))
            assertNull(legacy.queryParameter("search"))

            source(filter = BlocklistFilter.Tagged, search = " heat ").firstPage()
            val current = received.last { it.url.encodedPath == "/api/v1/blocklist" }.url
            assertEquals("blocklistedTags", current.queryParameter("filter"))
            assertEquals(" heat ", current.queryParameter("search"))
        }

    private fun json(body: String) = MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)
}
