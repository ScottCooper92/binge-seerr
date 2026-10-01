package io.github.scottcooper92.binge.seerr.ui.settings.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsCacheFormTest {
    private fun cache(
        min: String,
        max: String,
        enabled: Boolean = true,
    ) = DnsCacheForm(enabled = enabled, minTtl = min, maxTtl = max)

    @Test
    fun `a minimum above the maximum is invalid`() {
        assertFalse(cache("600", "60").valid)
        assertFalse(cache("600", "60").orderValid)
    }

    @Test
    fun `a minimum one above the maximum is invalid and equal bounds are valid`() {
        assertFalse(cache("61", "60").valid)
        assertTrue(cache("60", "60").valid)
        assertTrue(cache("59", "60").valid)
    }

    @Test
    fun `a blank bound is no bound so order is not compared`() {
        assertTrue(cache("", "60").valid)
        assertTrue(cache("600", "").valid)
        assertTrue(cache("", "").valid)
    }

    @Test
    fun `surrounding spaces do not hide a crossed pair`() = assertFalse(cache(" 600 ", " 60 ").valid)

    @Test
    fun `a crossed pair does not block saving while the cache is off`() = assertTrue(cache("600", "60", enabled = false).valid)

    @Test
    fun `a crossed pair makes the whole network form invalid`() = assertFalse(NetworkForm(dnsCache = cache("600", "60")).valid)
}
