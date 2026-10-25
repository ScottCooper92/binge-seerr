package com.cooper.binge.companion.seerr.seerr

import com.cooper.binge.companion.seerr.auth.SeerrConnectionHealthReporter
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HTTP_OK = 200
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_BAD_GATEWAY = 502
private const val HTTP_SERVER_ERROR = 500

/**
 * Driven through the client's own order: health outside [SeerrSessionInterceptor], so a 403 is read
 * the way [toSeerrError] reads it, with the mark already on it (#689).
 */
class SeerrHealthInterceptorTest {
    private class RecordingReporter : SeerrConnectionHealthReporter {
        val calls = mutableListOf<String>()

        override fun reportSuccess() {
            calls += "success"
        }

        override fun reportAuthFailure() {
            calls += "auth"
        }

        override fun reportNetworkFailure() {
            calls += "network"
        }
    }

    private val server = MockWebServer().apply { start() }
    private val reporter = RecordingReporter()
    private val baseUrl = server.url("/").toString()
    private val client =
        OkHttpClient
            .Builder()
            .addInterceptor(SeerrHealthInterceptor(reporter, baseUrl))
            .addInterceptor(SeerrSessionInterceptor(baseUrl))
            .build()

    @After
    fun tearDown() = server.close()

    private fun report(
        vararg answers: MockResponse,
        path: String = "/api/v1/request",
    ): List<String> {
        answers.forEach(server::enqueue)
        client.newCall(Request.Builder().url(server.url(path)).build()).execute().close()
        return reporter.calls
    }

    @Test
    fun `a 2xx and a non-auth 4xx report success`() {
        assertEquals(listOf("success"), report(MockResponse(code = HTTP_OK, body = "{}")))
        reporter.calls.clear()
        assertEquals(listOf("success"), report(MockResponse(code = HTTP_NOT_FOUND, body = "{}")))
    }

    @Test
    fun `a 401 reports an auth failure`() {
        assertEquals(listOf("auth"), report(MockResponse(code = HTTP_UNAUTHORIZED, body = "{}")))
    }

    @Test
    fun `a 403 while auth_me still answers is a permission, and reports success`() {
        val calls =
            report(
                MockResponse(code = HTTP_FORBIDDEN, body = "{}"),
                MockResponse(code = HTTP_OK, body = """{"id":1,"permissions":2}"""),
                path = "/api/v1/issue",
            )

        assertEquals(listOf("success"), calls)
    }

    @Test
    fun `a 403 whose auth_me is refused too reports an auth failure`() {
        val calls = report(MockResponse(code = HTTP_FORBIDDEN, body = "{}"), MockResponse(code = HTTP_FORBIDDEN, body = "{}"))

        assertEquals(listOf("auth"), calls)
    }

    @Test
    fun `a 403 from auth_me itself reports an auth failure`() {
        val calls = report(MockResponse(code = HTTP_FORBIDDEN, body = "{}"), path = "/api/v1/auth/me")

        assertEquals(listOf("auth"), calls)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a quota 403 reports success`() {
        assertEquals(listOf("success"), report(MockResponse(code = HTTP_FORBIDDEN, body = """{"message":"Quota exceeded"}""")))
    }

    @Test
    fun `a 429 and a 5xx report a network failure`() {
        assertEquals(listOf("network"), report(MockResponse(code = HTTP_TOO_MANY_REQUESTS, body = "{}")))
        reporter.calls.clear()
        assertEquals(listOf("network"), report(MockResponse(code = HTTP_SERVER_ERROR, body = "{}")))
    }

    @Test
    fun `a 500 from a route that asks an arr live is the arr being down, not Seerr`() {
        val profiles = "/api/v1/service/radarr/1"
        report(MockResponse(code = HTTP_SERVER_ERROR, body = "{}"), path = profiles)
        report(MockResponse(code = HTTP_SERVER_ERROR, body = "{}"), path = profiles)

        assertEquals(listOf("success", "success"), reporter.calls)
    }

    @Test
    fun `two 500s from a route Seerr answers itself still report network failures`() {
        report(MockResponse(code = HTTP_SERVER_ERROR, body = "{}"), path = "/api/v1/request")
        report(MockResponse(code = HTTP_SERVER_ERROR, body = "{}"), path = "/api/v1/service/radarr")

        assertEquals(listOf("network", "network"), reporter.calls)
    }

    @Test
    fun `a gateway error or a 429 on an arr route still counts against Seerr`() {
        val profiles = "/api/v1/service/sonarr/1"
        report(MockResponse(code = HTTP_BAD_GATEWAY, body = "{}"), path = profiles)
        report(MockResponse(code = HTTP_TOO_MANY_REQUESTS, body = "{}"), path = profiles)

        assertEquals(listOf("network", "network"), reporter.calls)
    }

    @Test
    fun `the routes that ask an arr live are the ones Seerr's service and settings routers send there`() {
        fun isArr(path: String) = server.url(path).isArrProxyRoute()

        assertTrue(isArr("/api/v1/service/radarr/2"))
        assertTrue(isArr("/api/v1/service/sonarr/2"))
        assertTrue(isArr("/api/v1/service/sonarr/lookup/603"))
        assertTrue(isArr("/api/v1/settings/radarr/test"))
        assertTrue(isArr("/api/v1/settings/sonarr/test"))
        assertTrue(isArr("/api/v1/settings/radarr/1/profiles"))
        assertTrue(isArr("/seerr/api/v1/service/radarr/2"))
        assertFalse(isArr("/api/v1/service/radarr"))
        assertFalse(isArr("/api/v1/settings/radarr"))
        assertFalse(isArr("/api/v1/settings/radarr/1"))
        assertFalse(isArr("/api/v1/request/1"))
        assertFalse(isArr("/service/radarr/2"))
    }

    @Test
    fun `a transport failure reports a network failure`() {
        val closed = MockWebServer()
        closed.start()
        val url = closed.url("/")
        closed.close()
        val client = OkHttpClient.Builder().addInterceptor(SeerrHealthInterceptor(reporter, url.toString())).build()
        runCatching { client.newCall(Request.Builder().url(url).build()).execute().close() }
        assertEquals(listOf("network"), reporter.calls)
    }
}
