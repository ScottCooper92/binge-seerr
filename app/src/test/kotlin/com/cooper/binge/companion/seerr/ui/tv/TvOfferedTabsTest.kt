package com.cooper.binge.companion.seerr.ui.tv

import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.ui.hub.HubOverview
import org.junit.Assert.assertEquals
import org.junit.Test

private val Discovery = listOf(TvTab.Search, TvTab.Movies, TvTab.Tv)

/**
 * Which tabs the bar offers: the discovery tabs by the TV discovery flag alone, Requests always, and Issues by its own rule over what the
 * server has said of the account.
 */
class TvOfferedTabsTest {
    private fun offered(
        overview: HubOverview?,
        resolved: Boolean = true,
        discovery: Boolean = false,
    ) = offeredTabs(overview, resolved = resolved, discovery = discovery)

    @Test
    fun `before the server has answered every server-decided tab stands, to be hidden as pending`() {
        assertEquals(listOf(TvTab.Requests, TvTab.Issues), offered(overview = null, resolved = false))
    }

    @Test
    fun `a server that answered without an account offers Requests alone`() {
        assertEquals(listOf(TvTab.Requests), offered(overview = null))
    }

    @Test
    fun `an account the server lets see issues, on a server that has them, gets the Issues tab`() {
        val overview = HubOverview(hasIssues = true, permissions = SeerrPermissions(canViewIssues = true))

        assertEquals(listOf(TvTab.Requests, TvTab.Issues), offered(overview))
    }

    @Test
    fun `an account the server will not let see issues gets Requests alone`() {
        val overview = HubOverview(hasIssues = true, permissions = SeerrPermissions())

        assertEquals(listOf(TvTab.Requests), offered(overview))
    }

    /** A lineage without issues offers no Issues tab, whatever the account may do. */
    @Test
    fun `a server without issues gets Requests alone`() {
        val overview = HubOverview(hasIssues = false, permissions = SeerrPermissions(canViewIssues = true))

        assertEquals(listOf(TvTab.Requests), offered(overview))
    }

    /** With the flag off, which is how it ships, no TV user is shown a discovery tab, whatever the server says. */
    @Test
    fun `the discovery tabs are not offered with the TV discovery flag off`() {
        val overview = HubOverview(hasIssues = true, permissions = SeerrPermissions(canViewIssues = true))

        assertEquals(emptyList<TvTab>(), offered(overview).filter { it.isDiscovery })
        assertEquals(emptyList<TvTab>(), offered(overview = null, resolved = false).filter { it.isDiscovery })
    }

    @Test
    fun `the discovery tabs come first, before Requests and Issues, with the flag on`() {
        val overview = HubOverview(hasIssues = true, permissions = SeerrPermissions(canViewIssues = true))

        assertEquals(Discovery + listOf(TvTab.Requests, TvTab.Issues), offered(overview, discovery = true))
    }

    /** The flag does not decide the server's tabs: a viewer who may not see issues has no Issues tab with it on either. */
    @Test
    fun `the flag leaves the permission rule for Issues alone`() {
        val overview = HubOverview(hasIssues = true, permissions = SeerrPermissions())

        assertEquals(Discovery + TvTab.Requests, offered(overview, discovery = true))
    }
}
