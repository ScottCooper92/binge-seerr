package com.cooper.binge.companion.seerr.seerr

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

private const val SERVER_KEY = "server-admin-key-0f3a"
private const val CLIENT_KEY = "client-key-77c1"

/**
 * What a debug build writes to the device log (#1031). Bodies carry secrets the header redaction never sees, such as the
 * server's own API key on its main settings, so no client logs a body, however it was built.
 */
class SeerrApiFactoryLoggingTest {
    private val seerr = MockWebServer()
    private val logged = CopyOnWriteArrayList<String>()
    private val factory = SeerrApiFactory(logRequests = true, testLogger = HttpLoggingInterceptor.Logger(logged::add))

    @Before
    fun setUp() = seerr.start()

    @After
    fun tearDown() = seerr.close()

    @Test
    fun `no client logs a body, so the server's key in its settings never reaches the log`() {
        val baseUrl = seerr.url("/").toString()
        val settings =
            MockResponse(
                code = 200,
                headers = headersOf("Content-Type", "application/json"),
                body = """{"apiKey":"$SERVER_KEY","applicationTitle":"Seerr"}""",
            )
        repeat(SETTINGS_READS) { seerr.enqueue(settings) }

        runBlocking {
            assertEquals(SERVER_KEY, factory.cached(baseUrl, SeerrAuth.ApiKey(CLIENT_KEY)).mainSettings().apiKey)
            factory.probe(baseUrl, SeerrAuth.ApiKey(CLIENT_KEY)) { it.mainSettings() }
            factory.login(baseUrl) { it.mainSettings() }
            factory.anonymous(baseUrl) { it.mainSettings() }
        }

        assertTrue("logging is on, so the requests are there", logged.any { it.contains("/api/v1/settings/main") })
        assertFalse(logged.joinToString("\n"), logged.any { SERVER_KEY in it })
        assertFalse("the credential header is redacted", logged.any { CLIENT_KEY in it })
    }

    @Test
    fun `the Quick Connect secret and a Pushover token never reach the request line`() {
        val baseUrl = seerr.url("/").toString()
        val json = headersOf("Content-Type", "application/json")
        seerr.enqueue(MockResponse(code = 200, headers = json, body = """{"authenticated":false}"""))
        seerr.enqueue(MockResponse(code = 200, headers = json, body = "[]"))

        runBlocking {
            factory.anonymous(baseUrl) { it.checkQuickConnect(QC_SECRET) }
            factory.cached(baseUrl, SeerrAuth.ApiKey(CLIENT_KEY)).pushoverSounds(PUSHOVER_TOKEN)
        }

        val text = logged.joinToString("\n")
        assertTrue(text, "quickconnect/check" in text && "pushover/sounds" in text)
        assertFalse(text, QC_SECRET in text)
        assertFalse(text, PUSHOVER_TOKEN in text)
    }

    private companion object {
        const val QC_SECRET = "qc-secret-91ab"
        const val PUSHOVER_TOKEN = "pushover-token-5d2e"

        /** One for each client the factory builds: cached, probe, login and anonymous. */
        const val SETTINGS_READS = 4
    }
}
