package io.github.scottcooper92.binge.seerr.handoff

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/** The phone posts the address, and only the address, the way the TV's own form does. */
class OkHttpAddressSenderTest {
    private val server = MockWebServer()
    private val token = "abcdefghijklmnopqrstuv"

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private val target get() = TvHandOffTarget(host = server.hostName, port = server.port, token = token)

    private val sender = OkHttpAddressSender(OkHttpClient.Builder().followRedirects(false).build())

    @Test
    fun `the address is posted as the form's one field to the token's path`() =
        runTest {
            server.enqueue(MockResponse(code = 200))

            assertTrue(sender.send(target, "http://seerr.lan:5055/", sealed = null))

            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("POST", request.method)
            assertEquals("/a/$token", request.url.encodedPath)
            assertEquals("address=http%3A%2F%2Fseerr.lan%3A5055%2F", request.body?.utf8())
            assertEquals(null, request.headers["Cookie"])
            assertEquals(null, request.headers["Authorization"])
            assertEquals(null, request.headers["X-Api-Key"])
        }

    @Test
    fun `anything but a 200 is a failure, and so is a TV that is not there`() =
        runTest {
            server.enqueue(MockResponse(code = 404))
            assertFalse(sender.send(target, "http://seerr.lan:5055/", sealed = null))

            server.enqueue(MockResponse(code = 302, headers = okhttp3.Headers.headersOf("Location", "http://example.com/")))
            assertFalse(sender.send(target, "http://seerr.lan:5055/", sealed = null))

            val gone = target
            server.close()
            assertFalse(sender.send(gone, "http://seerr.lan:5055/", sealed = null))
        }
}
