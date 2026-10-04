package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.auth.CleartextConsent
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.net.InetAddress

/**
 * `CleartextGuardTest` checks the guard on its own. This checks the factory installs it and hands it
 * the consent the app holds (#709). A test transport cannot: it answers as an application
 * interceptor, before any network interceptor runs. So these calls go to a real local server, with
 * every public name resolved to it, and each one passes the factory's network interceptors.
 */
class SeerrApiFactoryCleartextTest {
    private val server = MockWebServer().apply { start() }
    private val toLocalServer = Dns { listOf(InetAddress.getLoopbackAddress()) }

    @After
    fun tearDown() = server.close()

    private fun factory(consent: CleartextConsent) = SeerrApiFactory(logRequests = false, cleartext = consent, testDns = toLocalServer)

    private fun publicUrl(host: String = PUBLIC_HOST) = "http://$host:${server.port}/api/v1/"

    @Test
    fun `plain http to a public host is refused without consent, before the server sees it`() {
        val factory = factory(CleartextConsent.None)

        val refused =
            assertThrows(CleartextRefusedException::class.java) {
                runBlocking { factory.anonymous(publicUrl()) { it.status() } }
            }

        assertEquals(PUBLIC_HOST, refused.host)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `every kind of client is served once the user has consented for the host`() {
        val factory = factory(Granted(PUBLIC_HOST))
        repeat(4) { server.enqueue(status()) }

        runBlocking {
            factory.anonymous(publicUrl()) { it.status() }
            factory.login(publicUrl()) { it.status() }
            factory.probe(publicUrl(), SeerrAuth.ApiKey("key")) { it.status() }
            factory.cached(publicUrl(), SeerrAuth.ApiKey("key")).status()
        }

        assertEquals(4, server.requestCount)
    }

    @Test
    fun `a redirect to a public host without consent is refused on the hop`() {
        val factory = factory(Granted(PUBLIC_HOST))
        server.enqueue(MockResponse(code = 302, headers = headersOf("Location", publicUrl(OTHER_HOST) + "status")))

        val refused =
            assertThrows(CleartextRefusedException::class.java) {
                runBlocking { factory.cached(publicUrl(), SeerrAuth.ApiKey("key")).status() }
            }

        assertEquals(OTHER_HOST, refused.host)
        assertEquals(1, server.requestCount)
    }

    private fun status() = MockResponse(code = 200, headers = JSON, body = """{"version":"2.0.0"}""")

    private class Granted(
        private val host: String,
    ) : CleartextConsent {
        override suspend fun allows(host: String) = host == this.host

        override suspend fun grant(host: String) = Unit

        override suspend fun retainOnly(host: String?) = Unit
    }

    private companion object {
        const val PUBLIC_HOST = "seerr.example.com"
        const val OTHER_HOST = "elsewhere.example.com"
        val JSON = headersOf("Content-Type", "application/json")
    }
}
