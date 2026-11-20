package com.cooper.binge.companion.seerr.telemetry

import com.cooper.binge.companion.seerr.handoff.HandOffCredentials
import com.cooper.binge.companion.seerr.handoff.HandOffKey
import com.cooper.binge.companion.seerr.handoff.OkHttpAddressSender
import com.cooper.binge.companion.seerr.handoff.OkHttpTvSignInClient
import com.cooper.binge.companion.seerr.handoff.TvHandOffTarget
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.homelab.core.diagnostics.Redactor
import com.cooper.homelab.core.diagnostics.RingLog
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.foundation.LogField
import com.cooper.homelab.core.foundation.LogLevel
import com.cooper.homelab.core.foundation.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Instant

private const val ADDRESS = "https://seerr.home.example:5055"
private const val HOST = "seerr.home.example:5055"
private const val BARE_HOST = "seerr.home.example"
private const val API_KEY = "k3y-0123456789abcdef"
private const val USER = "alice-the-requester"

/**
 * The redaction canary of docs/design/logging.md section 6, over this app's own log calls: whatever the saved connection
 * holds is registered with the redactor, a failing call is driven, and none of it may be in the log or in the text shared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoggingCanaryTest {
    private val redactor = Redactor()
    private val ring =
        RingLog(
            Clock { Instant.fromEpochMilliseconds(0) },
            redactor,
            serverOrder = { emptyList() },
            ownPackages = listOf("com.cooper.binge."),
        )

    /** A client whose every call fails the way a real one can: with a message that quotes what it was sent. */
    private val failing =
        OkHttpClient
            .Builder()
            .addInterceptor(Interceptor { throw IOException("unable to reach $ADDRESS/api?apikey=$API_KEY for $USER") })
            .build()

    private val target = TvHandOffTarget(host = "192.168.1.9", port = 8080, token = "abcdefghijklmnopqrstuv")

    private fun seedRedactor() {
        redactor.followCredentials(
            MutableStateFlow(SeerrCredentials(ADDRESS, SeerrAuth.ApiKey(API_KEY))),
            CoroutineScope(UnconfinedTestDispatcher()),
        )
        redactor.register(USER)
    }

    @Test
    fun `a failing send of the address leaves no address, key or name in the log`() =
        runTest {
            seedRedactor()

            OkHttpAddressSender(failing, UnconfinedTestDispatcher(testScheduler), ring).send(target, ADDRESS, sealed = null)

            assertClean()
            val entry = ring.entries.value.single()
            assertEquals("sending the address failed", entry.event)
            assertEquals(LogLevel.Warn, entry.level)
        }

    @Test
    fun `a failing status read and credential send leave nothing in the log`() =
        runTest {
            seedRedactor()
            val client = OkHttpTvSignInClient(failing, UnconfinedTestDispatcher(testScheduler), ring)
            val credentials = HandOffCredentials(mode = "apiKey", apiKey = API_KEY, username = USER)

            client.status(target)
            client.send(target.copy(key = HandOffKey.generate()), ADDRESS, credentials)

            assertClean()
            assertEquals(2, ring.entries.value.size)
        }

    @Test
    fun `a status code is kept as a number and the event stays constant`() =
        runTest {
            ring.warn("t", "the TV refused the address", listOf(LogField.Status("status", 404)))

            assertTrue(ring.render().contains("status=404"))
        }

    @Test
    fun `the saved connection's literals follow it when it changes and go when it is cleared`() =
        runTest {
            val credentials = MutableStateFlow<SeerrCredentials?>(SeerrCredentials(ADDRESS, SeerrAuth.ApiKey(API_KEY)))
            redactor.followCredentials(credentials, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            assertEquals("a [redacted] b [redacted] c", redactor.redact("a $HOST b $API_KEY c"))

            credentials.value = SeerrCredentials("https://other.example", SeerrAuth.Session("cookie-value-1", userId = 1))
            assertEquals("a $HOST b $API_KEY c", redactor.redact("a $HOST b $API_KEY c"))
            assertEquals("[redacted] [redacted]", redactor.redact("https://other.example cookie-value-1"))

            credentials.value = null
            assertEquals("https://other.example", redactor.redact("https://other.example"))
        }

    @Test
    fun `a short host name is redacted as a whole token only, bare or with its port`() =
        runTest {
            val credentials = MutableStateFlow<SeerrCredentials?>(SeerrCredentials("http://jellyfin:5055/", SeerrAuth.ApiKey(API_KEY)))
            redactor.followCredentials(credentials, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            assertEquals("[redacted] timed out", redactor.redact("jellyfin timed out"))
            assertEquals("to [redacted] failed", redactor.redact("to jellyfin:5055 failed"))
            assertEquals("JellyfinSetup and mediajellyfin", redactor.redact("JellyfinSetup and mediajellyfin"))

            credentials.value = null
            assertEquals("jellyfin timed out", redactor.redact("jellyfin timed out"))
        }

    @Test
    fun `a literal another part registered stays replaced when the saved connection goes`() =
        runTest {
            val credentials = MutableStateFlow<SeerrCredentials?>(SeerrCredentials("http://jellyfin:5055/", SeerrAuth.ApiKey(API_KEY)))
            redactor.registerWholeToken("jellyfin")
            redactor.followCredentials(credentials, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
            assertEquals("[redacted] timed out", redactor.redact("jellyfin timed out"))

            credentials.value = null

            assertEquals("[redacted] timed out", redactor.redact("jellyfin timed out"))
            assertEquals("the key $API_KEY", redactor.redact("the key $API_KEY"))
        }

    @Test
    fun `the bare host of a saved address does not reach the log`() =
        runTest {
            seedRedactor()

            ring.warn("t", "failed", listOf(LogField.Text("note", "could not reach $BARE_HOST just now")))

            assertClean()
            assertTrue(ring.render().contains("could not reach [redacted] just now"))
        }

    private fun assertClean() {
        val shared = ring.render()
        listOf(ADDRESS, HOST, BARE_HOST, API_KEY, USER).forEach { secret ->
            assertFalse("$secret reached the log", shared.contains(secret, ignoreCase = true))
        }
    }

    private fun Logger.warn(
        tag: String,
        event: String,
        fields: List<LogField>,
    ) = log(LogLevel.Warn, tag, event, fields, null)
}
