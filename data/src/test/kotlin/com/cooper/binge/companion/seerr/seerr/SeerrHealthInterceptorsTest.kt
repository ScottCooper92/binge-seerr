package com.cooper.binge.companion.seerr.seerr

import com.cooper.binge.companion.seerr.auth.SeerrAuthFailureClassifier
import com.cooper.binge.companion.seerr.auth.SeerrHealth
import com.cooper.binge.companion.seerr.demo.DEMO_HOST
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.server.connection.ConnectionHealth
import com.cooper.homelab.core.server.connection.ConnectionHealthMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private const val HTTP_OK = 200
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_BAD_GATEWAY = 502
private const val HTTP_SERVER_ERROR = 500
private const val HTTP_NOT_IMPLEMENTED = 501

/**
 * The interceptors [SeerrHealth] gives the saved server's client, driven through the client's own order: health outside
 * [SeerrSessionInterceptor], so a 403 is read the way [toSeerrError] reads it, with the mark already on it (#689). What a call
 * means is the shared library's classifier, so each case reads the verdict the monitor is left with, with one failure enough to
 * read as unreachable unless a case says otherwise.
 */
class SeerrHealthInterceptorsTest {
    private val id = ServerId("server-1")
    private val server = MockWebServer().apply { start() }
    private val baseUrl = server.url("/").toString()

    private fun healthOf(unreachableAfter: Int = 1) =
        SeerrHealth(ConnectionHealthMonitor(SeerrAuthFailureClassifier(::isArrProxyRoute), unreachableAfter)) { id }

    private fun SeerrHealth.verdict(): ConnectionHealth = runBlocking { health.first() }

    private fun SeerrHealth.client(vararg inner: Interceptor): OkHttpClient =
        OkHttpClient
            .Builder()
            .apply { interceptors(id).forEach(::addInterceptor) }
            .addInterceptor(SeerrSessionInterceptor(baseUrl))
            .apply { inner.forEach(::addInterceptor) }
            .build()

    @After
    fun tearDown() = server.close()

    /** The verdict after one call per path, each answered in turn, on a monitor that has seen nothing else. */
    private fun verdictAfter(
        vararg answers: MockResponse,
        paths: List<String> = listOf("/api/v1/request"),
    ): ConnectionHealth {
        val health = healthOf()
        val client = health.client()
        answers.forEach(server::enqueue)
        paths.forEach { client.newCall(Request.Builder().url(server.url(it)).build()).execute().close() }
        return health.verdict()
    }

    @Test
    fun `a 2xx and a non-auth 4xx read as healthy`() {
        assertEquals(ConnectionHealth.Healthy, verdictAfter(MockResponse(code = HTTP_OK, body = "{}")))
        assertEquals(ConnectionHealth.Healthy, verdictAfter(MockResponse(code = HTTP_NOT_FOUND, body = "{}")))
    }

    @Test
    fun `a 401 reads as an auth failure`() {
        assertEquals(ConnectionHealth.Unauthorized, verdictAfter(MockResponse(code = HTTP_UNAUTHORIZED, body = "{}")))
    }

    @Test
    fun `a 403 while auth_me still answers is a permission, and reads as healthy`() {
        val verdict =
            verdictAfter(
                MockResponse(code = HTTP_FORBIDDEN, body = "{}"),
                MockResponse(code = HTTP_OK, body = """{"id":1,"permissions":2}"""),
                paths = listOf("/api/v1/issue"),
            )

        assertEquals(ConnectionHealth.Healthy, verdict)
    }

    @Test
    fun `a 403 whose auth_me is refused too reads as an auth failure`() {
        val verdict = verdictAfter(MockResponse(code = HTTP_FORBIDDEN, body = "{}"), MockResponse(code = HTTP_FORBIDDEN, body = "{}"))

        assertEquals(ConnectionHealth.Unauthorized, verdict)
    }

