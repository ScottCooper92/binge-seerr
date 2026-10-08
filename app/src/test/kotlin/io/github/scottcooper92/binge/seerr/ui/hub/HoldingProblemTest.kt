package io.github.scottcooper92.binge.seerr.ui.hub

import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import org.junit.Assert.assertEquals
import org.junit.Test

/** A problem the hub named stays named, and marked rechecking, through the re-check meant to clear it (#873). */
class HoldingProblemTest {
    private fun ready(health: ConnectionHealth) =
        HubUiState.Ready(
            server = HubServer("http://seerr.lan", "Family", SeerrVariant.Seerr, "3.4.0", updateAvailable = false, commitsBehind = 0),
            health = health,
            overview = HubOverview(loaded = true),
            downloading = emptyList(),
            bingeStatus = BingeStatus.Connected,
        )

    @Test
    fun `a re-check's Checking keeps the problem it set out to clear`() {
        val held = holdingProblem(shown = ready(ConnectionHealth.Unreachable), fresh = ready(ConnectionHealth.Checking))

        assertEquals(ready(ConnectionHealth.Unreachable).copy(rechecking = true), held)
    }

    @Test
    fun `a re-check from the error page stays on it, rechecking, while the hub reloads`() {
        val error = HubUiState.Error(ConnectionHealth.Unreachable)

        assertEquals(error.copy(rechecking = true), holdingProblem(shown = error, fresh = HubUiState.Loading))
        assertEquals(
            ready(ConnectionHealth.Unreachable).copy(rechecking = true),
            holdingProblem(shown = error, fresh = ready(ConnectionHealth.Checking)),
        )
    }

    @Test
    fun `the re-check's own answer is what shows next, a fix or a new problem`() {
        val shown = ready(ConnectionHealth.Unreachable).copy(rechecking = true)

        assertEquals(ready(ConnectionHealth.Healthy), holdingProblem(shown, ready(ConnectionHealth.Healthy)))
        assertEquals(ready(ConnectionHealth.CouldNotLoad), holdingProblem(shown, ready(ConnectionHealth.CouldNotLoad)))
    }

    @Test
    fun `a cold start has nothing to hold, so it still shows the remembered hub while it checks`() {
        assertEquals(ready(ConnectionHealth.Checking), holdingProblem(shown = HubUiState.Loading, fresh = ready(ConnectionHealth.Checking)))
        assertEquals(
            ready(ConnectionHealth.Checking),
            holdingProblem(shown = ready(ConnectionHealth.Healthy), fresh = ready(ConnectionHealth.Checking)),
        )
    }
}
