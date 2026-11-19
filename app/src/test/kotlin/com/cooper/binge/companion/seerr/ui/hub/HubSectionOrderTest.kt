package com.cooper.binge.companion.seerr.ui.hub

import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import org.junit.Assert.assertEquals
import org.junit.Test

/** The order of the Manage group on the Settings tab is the order of [HubSection]; a reorder is a decision, so it is pinned (#2337). */
class HubSectionOrderTest {
    @Test
    fun `an admin on a full server sees every section in the display order`() {
        val permissions =
            SeerrPermissions(isAdmin = true, canManageUsers = true, canViewIssues = true, canViewBlocklist = true)
        val overview = HubOverview(permissions = permissions, hasIssues = true, hasBlocklist = true)

        assertEquals(
            listOf(
                HubSection.Requests,
                HubSection.Issues,
                HubSection.Users,
                HubSection.Blocklist,
                HubSection.Settings,
                HubSection.ThisApp,
            ),
            overview.visibleSections(),
        )
    }

    @Test
    fun `a viewer with no permissions sees the requests row and This app, in that order`() {
        assertEquals(listOf(HubSection.Requests, HubSection.ThisApp), HubOverview().visibleSections())
    }
}
