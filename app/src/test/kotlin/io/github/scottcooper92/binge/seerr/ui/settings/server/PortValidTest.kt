package io.github.scottcooper92.binge.seerr.ui.settings.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The port range every settings form shares, asked directly rather than through a form. */
class PortValidTest {
    @Test
    fun `the ends of the range are ports`() {
        assertTrue(portValid("1"))
        assertTrue(portValid("65535"))
    }

    @Test
    fun `just outside the range is not a port`() {
        assertFalse(portValid("0"))
        assertFalse(portValid("65536"))
    }

    @Test
    fun `blank and non-numeric text is not a port`() {
        assertFalse(portValid(""))
        assertFalse(portValid("   "))
        assertFalse(portValid("http"))
        assertFalse(portValid("80a"))
        assertFalse(portValid("8.0"))
    }

    @Test
    fun `surrounding spaces are trimmed`() {
        assertTrue(portValid(" 8080 "))
    }

    @Test
    fun `a host and port need both`() {
        assertTrue(hostAndPortValid("nas", "8989"))
        assertFalse(hostAndPortValid(" ", "8989"))
        assertFalse(hostAndPortValid("nas", "0"))
    }
}
