package com.cooper.binge.companion.seerr.discover.title

import com.cooper.binge.companion.seerr.discover.ExternalLink
import com.cooper.binge.companion.seerr.discover.ExternalLinkKind
import com.cooper.binge.companion.seerr.seerr.PERMISSION_ADMIN
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrPublicSettings
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.FakeRequest
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.homelab.core.foundation.ServerVersion
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

private const val SEASON =
    """{"seasonNumber":2,"name":"Season 2","episodes":[
{"id":22,"name":"Second","overview":"","airDate":"2012-04-08","episodeNumber":2,"seasonNumber":2,"stillPath":"/s2.jpg"},
{"id":21,"name":"First","overview":"Opening.","airDate":"","episodeNumber":1,"seasonNumber":2}]}"""

private const val RT = """{"title":"A Show","criticsScore":91,"audienceScore":88,"url":"https://rt.example/"}"""

class SeerrSeriesRepositoryTest {
    private val seerr = FakeSeerrServer()
    private val factory = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher)
    private var version = ServerVersion.of(3, 2, 0)
    private var variant = SeerrVariant.Seerr
    private var ratings = FakeResponse(headers = JSON, body = RT)

    @After
    fun tearDown() {
        seerr.awaitIdle()
        seerr.close()
    }

    private fun route(request: FakeRequest): FakeResponse =
        when (request.url.encodedPath) {
            "/api/v1/tv/1399" -> FakeResponse(headers = JSON, body = """{"id":1399,"name":"A Show"}""")
            "/api/v1/tv/1399/season/2" -> FakeResponse(headers = JSON, body = SEASON)
            "/api/v1/tv/1399/ratings" -> ratings
            "/api/v1/tv/1399/recommendations" ->
                FakeResponse(
                    headers = JSON,
                    body = """{"page":1,"totalPages":1,"results":[{"id":9,"mediaType":"tv","name":"Other","firstAirDate":"2015-01-01"}]}""",
                )
            "/api/v1/tv/1399/similar" -> FakeResponse(code = 500)
            "/api/v1/user/9/settings/main" -> FakeResponse(headers = JSON, body = "{}")
            else -> FakeResponse(code = 404)
        }

    private fun <T> withRepository(block: suspend (SeriesDetailsRepository) -> T): T {
        seerr.dispatcher = ::route
        return runBlocking {
            factory.anonymous(seerr.url()) { api ->
                val profile =
                    SeerrServerProfile(variant = variant, version = version, settings = SeerrPublicSettings())
                block(
                    SeerrSeriesRepository(
                        api = { api },
                        viewer = { SeerrUserDto(id = 9, permissions = PERMISSION_ADMIN) },
                        profile = { profile },
                        serverUrl = { "https://seerr.example/" },
                        deviceRegion = { null },
                        io = Dispatchers.Unconfined,
                    ),
                )
            }
        }
    }

    @Test
    fun `networks may be links only where the server can list what a network made`() {
        assertTrue(withRepository { it.series(1399) }.companiesOpen)
        variant = SeerrVariant.Overseerr
        version = ServerVersion.of(1, 20, 0)
        assertFalse(withRepository { it.series(1399) }.companiesOpen)
    }

    @Test
    fun `a show links to its TMDB page as a show`() {
        assertEquals(
            listOf(ExternalLink(ExternalLinkKind.Tmdb, "https://www.themoviedb.org/tv/1399")),
            withRepository { it.series(1399) }.links,
        )
    }

    @Test
    fun `keywords may be links only where the server can filter by keyword`() {
        assertTrue(withRepository { it.series(1399) }.keywordsOpen)
        variant = SeerrVariant.Overseerr
        version = ServerVersion.of(1, 8, 0)
        assertFalse(withRepository { it.series(1399) }.keywordsOpen)
    }

    @Test
    fun `a season's episodes come in order with blank text and dates left out`() {
        val episodes = withRepository { it.season(1399, 2) }

        assertEquals(listOf("First", "Second"), episodes.map { it.name })
        assertNull(episodes[0].airDate)
        assertEquals("Opening.", episodes[0].overview)
        assertNull(episodes[1].overview)
        assertEquals("2012-04-08", episodes[1].airDate)
        assertNull(episodes[0].stillUrl)
        assertTrue(episodes[1].stillUrl.orEmpty().endsWith("/s2.jpg"))
    }

    @Test
    fun `the extras give the Rotten Tomatoes score and recommendations and leave a failed call empty`() {
        val extras = withRepository { it.extras(1399) }

        assertEquals(91, extras.ratings?.criticsPercent)
        assertNull(extras.ratings?.imdbScore)
        assertEquals(listOf("Other"), extras.recommendations.map { it.name })
        assertTrue(extras.similar.isEmpty())
    }

    @Test
    fun `a show with no score has no ratings`() {
        ratings = FakeResponse(code = 404)

        assertNull(withRepository { it.extras(1399) }.ratings)
    }
}