    @Test
    fun `a 403 from auth_me itself reads as an auth failure`() {
        val verdict = verdictAfter(MockResponse(code = HTTP_FORBIDDEN, body = "{}"), paths = listOf("/api/v1/auth/me"))

        assertEquals(ConnectionHealth.Unauthorized, verdict)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a quota 403 reads as healthy`() {
        assertEquals(ConnectionHealth.Healthy, verdictAfter(MockResponse(code = HTTP_FORBIDDEN, body = """{"message":"Quota exceeded"}""")))
    }

    @Test
    fun `a 429 and a 5xx read as no answer`() {
        assertEquals(ConnectionHealth.Unreachable, verdictAfter(MockResponse(code = HTTP_TOO_MANY_REQUESTS, body = "{}")))
        assertEquals(ConnectionHealth.Unreachable, verdictAfter(MockResponse(code = HTTP_SERVER_ERROR, body = "{}")))
    }

    @Test
    fun `a 500 from a route that asks an arr live is the arr being down, not Seerr`() {
        val profiles = "/api/v1/service/radarr/1"
        val verdict =
            verdictAfter(
                MockResponse(code = HTTP_SERVER_ERROR, body = "{}"),
                MockResponse(code = HTTP_SERVER_ERROR, body = "{}"),
                paths = listOf(profiles, profiles),
            )

        assertEquals(ConnectionHealth.Healthy, verdict)
    }

    @Test
    fun `a 500 from a route Seerr answers itself, or from a bare arr path, still reads as no answer`() {
        assertEquals(ConnectionHealth.Unreachable, verdictAfter(MockResponse(code = HTTP_SERVER_ERROR, body = "{}")))
        assertEquals(
            ConnectionHealth.Unreachable,
            verdictAfter(MockResponse(code = HTTP_SERVER_ERROR, body = "{}"), paths = listOf("/api/v1/service/radarr")),
        )
    }

    @Test
    fun `a gateway error or a 429 on an arr route still counts against Seerr`() {
        val profiles = "/api/v1/service/sonarr/1"

        assertEquals(
            ConnectionHealth.Unreachable,
            verdictAfter(MockResponse(code = HTTP_BAD_GATEWAY, body = "{}"), paths = listOf(profiles)),
        )
        assertEquals(
            ConnectionHealth.Unreachable,
            verdictAfter(MockResponse(code = HTTP_TOO_MANY_REQUESTS, body = "{}"), paths = listOf(profiles)),
        )
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
    fun `the demo is always reached, so its 501 is an answer and any other 501 is not`() {
        val notImplemented =
            Interceptor { chain ->
                Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(HTTP_NOT_IMPLEMENTED)
                    .message("Not Implemented")
                    .build()
            }
        val demo = healthOf()
        val elsewhere = healthOf()

        demo
            .client(notImplemented)
            .newCall(Request.Builder().url("https://$DEMO_HOST/api/v1/status").build())
            .execute()
            .close()
        elsewhere
            .client(notImplemented)
            .newCall(Request.Builder().url("https://seerr.example.com/api/v1/status").build())
            .execute()
            .close()

        assertEquals(ConnectionHealth.Healthy, demo.verdict())
        assertEquals(ConnectionHealth.Unreachable, elsewhere.verdict())
    }

    private fun closedUrl() =
        MockWebServer().use {
            it.start()
            it.url("/")
        }

    private fun cancelling() =
        Interceptor { chain ->
            chain.call().cancel()
            throw IOException("Canceled")
        }

    @Test
    fun `a transport failure reads as no answer`() {
        val health = healthOf()

        runCatching {
            health
                .client()
                .newCall(Request.Builder().url(closedUrl()).build())
                .execute()
                .close()
        }

        assertEquals(ConnectionHealth.Unreachable, health.verdict())
    }

    @Test
    fun `a call its caller cancelled says nothing about the server`() {
        val health = healthOf()
        val client = health.client(cancelling())
        val url = server.url("/api/v1/request")

        repeat(2) { runCatching { client.newCall(Request.Builder().url(url).build()).execute().close() } }

        assertEquals(ConnectionHealth.Unchecked, health.verdict())
    }

    @Test
    fun `a cancelled call between two real failures neither hides nor adds one`() {
        val health = healthOf(unreachableAfter = 2)
        val failing = health.client()
        val cancelled = health.client(cancelling())
        val url = closedUrl()

        runCatching { failing.newCall(Request.Builder().url(url).build()).execute().close() }
        runCatching { cancelled.newCall(Request.Builder().url(url).build()).execute().close() }
        assertFalse(health.verdict() == ConnectionHealth.Unreachable)
        runCatching { failing.newCall(Request.Builder().url(url).build()).execute().close() }

        assertEquals(ConnectionHealth.Unreachable, health.verdict())
    }
}
