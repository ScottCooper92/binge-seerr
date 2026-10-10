package io.github.scottcooper92.binge.seerr.ui.hub

import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Requests badge counts only what the viewer's own Requests screen lists (#980). */
class HubSectionBadgeTest {
    @Test
    fun `a viewer who sees every request gets the server's pending count`() {
        val overview = HubOverview(permissions = SeerrPermissions(canViewRequests = true), pendingRequestCount = 3)

        assertEquals(3, HubSection.Requests.badgeCount(overview))
    }

    @Test
    fun `a viewer whose list is theirs alone gets no badge`() {
        val overview = HubOverview(permissions = SeerrPermissions(canRequestMovie = true), pendingRequestCount = 3)

        assertNull(HubSection.Requests.badgeCount(overview))
    }
}
