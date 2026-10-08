package io.github.scottcooper92.binge.seerr.ui.users

import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.Admin
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.AutoApprove
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.AutoApprove4k
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.CreateIssues
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.ManageBlocklist
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.ManageIssues
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.ManageRequests
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.ManageSettings
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.ManageUsers
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.Request
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.Request4k
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.RequestAdvanced
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.ViewBlocklist
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.ViewIssues
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission.ViewRequests
import io.github.scottcooper92.binge.seerr.seerr.PermissionGroup
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
                        PermissionNode(ViewRequests, child = true, last = true),
                        PermissionNode(Request),
                        PermissionNode(AutoApprove),
                        PermissionNode(Request4k),
                        PermissionNode(AutoApprove4k),
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
        val tree = permissionTree(listOf(Request, ViewRequests, ViewBlocklist), emptySet())

        assertEquals(
            listOf(
                PermissionGroup.Requests to listOf(PermissionNode(ViewRequests, child = true, last = true), PermissionNode(Request)),
                PermissionGroup.Blocklist to listOf(PermissionNode(ViewBlocklist, child = true, last = true)),
            ),
            tree,
        )
    }
}
