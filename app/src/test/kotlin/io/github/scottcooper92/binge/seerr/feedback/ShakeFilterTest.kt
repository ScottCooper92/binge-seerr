package io.github.scottcooper92.binge.seerr.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

private const val MS = 1_000_000L
private const val STILL = 9.81f
private const val JOLT = 60f

class ShakeFilterTest {
    /** Samples every 50 ms, alternating a hard jolt with rest, starting at [startMs]. */
    private fun ShakeFilter.shake(
        jolts: Int,
        startMs: Long = 0,
    ): List<Boolean> =
        (0 until jolts * 2).map { i ->
            val x = if (i % 2 == 0) JOLT else -JOLT
            onSample(x, 0f, STILL, (startMs + i * 50) * MS)
        }

    @Test
    fun `a phone held still never shakes`() {
        val filter = ShakeFilter()
        assertFalse((0 until 200).any { filter.onSample(0f, 0f, STILL, it * 20 * MS) })
    }

    @Test
    fun `three hard jolts inside the window are one shake`() {
        val filter = ShakeFilter()
        assertEquals(1, filter.shake(jolts = 3).count { it })
    }

    @Test
    fun `a sustained shake fires once, then again only after the debounce`() {
        val filter = ShakeFilter()
        assertEquals(1, filter.shake(jolts = 8).count { it })
        assertEquals(1, filter.shake(jolts = 3, startMs = 3_000).count { it })
    }

    @Test
    fun `jolts too far apart are not a shake`() {
        val filter = ShakeFilter()
        val fired = (0 until 3).map { filter.onSample(JOLT, 0f, STILL, it * 1_000 * MS) }
        assertFalse(fired.any { it })
    }

    @Test
    fun `a reset forgets the debounce`() {
        val filter = ShakeFilter()
        filter.shake(jolts = 3)
        filter.reset()
        assertEquals(1, filter.shake(jolts = 3, startMs = 100).count { it })
    }
}
