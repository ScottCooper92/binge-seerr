package io.github.scottcooper92.binge.seerr.ui.settings.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The API request timeout's entry, in either of the app's languages (#890). */
class TimeoutSecondsTest {
    @Test
    fun `either decimal separator reads as the same seconds`() {
        assertEquals(1500L, "1.5".toTimeoutMillis())
        assertEquals(1500L, "1,5".toTimeoutMillis())
        assertEquals(30000L, " 30 ".toTimeoutMillis())
    }

    @Test
    fun `the draft keeps the point, and the display takes the device's separator`() {
        assertEquals("1.5", "1,5".canonicalSeconds())
        assertEquals("1,5", "1.5".withDecimalSeparator(','))
        assertEquals("1.5", "1.5".withDecimalSeparator('.'))
    }

    @Test
    fun `a grouped, doubled or negative number is not seconds`() {
        assertNull("1,000.5".toTimeoutMillis())
        assertNull("1.5.0".toTimeoutMillis())
        assertFalse("-1".isTimeoutSeconds())
    }
}
