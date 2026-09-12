package io.github.scottcooper92.binge.seerr.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrLoginRequest
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException

/** Connecting, logging in and the cached user, against a real HTTP server on the JVM. */
class SeerrConnectionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val baseUrl = server.url("/").toString()

    private fun connection(scope: CoroutineScope): SeerrConnection =
        SeerrConnection(
            store =
                CredentialStore(
                    dataStore = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("creds.preferences_pb") },
                    cipher = ReversingCipher,
                ),
            apis = SeerrApiFactory(logRequests = false),
        )

    @After
    fun tearDown() = server.close()

    @Test
    fun `connect validates the key, detects the fork and saves`() =
        runTest {
            server.enqueue(json("""{"id":1,"permissions":2}"""))
            server.enqueue(json("""{"version":"2.7.0"}"""))
            val sut = connection(backgroundScope)

            val result = sut.connect(baseUrl, SeerrAuth.ApiKey("k3y"))

            val saved = result.getOrThrow()
            assertEquals(SeerrVariant.Jellyseerr, saved.variant)
            assertEquals(saved, sut.credentials.first())
            assertEquals("k3y", server.takeRequest().headers["X-Api-Key"])
        }

    @Test
    fun `a rejected key saves nothing`() =
        runTest {
            server.enqueue(MockResponse(code = 401))
            val sut = connection(backgroundScope)

            val result = sut.connect(baseUrl, SeerrAuth.ApiKey("bad"))

            assertTrue(result.exceptionOrNull() is HttpException)
            assertNull(sut.credentials.first())
        }

    @Test
    fun `an unparseable address fails before any request`() =
        runTest {
            val sut = connection(backgroundScope)

            val result = sut.connect("http://", SeerrAuth.ApiKey("k3y"))

            assertTrue(result.exceptionOrNull() is InvalidServerUrlException)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `login keeps the session cookie the server set`() =
        runTest {
            server.enqueue(json("""{"id":42}""", headersOf("Set-Cookie", "connect.sid=s3ss10n; Path=/; HttpOnly")))
            server.enqueue(json("""{"version":"3.1.0"}"""))
            val sut = connection(backgroundScope)

            val saved = sut.logIn(baseUrl, SeerrLoginRequest.Jellyfin("scott", "pw")).getOrThrow()

            assertEquals(SeerrAuth.Session("s3ss10n", userId = 42), saved.auth)
            assertEquals(SeerrVariant.Seerr, saved.variant)
            val login = server.takeRequest()
            assertEquals("/api/v1/auth/jellyfin", login.url.encodedPath)
            assertTrue(
                login.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"username\":\"scott\""),
            )
        }

    @Test
    fun `a login that sets no cookie is a failure, not a session`() =
        runTest {
            server.enqueue(json("""{"id":42}"""))
            val sut = connection(backgroundScope)

            val result = sut.logIn(baseUrl, SeerrLoginRequest.Local("s@example.com", "pw"))

            assertTrue(result.exceptionOrNull() is NoSessionCookieException)
            assertNull(sut.credentials.first())
        }

    @Test
    fun `the authenticated user is fetched once per connection and sent the session cookie`() =
        runTest {
            server.enqueue(json("""{"id":42}""", headersOf("Set-Cookie", "connect.sid=s3ss10n; Path=/")))
            server.enqueue(json("""{"version":"3.1.0"}"""))
            server.enqueue(json("""{"id":42,"permissions":32}"""))
            val sut = connection(backgroundScope)
            sut.logIn(baseUrl, SeerrLoginRequest.Local("s@example.com", "pw")).getOrThrow()
            repeat(2) { server.takeRequest() }

            val first = sut.authenticatedUser()
            val second = sut.authenticatedUser()

            assertEquals(32, first.permissions)
            assertEquals(first, second)
            assertEquals(3, server.requestCount)
            assertEquals("connect.sid=s3ss10n", server.takeRequest().headers["Cookie"])
        }

    @Test
    fun `nothing connected is NotConnectedException`() =
        runTest {
            val sut = connection(backgroundScope)

            assertTrue(runCatching { sut.api() }.exceptionOrNull() is NotConnectedException)
        }

    @Test
    fun `disconnect forgets the connection and the cached user`() =
        runTest {
            server.enqueue(json("""{"id":1,"permissions":2}"""))
            server.enqueue(json("""{"version":"3.1.0"}"""))
            val sut = connection(backgroundScope)
            sut.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()

            sut.disconnect()

            assertNull(sut.credentials.first())
            assertTrue(runCatching { sut.authenticatedUser() }.exceptionOrNull() is NotConnectedException)
        }

    private fun json(
        body: String,
        headers: okhttp3.Headers = headersOf(),
    ): MockResponse = MockResponse(code = 200, headers = headers.newBuilder().add("Content-Type", "application/json").build(), body = body)

    private object ReversingCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext.reversed()

        override fun decrypt(ciphertext: String): String = ciphertext.reversed()
    }
}
