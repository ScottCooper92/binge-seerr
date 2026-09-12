package io.github.scottcooper92.binge.seerr.ui.hub

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The bound the loop fires before handing over to the manual Retry. */
private const val EXPECTED_AUTO_RETRIES = 5

/** Well past the backoff ceiling times the budget, so every scheduled retry has fired. */
private const val DRAIN_MS = 5L * 60 * 1000

class HubAutoRetryTest {
    private fun TestScope.autoRetry(
        health: MutableStateFlow<ConnectionHealth>,
        visible: MutableStateFlow<Boolean> = MutableStateFlow(true),
        retry: () -> Unit,
    ) = HubAutoRetry(scope = backgroundScope, health = health, visible = visible, retry = retry)

    @Test
    fun `retries on a bounded backoff while unreachable or not loaded, and never while healthy or hidden`() =
        runTest {
            var retries = 0
            autoRetry(MutableStateFlow(ConnectionHealth.Healthy)) { retries++ }
            runCurrent()
            advanceTimeBy(DRAIN_MS)
            runCurrent()
            assertEquals(0, retries)

            autoRetry(MutableStateFlow(ConnectionHealth.Unreachable)) { retries++ }
            runCurrent()
            advanceTimeBy(DRAIN_MS)
            runCurrent()
            assertEquals(EXPECTED_AUTO_RETRIES, retries)

            autoRetry(MutableStateFlow(ConnectionHealth.CouldNotLoad), visible = MutableStateFlow(false)) { retries++ }
            runCurrent()
            advanceTimeBy(DRAIN_MS)
            runCurrent()
            assertEquals(EXPECTED_AUTO_RETRIES, retries)
        }

    @Test
    fun `recovering, or leaving the screen, stops the loop at once`() =
        runTest {
            var retries = 0
            val health = MutableStateFlow(ConnectionHealth.Unreachable)
            autoRetry(health) {
                retries++
                health.value = ConnectionHealth.Healthy
            }
            runCurrent()
            advanceTimeBy(DRAIN_MS)
            runCurrent()
            assertEquals(1, retries)

            val visible = MutableStateFlow(true)
            autoRetry(MutableStateFlow(ConnectionHealth.Unreachable), visible = visible) {
                retries++
                visible.value = false
            }
            runCurrent()
            advanceTimeBy(DRAIN_MS)
            runCurrent()
            assertEquals(2, retries)
        }

    @Test
    fun `a probe's transient Checking does not reset the budget`() =
        runTest {
            var retries = 0
            val health = MutableStateFlow(ConnectionHealth.Unreachable)
            autoRetry(health) {
                retries++
                health.value = ConnectionHealth.Checking
                health.value = ConnectionHealth.Unreachable
            }
            runCurrent()
            advanceTimeBy(DRAIN_MS)
            runCurrent()
            assertEquals(EXPECTED_AUTO_RETRIES, retries)
        }
}
