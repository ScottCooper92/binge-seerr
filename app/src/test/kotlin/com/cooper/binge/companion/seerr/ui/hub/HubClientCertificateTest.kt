package com.cooper.binge.companion.seerr.ui.hub

import com.cooper.binge.companion.seerr.auth.SeerrConnectionHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hub's problem page for a server that asks for a client certificate (#2156). */
class HubClientCertificateTest {
    @Test
    fun `the connection's reading becomes the hub's own problem`() {
        assertEquals(ConnectionHealth.ClientCertificateNeeded, SeerrConnectionHealth.ClientCertificateNeeded.toConnectionHealth())
    }

    @Test
    fun `it is a problem the hub shows in place of its dashboard, and one it retries`() {
        assertTrue(ConnectionHealth.ClientCertificateNeeded.isProblem())
        assertFalse(ConnectionHealth.ClientCertificateNeeded.showsDashboard())
    }
}
