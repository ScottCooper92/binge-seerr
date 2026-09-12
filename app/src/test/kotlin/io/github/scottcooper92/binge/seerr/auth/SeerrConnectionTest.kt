package io.github.scottcooper92.binge.seerr.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrLoginRequest
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.seerr.SeerrVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.HttpException
import kotlin.time.Duration.Companion.milliseconds

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
            server.enqueue(json("""{"initialized":true,"localLogin":false}"""))
            val sut = connection(backgroundScope)

            val result = sut.connect(baseUrl, SeerrAuth.ApiKey("k3y"))

            val saved = result.getOrThrow()
            assertEquals(SeerrVariant.Jellyseerr, saved.variant)
            assertEquals(saved, sut.credentials.first())
            assertEquals("k3y", server.takeRequest().headers["X-Api-Key"])
            // The profile read on connect is the connection's: no further request serves it.
            val profile = sut.profile()
            assertEquals(3, server.requestCount)
            assertEquals(SeerrVersion(2, 7, 0), profile.version)
            assertFalse(profile.settings.localLogin)
        }

    @Test
    fun `a server that answers neither profile call is profiled as its recorded lineage, at its latest`() =
        runTest {
            server.enqueue(json("""{"id":1,"permissions":2}"""))
            server.enqueue(MockResponse(code = 503))
            server.enqueue(MockResponse(code = 503))
            val sut = connection(backgroundScope)

            val saved = sut.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()

            assertEquals(SeerrVariant.Unknown, saved.variant)
            assertNull(sut.profile().version)
        }

    @Test
    fun `refreshing the profile re-reads the server`() =
        runTest {
            server.enqueue(json("""{"id":1,"permissions":2}"""))
            server.enqueue(json("""{"version":"2.7.0"}"""))
            server.enqueue(json("""{"initialized":true}"""))
            val sut = connection(backgroundScope)
            sut.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()
            server.enqueue(json("""{"version":"3.0.0"}"""))
            server.enqueue(json("""{"initialized":true}"""))

            val refreshed = sut.refreshProfile()

            assertEquals(SeerrVersion(3, 0, 0), refreshed.version)
            assertEquals(5, server.requestCount)
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
            server.enqueue(json("""{"initialized":true}"""))
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
            server.enqueue(json("""{"initialized":true}"""))
            server.enqueue(json("""{"id":42,"permissions":32}"""))
            val sut = connection(backgroundScope)
            sut.logIn(baseUrl, SeerrLoginRequest.Local("s@example.com", "pw")).getOrThrow()
            repeat(3) { server.takeRequest() }

            val first = sut.authenticatedUser()
            val second = sut.authenticatedUser()

            assertEquals(32, first.permissions)
            assertEquals(first, second)
            assertEquals(4, server.requestCount)
            assertEquals("connect.sid=s3ss10n", server.takeRequest().headers["Cookie"])
        }

    @Test
    fun `nothing connected is NotConnectedException`() =
        runTest {
            val sut = connection(backgroundScope)

            assertTrue(runCatching { sut.api() }.exceptionOrNull() is NotConnectedException)
        }

    @Test
    fun `health follows the saved server's calls, and connecting or disconnecting resets it`() =
        runTest {
            server.enqueue(json("""{"id":1,"permissions":2}"""))
            server.enqueue(json("""{"version":"3.1.0"}"""))
            server.enqueue(json("""{"initialized":true}"""))
            val monitor = SeerrConnectionHealthMonitor()
            val sut =
                SeerrConnection(
                    store =
                        CredentialStore(
                            PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("h.preferences_pb") },
                            ReversingCipher,
                        ),
                    apis = SeerrApiFactory(logRequests = false, health = monitor),
                    healthMonitor = monitor,
                )
            assertEquals(SeerrConnectionHealth.NotConnected, sut.health.value)

            sut.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()
            assertEquals(SeerrConnectionHealth.Healthy, sut.health.value)

            server.enqueue(MockResponse(code = 401))
            runCatching { sut.api().authenticatedUser() }
            assertEquals(SeerrConnectionHealth.Unauthorized, sut.health.value)

            sut.disconnect()
            assertEquals(SeerrConnectionHealth.NotConnected, sut.health.value)
        }

    @Test
    fun `inspecting an address reads its profile and artwork with no credentials`() =
        runTest {
            server.enqueue(json("""{"version":"3.4.0"}"""))
            server.enqueue(json("""{"mediaServerType":2,"jellyfinServerName":"Home"}"""))
            server.enqueue(json("""["/one.jpg","/two.jpg"]"""))
            val sut = connection(backgroundScope)

            val preview = sut.inspect(server.url("/").host + ":" + server.port).getOrThrow()

            assertEquals(baseUrl, preview.baseUrl)
            assertEquals(SeerrVersion(3, 4, 0), preview.profile.version)
            assertTrue(preview.profile.hasQuickConnect)
            assertEquals(
                listOf("https://image.tmdb.org/t/p/w1280/one.jpg", "https://image.tmdb.org/t/p/w1280/two.jpg"),
                preview.backdropUrls,
            )
            repeat(3) { assertNull(server.takeRequest().headers["X-Api-Key"]) }
            assertNull(sut.credentials.first())
        }

    @Test
    fun `an address that answers neither profile call is not a Seerr server, and one that is down is unreachable`() =
        runTest {
            server.enqueue(MockResponse(code = 404))
            server.enqueue(MockResponse(code = 404))
            val sut = connection(backgroundScope)

            assertTrue(sut.inspect(baseUrl).exceptionOrNull() is NotSeerrServerException)
            assertEquals(2, server.requestCount)

            server.close()

            assertTrue(sut.inspect(baseUrl).exceptionOrNull() is java.io.IOException)
        }

    @Test
    fun `quick connect polls the code until it is approved, then signs in as that user`() =
        runTest {
            server.enqueue(json("""{"code":"123456","secret":"abcdef12"}"""))
            server.enqueue(json("""{"authenticated":false}"""))
            server.enqueue(json("""{"authenticated":true}"""))
            server.enqueue(json("""{"id":7}""", headersOf("Set-Cookie", "connect.sid=qc; Path=/")))
            server.enqueue(json("""{"version":"3.4.0"}"""))
            server.enqueue(json("""{"mediaServerType":2}"""))
            val sut =
                SeerrConnection(
                    store =
                        CredentialStore(
                            PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("qc.preferences_pb") },
                            ReversingCipher,
                        ),
                    apis = SeerrApiFactory(logRequests = false),
                    quickConnectPollInterval = 10.milliseconds,
                )

            val session = sut.startQuickConnect(baseUrl).getOrThrow()
            val saved = sut.finishQuickConnect(baseUrl, session).getOrThrow()

            assertEquals("123456", session.code)
            assertEquals(SeerrAuth.Session("qc", userId = 7), saved.auth)
            assertEquals("/api/v1/auth/jellyfin/quickconnect/initiate", server.takeRequest().url.encodedPath)
            val check = server.takeRequest()
            assertEquals("/api/v1/auth/jellyfin/quickconnect/check", check.url.encodedPath)
            assertEquals("abcdef12", check.url.queryParameter("secret"))
            server.takeRequest()
            val authenticate = server.takeRequest()
            assertEquals("/api/v1/auth/jellyfin/quickconnect/authenticate", authenticate.url.encodedPath)
            assertTrue(
                authenticate.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"secret\":\"abcdef12\""),
            )
        }

    @Test
    fun `a quick connect code the server has forgotten is expired`() =
        runTest {
            server.enqueue(MockResponse(code = 404))
            val sut = connection(backgroundScope)

            val result = sut.finishQuickConnect(baseUrl, SeerrQuickConnect(code = "123456", secret = "abcdef12"))

            assertTrue(result.exceptionOrNull() is QuickConnectExpiredException)
            assertNull(sut.credentials.first())
        }

    @Test
    fun `a plex token signs in and keeps the session`() =
        runTest {
            server.enqueue(json("""{"id":9}""", headersOf("Set-Cookie", "connect.sid=plx; Path=/")))
            server.enqueue(json("""{"version":"1.33.2"}"""))
            server.enqueue(json("""{"localLogin":true}"""))
            val sut = connection(backgroundScope)

            val saved = sut.logInWithPlex(baseUrl, "tok3n").getOrThrow()

            assertEquals(SeerrAuth.Session("plx", userId = 9), saved.auth)
            assertEquals(SeerrVariant.Overseerr, saved.variant)
            val login = server.takeRequest()
            assertEquals("/api/v1/auth/plex", login.url.encodedPath)
            assertTrue(
                login.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"authToken\":\"tok3n\""),
            )
        }

    @Test
    fun `a password reset posts the address without credentials`() =
        runTest {
            server.enqueue(json("""{"status":"ok"}"""))
            val sut = connection(backgroundScope)

            sut.requestPasswordReset(baseUrl, "s@example.com").getOrThrow()

            val reset = server.takeRequest()
            assertEquals("/api/v1/auth/reset-password", reset.url.encodedPath)
            assertTrue(
                reset.body
                    ?.utf8()
                    .orEmpty()
                    .contains("\"email\":\"s@example.com\""),
            )
            assertNull(reset.headers["X-Api-Key"])
        }

    @Test
    fun `disconnecting a session sign-in ends it on the server first, and a key connection posts nothing`() =
        runTest {
            server.enqueue(json("""{"id":42}""", headersOf("Set-Cookie", "connect.sid=s3ss10n; Path=/")))
            server.enqueue(json("""{"version":"3.1.0"}"""))
            server.enqueue(json("""{"initialized":true}"""))
            server.enqueue(json("""{"status":"ok"}"""))
            val sut = connection(backgroundScope)
            sut.logIn(baseUrl, SeerrLoginRequest.Local("s@example.com", "pw")).getOrThrow()
            repeat(3) { server.takeRequest() }

            sut.disconnect()

            val logout = server.takeRequest()
            assertEquals("/api/v1/auth/logout", logout.url.encodedPath)
            assertEquals("connect.sid=s3ss10n", logout.headers["Cookie"])
            assertNull(sut.credentials.first())

            server.enqueue(json("""{"id":1,"permissions":2}"""))
            server.enqueue(json("""{"version":"3.1.0"}"""))
            server.enqueue(json("""{"initialized":true}"""))
            sut.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()

            sut.disconnect()

            assertEquals(7, server.requestCount)
        }

    @Test
    fun `disconnect forgets the connection and the cached user`() =
        runTest {
            server.enqueue(json("""{"id":1,"permissions":2}"""))
            server.enqueue(json("""{"version":"3.1.0"}"""))
            server.enqueue(json("""{"initialized":true}"""))
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
