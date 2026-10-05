package io.github.scottcooper92.binge.seerr.ui.hub

import io.github.scottcooper92.binge.seerr.seerr.LocalNetworkPermission
import org.junit.Assert.assertEquals
import org.junit.Test

class HubLocalNetworkHealthTest {
    private val refused = LocalNetworkPermission { false }
    private val granted = LocalNetworkPermission { true }

    @Test
    fun `only an unreachable local server becomes the permission`() {
        assertEquals(
            ConnectionHealth.LocalNetworkDenied,
            ConnectionHealth.Unreachable.orLocalNetworkDenied("http://192.168.1.10:5055/", refused),
        )
        assertEquals(ConnectionHealth.Unreachable, ConnectionHealth.Unreachable.orLocalNetworkDenied("http://192.168.1.10:5055/", granted))
        assertEquals(ConnectionHealth.Unreachable, ConnectionHealth.Unreachable.orLocalNetworkDenied("https://seerr.example.com/", refused))
        assertEquals(ConnectionHealth.Unreachable, ConnectionHealth.Unreachable.orLocalNetworkDenied(null, refused))
    }

    @Test
    fun `every other health is left alone, even on a refused local server`() {
        listOf(ConnectionHealth.Healthy, ConnectionHealth.Checking, ConnectionHealth.CouldNotLoad, ConnectionHealth.Unauthorized)
            .forEach { assertEquals(it, it.orLocalNetworkDenied("http://seerr.lan/", refused)) }
    }
}
