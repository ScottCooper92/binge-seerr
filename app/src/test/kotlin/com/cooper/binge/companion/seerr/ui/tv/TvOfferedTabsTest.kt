package com.cooper.binge.companion.seerr.ui.tv

import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.ui.hub.HubOverview
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which tabs the bar offers: Home always, and each other tab by its own rule over what the server has said of the account. */
class TvOfferedTabsTest {
    @Test
    fun `before the server has said anything only the start tab is offered`() {
        assertEquals(listOf(TvDestination.Hub), offeredTabs(overview = null))
    }

    @Test
    fun `an account the server lets see issues, on a server that has them, gets the Issues tab`() {
        val overview = HubOverview(hasIssues = true, permissions = SeerrPermissions(canViewIssues = true))

        assertEquals(listOf(TvDestination.Hub, TvDestination.Issues), offeredTabs(overview))
    }

    @Test
    fun `an account the server will not let see issues gets Home alone`() {
        val overview = HubOverview(hasIssues = true, permissions = SeerrPermissions())

        assertEquals(listOf(TvDestination.Hub), offeredTabs(overview))
    }

    /** A lineage without issues offers no Issues tab, whatever the account may do. */
    @Test
    fun `a server without issues gets Home alone`() {
        val overview = HubOverview(hasIssues = false, permissions = SeerrPermissions(canViewIssues = true))

        assertEquals(listOf(TvDestination.Hub), offeredTabs(overview))
    }
}
