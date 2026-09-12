package io.github.scottcooper92.binge.seerr.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class SeerrConnectionHealthMonitorTest {
    private val monitor = SeerrConnectionHealthMonitor()

    @Test
    fun `starts not connected, and a success is healthy`() {
        assertEquals(SeerrConnectionHealth.NotConnected, monitor.health.value)
        monitor.reportSuccess()
        assertEquals(SeerrConnectionHealth.Healthy, monitor.health.value)
    }

    @Test
    fun `an auth failure flips to unauthorized at once`() {
        monitor.reportAuthFailure()
        assertEquals(SeerrConnectionHealth.Unauthorized, monitor.health.value)
    }

    @Test
    fun `one network failure is a blip, two in a row are unreachable`() {
        monitor.reportSuccess()
        monitor.reportNetworkFailure()
        assertEquals(SeerrConnectionHealth.Healthy, monitor.health.value)
        monitor.reportNetworkFailure()
        assertEquals(SeerrConnectionHealth.Unreachable, monitor.health.value)
    }

    @Test
    fun `a success between network failures resets the streak`() {
        monitor.reportNetworkFailure()
        monitor.reportSuccess()
        monitor.reportNetworkFailure()
        assertEquals(SeerrConnectionHealth.Healthy, monitor.health.value)
    }

    @Test
    fun `connecting starts clean, and resetting forgets everything`() {
        monitor.reportNetworkFailure()
        monitor.reportNetworkFailure()
        monitor.onConnected()
        assertEquals(SeerrConnectionHealth.Healthy, monitor.health.value)
        monitor.reportNetworkFailure()
        monitor.reset()
        monitor.reportNetworkFailure()
        assertEquals(SeerrConnectionHealth.NotConnected, monitor.health.value)
    }
}
