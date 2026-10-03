package io.github.scottcooper92.binge.seerr.seerr

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException

/**
 * A 403 is a permission or a dead session on every lineage, and only `auth/me` tells them apart (#676).
 * Driven through the interceptor on a real client, and classified the way every caller classifies it.
 */
class SeerrSessionInterceptorTest {
    private val server = MockWebServer().apply { start() }
    private val client = OkHttpClient.Builder().addInterceptor(SeerrSessionInterceptor(server.url("/").toString())).build()

    @After
    fun tearDown() = server.close()

    private fun classify(path: String): SeerrError {
        val response = client.newCall(Request.Builder().url(server.url(path)).build()).execute()
        return HttpException(retrofit2.Response.error<Unit>(response.body, response)).toSeerrError()
    }

    @Test
    fun `a 403 whose auth_me is refused too is the session`() {
        server.enqueue(MockResponse(code = 403))
        server.enqueue(MockResponse(code = 403))

        assertEquals(SeerrError.Unauthorized, classify("/api/v1/request"))
        assertEquals("/api/v1/auth/me", server.takeRequest().let { server.takeRequest().url.encodedPath })
    }

    @Test
    fun `a 403 while auth_me still answers is a permission`() {
        server.enqueue(MockResponse(code = 403))
        server.enqueue(MockResponse(code = 200, body = """{"id":1,"permissions":2}"""))

        assertEquals(SeerrError.Forbidden, classify("/api/v1/request"))
    }

    @Test
    fun `a quota 403 is not probed`() {
        server.enqueue(MockResponse(code = 403, body = """{"message":"Quota exceeded"}"""))

        assertEquals(SeerrError.Quota, classify("/api/v1/request"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `auth_me itself is not probed`() {
        server.enqueue(MockResponse(code = 403))

        assertEquals(SeerrError.Forbidden, classify("/api/v1/auth/me"))
        assertEquals(1, server.requestCount)
    }
}
