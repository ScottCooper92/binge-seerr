package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealthReporter
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

private const val HTTP_OK = 200
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val HTTP_SERVER_ERROR = 500

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

    private fun report(code: Int): List<String> {
        val reporter = RecordingReporter()
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = code, body = "{}"))
            server.start()
            val client = OkHttpClient.Builder().addInterceptor(SeerrHealthInterceptor(reporter)).build()
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
        }
        return reporter.calls
    }

    @Test
    fun `a 2xx and a non-auth 4xx report success`() {
        assertEquals(listOf("success"), report(HTTP_OK))
        assertEquals(listOf("success"), report(HTTP_NOT_FOUND))
    }

    @Test
    fun `a 401 and a 403 report an auth failure`() {
        assertEquals(listOf("auth"), report(HTTP_UNAUTHORIZED))
        assertEquals(listOf("auth"), report(HTTP_FORBIDDEN))
    }

    @Test
    fun `a 429 and a 5xx report a network failure`() {
        assertEquals(listOf("network"), report(HTTP_TOO_MANY_REQUESTS))
        assertEquals(listOf("network"), report(HTTP_SERVER_ERROR))
    }

    @Test
    fun `a transport failure reports a network failure`() {
        val reporter = RecordingReporter()
        val server = MockWebServer()
        server.start()
        val url = server.url("/")
        server.close()
        val client = OkHttpClient.Builder().addInterceptor(SeerrHealthInterceptor(reporter)).build()
        runCatching { client.newCall(Request.Builder().url(url).build()).execute().close() }
        assertEquals(listOf("network"), reporter.calls)
    }
}
