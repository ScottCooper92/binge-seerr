package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The API key is a permanent admin credential, so it must go to the saved server and nowhere a
 * redirect sends the request. The session cookie already had this guarantee ([SessionCookieJar]);
 * the header did not, because OkHttp follows a redirect inside an application interceptor's
 * `proceed` and copies that request's headers onto the next hop.
 */
class SeerrApiKeyRedirectTest {
    private val saved = MockWebServer().apply { start() }
    private val other = MockWebServer().apply { start() }
    private val factory = SeerrApiFactory(logRequests = false)
    private val savedUrl = saved.url("/").toString()

    @After
    fun tearDown() {
        factory.evict()
        saved.close()
        other.close()
    }

    @Test
    fun `the key is sent to the saved server`() =
        runBlocking {
            saved.enqueue(status())

            factory.cached(savedUrl, SeerrAuth.ApiKey(KEY)).status()

            assertEquals(KEY, saved.takeRequest().headers[API_KEY_HEADER])
        }

    @Test
    fun `a redirect to another host does not carry the key`() =
        runBlocking {
            // localhost and 127.0.0.1 are different hosts, which is all a cross-host hop needs to be.
            val elsewhere = "http://127.0.0.1:${other.port}/api/v1/status"
            saved.enqueue(MockResponse(code = 302, headers = headersOf("Location", elsewhere)))
            other.enqueue(status())

            factory.cached(savedUrl, SeerrAuth.ApiKey(KEY)).status()

            assertEquals(KEY, saved.takeRequest().headers[API_KEY_HEADER])
            assertNull("the key leaked to another host", other.takeRequest().headers[API_KEY_HEADER])
        }

    @Test
    fun `a redirect within the saved server keeps the key`() =
        runBlocking {
            saved.enqueue(MockResponse(code = 302, headers = headersOf("Location", savedUrl + "api/v1/status")))
            saved.enqueue(status())

            factory.cached(savedUrl, SeerrAuth.ApiKey(KEY)).status()

            repeat(2) { assertEquals(KEY, saved.takeRequest().headers[API_KEY_HEADER]) }
        }

    @Test
    fun `only the saved server's own origin shares the key`() {
        val base = "https://seerr.example:5055/".toHttpUrl()

        assertTrue("https://seerr.example:5055/api/v1/status".toHttpUrl().sharesOriginWith(base))
        assertFalse("http://seerr.example:5055/api/v1/status".toHttpUrl().sharesOriginWith(base))
        assertFalse("https://other.example:5055/api/v1/status".toHttpUrl().sharesOriginWith(base))
        assertFalse("https://seerr.example:8443/api/v1/status".toHttpUrl().sharesOriginWith(base))
    }

    private fun status() =
        MockResponse(
            code = 200,
            headers = headersOf("Content-Type", "application/json"),
            body = """{"version":"2.7.0"}""",
        )

    private companion object {
        const val KEY = "secret-key"
        const val API_KEY_HEADER = "X-Api-Key"
    }
}
