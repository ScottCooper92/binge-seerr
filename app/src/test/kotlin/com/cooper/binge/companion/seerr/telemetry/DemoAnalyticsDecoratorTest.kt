package com.cooper.binge.companion.seerr.telemetry

import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.telemetry.AnalyticsConsentGate
import com.cooper.homelab.core.telemetry.DemoAnalyticsDecorator
import com.cooper.homelab.core.telemetry.DemoSession
import com.cooper.homelab.core.telemetry.GatedAnalytics
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import com.cooper.homelab.core.testing.FakeAnalyticsClient
import com.cooper.homelab.core.testing.InMemoryDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The `demo=true` property is one of the few places the app tells the demo apart, so it stays pinned here. */
class DemoAnalyticsDecoratorTest {
    private val client = FakeAnalyticsClient()
    private val prefs = TelemetryPrefs(InMemoryDataStore(), crashReportingDefault = true)

    private fun TestScope.session(
        isDemo: Boolean,
        read: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    ) = DemoSession(
        flow {
            read.await()
            emit(isDemo)
        },
        scope(),
    )

    private fun TestScope.scope() = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))

    private fun TestScope.analytics(
        demo: DemoSession,
        client: FakeAnalyticsClient = this@DemoAnalyticsDecoratorTest.client,
    ) = GatedAnalytics(
        client,
        AnalyticsConsentGate(prefs, scope(), Logger { _, _, _, _, _ -> }),
        DemoAnalyticsDecorator(demo),
        scope(),
    )

    @Test
    fun `a demo session marks its properties and a real one leaves them alone`() =
        runTest {
            val properties = mapOf<String, Any>("action" to "approve")
            assertEquals(
                mapOf("action" to "approve", "demo" to true),
                DemoAnalyticsDecorator(session(isDemo = true)).decorate(properties),
            )
            assertEquals(properties, DemoAnalyticsDecorator(session(isDemo = false)).decorate(properties))
            assertEquals(mapOf("demo" to true), DemoAnalyticsDecorator(session(isDemo = true)).decorate(emptyMap()))
        }

    @Test
    fun `it is ready only once the saved connection has been read`() =
        runTest {
            val read = CompletableDeferred<Unit>()
            val decorator = DemoAnalyticsDecorator(session(isDemo = true, read = read))
            val ready = CompletableDeferred<Unit>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                decorator.awaitReady()
                ready.complete(Unit)
            }
            assertFalse(ready.isCompleted)
            read.complete(Unit)
            assertEquals(true, ready.isCompleted)
        }

    @Test
    fun `a demo session marks its events and screens and a real one does not`() =
        runTest {
            prefs.setAnalyticsGranted(true)
            val demo = analytics(session(isDemo = true))
            demo.screen("requests")
            demo.event("request_moderated", mapOf("action" to "approve"))
            assertEquals(
                listOf("screen:requests:{demo=true}", "event:request_moderated:{action=approve, demo=true}"),
                client.calls.drop(1),
            )

            val real = FakeAnalyticsClient()
            analytics(session(isDemo = false), real).apply {
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
            analytics(session(isDemo = true, read = read)).screen("hub")
            assertFalse(client.calls.any { it.startsWith("screen:") })

            read.complete(Unit)
            assertEquals("screen:hub:{demo=true}", client.calls.last())
        }
}
