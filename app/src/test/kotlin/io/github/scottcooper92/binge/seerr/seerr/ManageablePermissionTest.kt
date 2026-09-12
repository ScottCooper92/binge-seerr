package io.github.scottcooper92.binge.seerr.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val UNMANAGED_BIT = 1 shl 26

class ManageablePermissionTest {
    @Test
    fun `admin implies everything, request covers what sits beneath it, and the umbrellas chain`() {
        val admin = setOf(ManageablePermission.Admin)
        assertTrue(ManageablePermission.entries.all { ManageablePermission.isGranted(it, admin) })

        val requester = setOf(ManageablePermission.Request)
        assertTrue(ManageablePermission.isGranted(ManageablePermission.Request4k, requester))
        assertTrue(ManageablePermission.isGranted(ManageablePermission.AutoApprove4k, requester))
        assertFalse(ManageablePermission.isGranted(ManageablePermission.ManageRequests, requester))

        val manager = setOf(ManageablePermission.ManageIssues)
        assertTrue(ManageablePermission.isGranted(ManageablePermission.CreateIssues, manager))
        assertFalse(ManageablePermission.isGranted(ManageablePermission.ViewBlocklist, manager))
    }

    @Test
    fun `applying a selection keeps the bits the editor does not manage, and decoding reads them back`() {
        val original = UNMANAGED_BIT or ManageablePermission.Request.bit or ManageablePermission.ViewIssues.bit

        val applied = ManageablePermission.apply(original, setOf(ManageablePermission.ManageRequests))

        assertEquals(UNMANAGED_BIT or ManageablePermission.ManageRequests.bit, applied)
        assertEquals(setOf(ManageablePermission.ManageRequests), ManageablePermission.decode(applied))
    }

    @Test
    fun `the blocklist toggles are offered on the jellyseerr lineage only`() {
        assertTrue(ManageablePermission.ViewBlocklist in ManageablePermission.offered(jellyseerrLineage = true))
        assertFalse(ManageablePermission.ViewBlocklist in ManageablePermission.offered(jellyseerrLineage = false))
        assertTrue(ManageablePermission.Admin in ManageablePermission.offered(jellyseerrLineage = false))
    }
}
