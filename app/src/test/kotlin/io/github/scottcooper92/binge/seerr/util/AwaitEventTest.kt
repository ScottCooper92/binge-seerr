package io.github.scottcooper92.binge.seerr.util

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AwaitEventTest {
    @Test
    fun `the helper has subscribed by the time it returns, so the next emission reaches it`() =
        runTest {
            val events = MutableSharedFlow<String>(extraBufferCapacity = 1)

            val awaited = awaitEvent(events)

            assertEquals(1, events.subscriptionCount.value)
            events.emit("saved")
            assertEquals("saved", awaited.await())
        }

    /** The bug the helper exists for: a queued collector has registered nothing, so the emission is dropped. */
    @Test
    fun `a plain async has not subscribed, and the emission it was waiting for is lost`() =
        runTest {
            val events = MutableSharedFlow<String>(extraBufferCapacity = 1)

            val queued = async { events.first() }
            events.emit("saved")

            assertEquals(0, events.subscriptionCount.value)
            queued.cancel()
        }
}
