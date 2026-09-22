package io.github.scottcooper92.binge.seerr.telemetry

import io.github.scottcooper92.binge.seerr.util.InMemoryDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostHogAnalyticsTest {
    /** Records every call in order, so a test can say what reached the SDK and when. */
    private class RecordingClient : AnalyticsClient {
        val calls = mutableListOf<String>()

        override fun optIn() {
            calls += "optIn"
        }

        override fun optOut() {
            calls += "optOut"
        }

        override fun screen(name: String) {
            calls += "screen:$name"
        }
    }

    private val client = RecordingClient()
    private val prefs = TelemetryPrefs(InMemoryDataStore())

    private fun TestScope.gate() =
        AnalyticsConsentGate(prefs, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun `nothing is sent while undecided, and the client is closed against any opt-in it kept`() =
        runTest {
            PostHogAnalytics(client, gate()).screen("requests")
            assertEquals(listOf("optOut"), client.calls)
        }

    @Test
    fun `a grant opens the client before the first screen, and a decline closes it again`() =
        runTest {
            val analytics = PostHogAnalytics(client, gate())
            prefs.setAnalyticsGranted(true)
            analytics.screen("requests")
            prefs.setAnalyticsGranted(false)
            analytics.screen("settings")
            assertEquals(listOf("optOut", "optIn", "screen:requests", "optOut"), client.calls)
        }

    @Test
    fun `an install that already agreed is opened at start, not closed first`() =
        runTest {
            prefs.setAnalyticsGranted(true)
            PostHogAnalytics(client, gate()).screen("hub")
            assertEquals(listOf("optIn", "screen:hub"), client.calls)
        }

    @Test
    fun `a build with no project key sends nothing and does not fail`() =
        runTest {
            prefs.setAnalyticsGranted(true)
            PostHogAnalytics(null, gate()).screen("hub")
        }

    @Test
    fun `the config starts opted out, with the SDK's own screen and deep link capture off`() {
        val config = postHogConfig("key", "https://eu.i.posthog.com")
        assertTrue(config.optOut)
        assertFalse(config.captureScreenViews)
        assertFalse(config.captureDeepLinks)
    }
}
