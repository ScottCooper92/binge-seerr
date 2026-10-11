package com.cooper.binge.companion.seerr.ui.users

import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.Admin
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.AutoApprove
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.AutoApprove4k
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.AutoApprove4kMovies
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.AutoApprove4kSeries
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.AutoApproveMovies
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.AutoApproveSeries
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.AutoRequest
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.AutoRequestMovies
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.AutoRequestSeries
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.CreateIssues
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.ManageBlocklist
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.ManageIssues
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.ManageRequests
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.ManageSettings
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.ManageUsers
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.Request
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.Request4k
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.Request4kMovies
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.Request4kSeries
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.RequestAdvanced
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.RequestMovies
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.RequestSeries
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.ViewBlocklist
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.ViewIssues
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.ViewRecent
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.ViewRequests
import com.cooper.binge.companion.seerr.seerr.ManageablePermission.ViewWatchlists
import com.cooper.binge.companion.seerr.seerr.PermissionGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionTreeTest {
    @Test
    fun `the tree reads in the web client's order, children under their parent`() {
        val tree = permissionTree(ManageablePermission.entries, emptySet())

        assertEquals(
            listOf(
                PermissionGroup.Administration to listOf(PermissionNode(Admin), PermissionNode(ManageUsers)),
                PermissionGroup.Requests to
                    listOf(
                        PermissionNode(ManageRequests),
                        PermissionNode(RequestAdvanced, child = true),
                        PermissionNode(ViewRequests, child = true),
                        PermissionNode(ViewRecent, child = true),
                        PermissionNode(ViewWatchlists, child = true, last = true),
                        PermissionNode(Request),
                        PermissionNode(RequestMovies, child = true),
                        PermissionNode(RequestSeries, child = true, last = true),
                        PermissionNode(AutoApprove),
                        PermissionNode(AutoApproveMovies, child = true),
                        PermissionNode(AutoApproveSeries, child = true, last = true),
                        PermissionNode(AutoRequest),
                        PermissionNode(AutoRequestMovies, child = true),
                        PermissionNode(AutoRequestSeries, child = true, last = true),
                        PermissionNode(Request4k),
                        PermissionNode(Request4kMovies, child = true),
                        PermissionNode(Request4kSeries, child = true, last = true),
                        PermissionNode(AutoApprove4k),
                        PermissionNode(AutoApprove4kMovies, child = true),
                        PermissionNode(AutoApprove4kSeries, child = true, last = true),
                    ),
                PermissionGroup.Issues to
                    listOf(
                        PermissionNode(ManageIssues),
                        PermissionNode(CreateIssues, child = true),
                        PermissionNode(ViewIssues, child = true, last = true),
                    ),
                PermissionGroup.Blocklist to
                    listOf(PermissionNode(ManageBlocklist), PermissionNode(ViewBlocklist, child = true, last = true)),
            ),
            tree,
        )
    }

    @Test
    fun `manage settings shows only when it is already held`() {
        val admin = permissionTree(ManageablePermission.entries, setOf(ManageSettings)).first().second

        assertEquals(listOf(Admin, ManageSettings, ManageUsers), admin.map { it.permission })
    }

    @Test
    fun `what is not offered is left out, and an emptied group with it`() {
        val tree = permissionTree(listOf(Request, RequestMovies, ManageBlocklist), emptySet())

        assertEquals(
            listOf(
                PermissionGroup.Requests to listOf(PermissionNode(Request), PermissionNode(RequestMovies, child = true, last = true)),
                PermissionGroup.Blocklist to listOf(PermissionNode(ManageBlocklist)),
            ),
            tree,
        )
    }

    @Test
    fun `a child whose parent is not offered stands as a root`() {
        // A server with 4K movies on and 4K series off offers Request 4K Movies without the umbrella, which needs both.
        val tree = permissionTree(listOf(Request, Request4kMovies, AutoApprove4kMovies), emptySet())

        assertEquals(
            listOf(
                PermissionGroup.Requests to
                    listOf(PermissionNode(Request), PermissionNode(Request4kMovies), PermissionNode(AutoApprove4kMovies)),
            ),
            tree,
        )
    }
}
