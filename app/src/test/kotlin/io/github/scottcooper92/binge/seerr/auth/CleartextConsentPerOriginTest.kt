package io.github.scottcooper92.binge.seerr.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SessionCookieJar
import io.github.scottcooper92.binge.seerr.util.InMemoryDataStore
import io.github.scottcooper92.binge.seerr.util.PlainCipher
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val HOST = "seerr.example.com"

/**
 * Consent to plain HTTP is for one origin, and so is the session cookie (#1034). A server moved from `http` to `https` on
 * the same host keeps neither the opt-in nor a cookie a redirect back down to `http` would carry.
 */
class CleartextConsentPerOriginTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val cleartext = DataStoreCleartextConsent(InMemoryDataStore()) { null }

    /** Answers the connect's own calls with no socket at all, so an `https` address needs no certificate here. */
    private val server: (okhttp3.CookieJar) -> Interceptor = {
        Interceptor { chain ->
            val body =
                when (chain.request().url.encodedPath) {
                    "/api/v1/auth/me" -> """{"id":1,"permissions":2}"""
                    "/api/v1/status" -> """{"version":"3.0.0"}"""
                    else -> """{"mediaServerType":2}"""
                }
            Response
                .Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody())
                .build()
        }
    }

    @Test
    fun `moving a server to https on the same host drops its plain-http opt-in`() =
        runTest {
            cleartext.grant(HOST)
            val connection =
                SeerrConnection(
                    store =
                        CredentialStore(
                            PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c.preferences_pb") },
                            PlainCipher,
                        ),
                    apis = SeerrApiFactory(logRequests = false, cleartext = cleartext, testTransport = server),
                    cleartext = cleartext,
                )

            connection.connect("https://$HOST/", SeerrAuth.ApiKey("k3y")).getOrThrow()

            assertFalse(cleartext.allows(HOST))
        }

    @Test
    fun `an https server's session cookie never goes to http on the same host`() {
        val jar = SessionCookieJar("https://$HOST/", "s3ss10n")

        assertEquals(1, jar.loadForRequest("https://$HOST/api/v1/auth/me".toHttpUrl()).size)
        assertTrue(jar.loadForRequest("http://$HOST/api/v1/auth/me".toHttpUrl()).isEmpty())
    }

    @Test
    fun `a plain-http server's session cookie still goes to it`() {
        val jar = SessionCookieJar("http://$HOST/", "s3ss10n")

        assertEquals(1, jar.loadForRequest("http://$HOST/api/v1/auth/me".toHttpUrl()).size)
    }
}
