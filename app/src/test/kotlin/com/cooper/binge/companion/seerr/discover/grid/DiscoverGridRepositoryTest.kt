package com.cooper.binge.companion.seerr.discover.grid

import com.cooper.binge.companion.seerr.discover.SeerrTitleListRepository
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.SeerrVersion
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val JSON = headersOf("Content-Type", "application/json")

private const val PAGE = """{"page":1,"totalPages":4,"totalResults":80,"results":[
{"id":603,"mediaType":"movie","title":"The Matrix","posterPath":"/m.jpg","releaseDate":"1999-03-30","mediaInfo":{"id":11,"status":5}},
{"id":1399,"mediaType":"tv","name":"A Show","posterPath":"/g.jpg","firstAirDate":"2011-04-17"}]}"""

class DiscoverGridRepositoryTest {
    private val seerr = FakeSeerrServer()
    private val factory = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher)
    private var profile = SeerrServerProfile(variant = SeerrVariant.Seerr, version = SeerrVersion(3, 2, 0))

    @After
    fun tearDown() {
        seerr.awaitIdle()
        seerr.close()
    }

    private fun <T> withGrid(block: suspend (DiscoverGridRepository) -> T): T =
        runBlocking {
            seerr.dispatcher = { FakeResponse(headers = JSON, body = PAGE) }
            factory.anonymous(seerr.url()) { api ->
                val titles = SeerrTitleListRepository(api = { api }, profile = { profile }, io = Dispatchers.Unconfined)
                block(SeerrDiscoverGridRepository(api = { api }, profile = { profile }, titles = titles, io = Dispatchers.Unconfined))
            }
        }

    private fun lastUrl() = seerr.requests.last().url

    @Test
    fun `a server with the filter parameters is asked for the filters and the order on the plain route`() {
        val request =
            GridRequest(
                TitleKind.Movie,
                GridPreset.Genre(18),
                DiscoverFilters(genres = setOf(18), ratingMin = 7.0),
                DiscoverSort.RatingDesc,
            )
        val page = withGrid { it.load(request, page = 2) }

        assertEquals("/api/v1/discover/movies", lastUrl().encodedPath)
        assertEquals("2", lastUrl().queryParameter("page"))
        assertEquals("vote_average.desc", lastUrl().queryParameter("sortBy"))
        assertEquals("18", lastUrl().queryParameter("genre"))
        assertEquals("7.0", lastUrl().queryParameter("voteAverageGte"))
        assertEquals("both rows come back as the kind asked for", listOf(TitleKind.Movie, TitleKind.Series), page.results.map { it.kind })
        assertEquals(4, page.totalPages)
    }

    @Test
    fun `a show is asked for on the TV route with its network`() {
        val request = GridRequest(TitleKind.Series, GridPreset.Company(213), DiscoverFilters(company = 213), DiscoverSort.PopularityDesc)
        withGrid { it.load(request, page = 1) }

        assertEquals("/api/v1/discover/tv", lastUrl().encodedPath)
        assertEquals("213", lastUrl().queryParameter("network"))
        assertNull(lastUrl().queryParameter("studio"))
    }

    @Test
    fun `a server without the filter parameters is asked for the preset's own route`() {
        profile = SeerrServerProfile(variant = SeerrVariant.Overseerr, version = SeerrVersion(1, 31, 0))
        val request = GridRequest(TitleKind.Movie, GridPreset.Genre(18), DiscoverFilters(genres = setOf(18)), DiscoverSort.RatingDesc)
        withGrid {
            assertFalse(it.canRefine(GridPreset.Genre(18)))
            it.load(request, page = 1)
        }

        assertEquals("/api/v1/discover/movies/genre/18", lastUrl().encodedPath)
        assertNull("the order is not sent where it cannot be applied", lastUrl().queryParameter("sortBy"))
    }

    @Test
    fun `trending and the watchlist are plain lists on every server`() {
        withGrid {
            assertFalse(it.canRefine(GridPreset.Trending))
            assertFalse(it.canRefine(GridPreset.Watchlist))
            assertTrue(it.canRefine(GridPreset.Popular))
            it.load(GridRequest(TitleKind.Movie, GridPreset.Trending, DiscoverFilters(), DiscoverSort.PopularityDesc), page = 1)
        }
        assertEquals("/api/v1/discover/trending", lastUrl().encodedPath)
        assertNull(lastUrl().queryParameter("sortBy"))
    }
}
