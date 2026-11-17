package com.cooper.binge.companion.seerr.telemetry

import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.telemetry.AnalyticsConsentGate
import com.cooper.homelab.core.telemetry.GatedAnalytics
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import com.cooper.homelab.core.testing.FakeAnalyticsClient
import com.cooper.homelab.core.testing.InMemoryDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAnalyticsDecoratorTest {
    private val client = FakeAnalyticsClient()
    private val prefs = TelemetryPrefs(InMemoryDataStore(), crashReportingDefault = true)

    private class FixedDemoSession(
        override val isDemo: Boolean,
        private val read: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    ) : DemoSession {
        override suspend fun awaitRead() = read.await()
    }

    private fun TestScope.scope() = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.analytics(
        client: FakeAnalyticsClient,
        demo: DemoSession,
    ) = GatedAnalytics(
        client,
        AnalyticsConsentGate(prefs, scope(), Logger { _, _, _ -> }),
        DemoAnalyticsDecorator(demo),
        scope(),
    )

    @Test
    fun `decorate adds demo only for a demo session`() {
        val properties = mapOf<String, Any>("action" to "approve")
        assertEquals(
            mapOf("action" to "approve", "demo" to true),
            DemoAnalyticsDecorator(FixedDemoSession(isDemo = true)).decorate(properties),
        )
        assertEquals(properties, DemoAnalyticsDecorator(FixedDemoSession(isDemo = false)).decorate(properties))
    }

    @Test
    fun `awaitReady suspends until the connection has been read`() =
        runTest {
            val read = CompletableDeferred<Unit>()
            val decorator = DemoAnalyticsDecorator(FixedDemoSession(isDemo = true, read = read))
            val ready = CompletableDeferred<Unit>()
            val job =
                scope().launch {
                    decorator.awaitReady()
                    ready.complete(Unit)
                }
            assertFalse(ready.isCompleted)
            read.complete(Unit)
            job.join()
            assertTrue(ready.isCompleted)
        }

    @Test
    fun `a demo session marks its events and screens, and a real one does not`() =
        runTest {
            prefs.setAnalyticsGranted(true)
            val demo = analytics(client, FixedDemoSession(isDemo = true))
            demo.screen("requests")
            demo.event("request_moderated", mapOf("action" to "approve"))
            assertEquals(
                listOf("screen:requests:{demo=true}", "event:request_moderated:{action=approve, demo=true}"),
                client.calls.drop(1),
            )

            val real = FakeAnalyticsClient()
            analytics(real, FixedDemoSession(isDemo = false)).apply {
                screen("requests")
                event("request_moderated", mapOf("action" to "approve"))
            }
            assertEquals(listOf("screen:requests", "event:request_moderated:{action=approve}"), real.calls.drop(1))
        }

    @Test
    fun `a screen held from a cold start waits for the connection to be read before it is marked`() =
        runTest {
            prefs.setAnalyticsGranted(true)
            val read = CompletableDeferred<Unit>()
            analytics(client, FixedDemoSession(isDemo = true, read = read)).screen("hub")
            assertFalse(client.calls.any { it.startsWith("screen:") })

            read.complete(Unit)
            assertEquals("screen:hub:{demo=true}", client.calls.last())
        }
}
