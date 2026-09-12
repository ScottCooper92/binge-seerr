package io.github.scottcooper92.binge.seerr.auth

import io.github.scottcooper92.binge.seerr.seerr.PlexClientIdentity
import io.github.scottcooper92.binge.seerr.seerr.plexTvApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

/** The plex.tv side of a Plex sign-in, against a scripted plex.tv. */
class PlexPinFlowTest {
    private val plex = MockWebServer().apply { start() }
    private val identity = PlexClientIdentity(identifier = "cid 1", product = "Binge Seerr", version = "0.1.0", device = "Pixel")

    private fun flow(now: () -> Instant = Instant::now): PlexPinFlow =
        PlexPinFlow(
            identity = { identity },
            apis = { plexTvApi(it, plex.url("/").toString()) },
            pollInterval = 10.milliseconds,
            now = now,
        )

    @After
    fun tearDown() = plex.close()

    @Test
    fun `a pin is minted with the client identity and polled until it carries a token`() =
        runTest {
            plex.enqueue(json("""{"id":41,"code":"ABCD","authToken":null,"expiresAt":"2099-01-01T00:00:00Z"}"""))
            plex.enqueue(json("""{"id":41,"code":"ABCD","authToken":null}"""))
            plex.enqueue(json("""{"id":41,"code":"ABCD","authToken":"tok3n"}"""))
            val sut = flow()

            val pin = sut.start()
            val token = sut.awaitToken(pin)

            assertEquals("tok3n", token)
            assertEquals("ABCD", pin.code)
            assertEquals("https://app.plex.tv/auth#?clientID=cid+1&code=ABCD&context%5Bdevice%5D%5Bproduct%5D=Binge+Seerr", pin.authUrl)
            val mint = plex.takeRequest()
            assertEquals("/api/v2/pins?strong=true", mint.url.encodedPath + "?" + mint.url.encodedQuery)
            assertEquals("cid 1", mint.headers["X-Plex-Client-Identifier"])
            assertEquals("Binge Seerr", mint.headers["X-Plex-Product"])
            assertEquals("/api/v2/pins/41", plex.takeRequest().url.encodedPath)
            assertEquals(3, plex.requestCount)
        }

    @Test
    fun `a pin past its deadline, or one plex has forgotten, is expired`() =
        runTest {
            plex.enqueue(json("""{"id":41,"code":"ABCD","expiresAt":"2020-01-01T00:00:00Z"}"""))
            val sut = flow(now = { Instant.parse("2020-01-01T00:00:01Z") })
            val pin = sut.start()

            assertTrue(runCatching { sut.awaitToken(pin) }.exceptionOrNull() is PlexPinExpiredException)
            assertEquals(1, plex.requestCount)

            plex.enqueue(json("""{"id":42,"code":"EFGH"}"""))
            plex.enqueue(MockResponse(code = 404))
            val forgotten = flow().start()

            assertTrue(runCatching { flow().awaitToken(forgotten) }.exceptionOrNull() is PlexPinExpiredException)
        }

    private fun json(body: String): MockResponse =
        MockResponse(code = 200, headers = okhttp3.Headers.headersOf("Content-Type", "application/json"), body = body)
}
