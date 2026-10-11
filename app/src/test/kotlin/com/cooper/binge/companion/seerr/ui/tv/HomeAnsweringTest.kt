package com.cooper.binge.companion.seerr.ui.tv

import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.hub.ConnectionHealth
import com.cooper.binge.companion.seerr.ui.hub.HubOverview
import com.cooper.binge.companion.seerr.ui.hub.HubServer
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAnsweringTest {
    private fun ready(health: ConnectionHealth) =
        HubUiState.Ready(
            server = HubServer("https://seerr.test", "Seerr", SeerrVariant.Seerr, null, false, 0),
            health = health,
            overview = HubOverview(),
            downloading = emptyList(),
            bingeStatus = BingeStatus.NotInstalled,
        )

    @Test
    fun `a re-probe of a failing server never swaps Home to the requests`() {
        var answering = false
        listOf(ConnectionHealth.Unreachable, ConnectionHealth.Checking, ConnectionHealth.Unreachable).forEach {
            answering = homeAnswering(ready(it), answering)
            assertFalse("health $it", answering)
        }
    }

    @Test
    fun `a re-probe of a healthy server keeps the requests`() {
        var answering = homeAnswering(ready(ConnectionHealth.Healthy), false)
        assertTrue(answering)
        answering = homeAnswering(ready(ConnectionHealth.Checking), answering)
        assertTrue(answering)
    }

    @Test
    fun `a rejected session is not an answer`() {
        assertFalse(homeAnswering(ready(ConnectionHealth.Unauthorized), true))
        assertFalse(homeAnswering(ready(ConnectionHealth.Unauthorized), false))
    }

    @Test
    fun `a server that stops answering returns Home to the problem`() {
        assertFalse(homeAnswering(ready(ConnectionHealth.Unreachable), true))
        assertFalse(homeAnswering(HubUiState.Error(ConnectionHealth.Unreachable), true))
        assertFalse(homeAnswering(HubUiState.Loading, true))
    }
}
