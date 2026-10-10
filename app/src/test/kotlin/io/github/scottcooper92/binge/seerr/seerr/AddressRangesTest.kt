package io.github.scottcooper92.binge.seerr.seerr

import io.github.scottcooper92.binge.seerr.handoff.AddressLocality
import io.github.scottcooper92.binge.seerr.handoff.hostLocality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three host classifiers read one table of ranges (#992). They still disagree on which ranges count, on purpose;
 * these are the cases where two copies of one range used to disagree on what the range was.
 */
class AddressRangesTest {
    @Test
    fun `link-local is all of fe80 slash 10, for all three`() {
        assertEquals(AddressLocality.Local, hostLocality("fe90::1"))
        assertTrue("fe90::1".isLocalNetworkHost())
        assertTrue("fe90::1".isLocalOrPrivateHost())
        assertFalse("fec0::1".isLocalNetworkHost())
    }

    @Test
    fun `a short literal that starts with fd is not unique-local, for any of them`() {
        // fd::1 is 00fd::1.
        assertEquals(AddressLocality.NotLocal, hostLocality("fd::1"))
        assertFalse("fd::1".isLocalNetworkHost())
        assertFalse("fd::1".isLocalOrPrivateHost())
    }

    @Test
    fun `an IPv4-mapped literal is the IPv4 address it maps, for all three`() {
        // The hex form of [::ffff:192.168.1.2], as a host written by hand may carry it.
        val mapped = "::ffff:c0a8:102"
        assertEquals(AddressLocality.Local, hostLocality(mapped))
        assertTrue(mapped.isLocalNetworkHost())
        assertTrue(mapped.isLocalOrPrivateHost())
        // OkHttp itself writes the mapped literal as the IPv4 address.
        assertEquals("192.168.1.2", "http://[::ffff:192.168.1.2]:5055".localNetworkHostOrNull())
    }

    @Test
    fun `the ranges hold their edges`() {
        assertTrue(checkNotNull(ipLiteralBytes("172.31.255.255")) in AddressRange.Private)
        assertFalse(checkNotNull(ipLiteralBytes("172.32.0.0")) in AddressRange.Private)
        assertTrue(checkNotNull(ipLiteralBytes("100.127.255.255")) in AddressRange.Cgnat)
        assertFalse(checkNotNull(ipLiteralBytes("100.128.0.0")) in AddressRange.Cgnat)
        assertTrue(checkNotNull(ipLiteralBytes("[fd7a:115c:a1e0::1]")) in AddressRange.Tailscale)
        assertTrue(checkNotNull(ipLiteralBytes("fd7a:115c:a1e0::1")) in AddressRange.UniqueLocal)
    }

    @Test
    fun `a name, or a number that is not an address, is not an IP literal`() {
        assertNull(ipLiteralBytes("seerr.lan"))
        assertNull(ipLiteralBytes("300.1.1.1"))
        assertNull(ipLiteralBytes("nas"))
    }
}
