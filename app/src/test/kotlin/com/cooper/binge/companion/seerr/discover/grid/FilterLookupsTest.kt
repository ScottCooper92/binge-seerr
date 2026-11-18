package com.cooper.binge.companion.seerr.discover.grid

import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrServerProfile
import com.cooper.binge.companion.seerr.seerr.SeerrUserDto
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
import org.junit.Assert.assertTrue
import org.junit.Test

private val JSON = headersOf("Content-Type", "application/json")

class FilterLookupsTest {
    private val seerr = FakeSeerrServer()
    private val factory = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher)
    private var profile = SeerrServerProfile(variant = SeerrVariant.Seerr, version = SeerrVersion(3, 2, 0))

    @After
    fun tearDown() {
        seerr.awaitIdle()
        seerr.close()
    }

    private fun <T> withLookups(
        body: String,
        block: suspend (FilterLookups) -> T,
    ): T =
        runBlocking {
            seerr.dispatcher = { FakeResponse(headers = JSON, body = body) }
            factory.anonymous(seerr.url()) { api ->
                block(
                    SeerrFilterLookups(
                        api = { api },
                        profile = { profile },
                        viewer = { SeerrUserDto(id = 1) },
                        deviceRegion = { "ES" },
                        io = Dispatchers.Unconfined,
                    ),
                )
            }
        }

    @Test
    fun `the ratings of each country come most open first and a country with none is left out`() {
        val body =
            """{"certifications":{"GB":[{"certification":"15","order":3},{"certification":"U","order":1}],"XX":[]}}"""
        val ratings = withLookups(body) { it.certifications(TitleKind.Series) }

        assertEquals(
            "/api/v1/certifications/tv",
            seerr.requests
                .last()
                .url.encodedPath,
        )
        assertEquals(mapOf("GB" to listOf("U", "15")), ratings)
    }

    @Test
    fun `the providers of a region are asked for by kind and region and sorted by name`() {
        val providers =
            withLookups("""[{"id":337,"name":"Disney Plus"},{"id":8,"name":"Netflix"},{"id":0,"name":""}]""") {
                it.providers(TitleKind.Movie, "GB")
            }

        assertEquals(
            "/api/v1/watchproviders/movies",
            seerr.requests
                .last()
                .url.encodedPath,
        )
        assertEquals(
            "GB",
            seerr.requests
                .last()
                .url
                .queryParameter("watchRegion"),
        )
        assertEquals(listOf("337", "8"), providers.map { it.id })
    }

    @Test
    fun `a keyword search keeps the names the server gave`() {
        val found = withLookups("""{"results":[{"id":9715,"name":"superhero"},{"id":1}]}""") { it.searchKeywords(" hero ") }

        assertEquals(
            "hero",
            seerr.requests
                .last()
                .url
                .queryParameter("query"),
        )
        assertEquals(listOf("9715" to "superhero"), found.map { it.id to it.name })
    }

    @Test
    fun `the capabilities follow the server's version`() {
        profile = SeerrServerProfile(variant = SeerrVariant.Overseerr, version = SeerrVersion(1, 33, 2))
        val old = withLookups("{}") { it.capabilities() }
        assertTrue(old.watch && old.keywords)
        assertFalse(old.certification || old.excludeKeywords || old.status)

        profile = SeerrServerProfile(variant = SeerrVariant.Seerr, version = SeerrVersion(3, 2, 0))
        val current = withLookups("{}") { it.capabilities() }
        assertTrue(current.certification && current.excludeKeywords && current.status)
    }

    @Test
    fun `with no region chosen anywhere the default is the one the phone is set to`() {
        val region = withLookups("{}") { it.defaultRegion() }

        assertEquals("ES", region)
    }
}
