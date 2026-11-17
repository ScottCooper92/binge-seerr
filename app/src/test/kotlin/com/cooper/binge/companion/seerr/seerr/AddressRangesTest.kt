package com.cooper.binge.companion.seerr.seerr

import com.cooper.homelab.core.server.network.AddressLocality
import com.cooper.homelab.core.server.network.hostLocality
import com.cooper.homelab.core.server.network.isLocalNetworkHost
import com.cooper.homelab.core.server.network.isLocalOrPrivateHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three host classifiers read the kit's one table of ranges (#992). They still disagree on which ranges count, on
 * purpose; these are the cases where two copies of one range used to disagree on what the range was. The ranges' own
 * edges and the literal parser are the kit's tests now.
 */
class AddressRangesTest {
    @Test
    fun `link-local is all of fe80 slash 10, for all three`() {
        assertEquals(AddressLocality.Local, hostLocality("fe90::1"))
        assertTrue(isLocalNetworkHost("fe90::1"))
        assertTrue(isLocalOrPrivateHost("fe90::1"))
        assertFalse(isLocalNetworkHost("fec0::1"))
    }

    @Test
    fun `a short literal that starts with fd is not unique-local, for any of them`() {
        // fd::1 is 00fd::1.
        assertEquals(AddressLocality.NotLocal, hostLocality("fd::1"))
        assertFalse(isLocalNetworkHost("fd::1"))
        assertFalse(isLocalOrPrivateHost("fd::1"))
    }

    @Test
    fun `an IPv4-mapped literal is the IPv4 address it maps, for all three`() {
        // The hex form of [::ffff:192.168.1.2], as a host written by hand may carry it.
        val mapped = "::ffff:c0a8:102"
        assertEquals(AddressLocality.Local, hostLocality(mapped))
        assertTrue(isLocalNetworkHost(mapped))
        assertTrue(isLocalOrPrivateHost(mapped))
        // OkHttp itself writes the mapped literal as the IPv4 address.
        assertEquals("192.168.1.2", "http://[::ffff:192.168.1.2]:5055".localNetworkHostOrNull())
    }
}
