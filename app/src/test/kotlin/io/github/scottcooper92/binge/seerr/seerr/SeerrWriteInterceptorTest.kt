package io.github.scottcooper92.binge.seerr.seerr

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Test

private const val HTTP_OK = 200
private const val HTTP_SERVER_ERROR = 500

class SeerrWriteInterceptorTest {
    private fun writes(
        method: String,
        code: Int,
    ): Int {
        var writeCount = 0
        MockWebServer().use { server ->
            server.enqueue(MockResponse(code = code, body = "{}"))
            server.start()
            val client = OkHttpClient.Builder().addInterceptor(SeerrWriteInterceptor { writeCount++ }).build()
            val body = if (method == "GET") null else "{}".toRequestBody()
            val builder = Request.Builder().url(server.url("/"))
            val request = builder.method(method, body).build()
            client.newCall(request).execute().close()
        }
        return writeCount
    }

    @Test
    fun `a successful GET does not report a write`() {
        assertEquals(0, writes(method = "GET", code = HTTP_OK))
    }

    @Test
    fun `a failed non-GET does not report a write`() {
        assertEquals(0, writes(method = "POST", code = HTTP_SERVER_ERROR))
    }

    @Test
    fun `a successful non-GET reports exactly one write`() {
        assertEquals(1, writes(method = "POST", code = HTTP_OK))
    }
}
