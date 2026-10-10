package io.github.scottcooper92.binge.seerr.ui.users.settings

import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import org.junit.Assert.assertEquals
import org.junit.Test

/** The one toggle the user's and the server's default permission pages share. */
class PermissionSettingsTest {
    @Test
    fun `a permission is ticked, then unticked`() {
        val none = PermissionSettings()

        val ticked = none.toggled(ManageablePermission.ManageRequests)
        assertEquals(setOf(ManageablePermission.ManageRequests), ticked.selected)
        assertEquals(none, ticked.toggled(ManageablePermission.ManageRequests))
    }

    @Test
    fun `a locked permission stays as it is, ticked or not`() {
        val locked =
            PermissionSettings(
                selected = setOf(ManageablePermission.ManageUsers),
                locked = setOf(ManageablePermission.Admin, ManageablePermission.ManageUsers),
            )

        assertEquals(locked, locked.toggled(ManageablePermission.Admin))
        assertEquals(locked, locked.toggled(ManageablePermission.ManageUsers))
    }
}
