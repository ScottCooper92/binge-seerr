package io.github.scottcooper92.binge.seerr.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrPermissionsTest {
    @Test
    fun `admin grants everything`() {
        val permissions = SeerrPermissions.fromBits(2)

        assertTrue(permissions.canRequest)
        assertTrue(permissions.canRequest4k)
        assertTrue(permissions.canManageRequests)
        assertTrue(permissions.canManageBlocklist)
        assertTrue(permissions.canCreateIssues)
        assertTrue(permissions.canRequestAdvanced)
        assertTrue(permissions.canManageSettings)
        assertTrue(permissions.canViewBlocklist)
    }

    @Test
    fun `managing the blocklist implies viewing it`() {
        assertTrue(SeerrPermissions.fromBits(1 shl 28).canViewBlocklist)
        assertFalse(SeerrPermissions.fromBits(1 shl 30).canManageBlocklist)
        assertTrue(SeerrPermissions.fromBits(1 shl 30).canViewBlocklist)
    }

    @Test
    fun `managing requests implies viewing them, but a plain requester may not`() {
        assertTrue(SeerrPermissions.fromBits(1 shl 14).canViewRequests)
        assertFalse(SeerrPermissions.fromBits(1 shl 5).canViewRequests)
        assertTrue(SeerrPermissions.fromBits(1 shl 4).canManageRequests)
        assertTrue(SeerrPermissions.fromBits(1 shl 4).canViewRequests)
    }

    @Test
    fun `the 4k umbrella grants both media types, the per-type bit only its own`() {
        assertTrue(SeerrPermissions.fromBits(1 shl 10).canRequest4kTv)
        assertTrue(SeerrPermissions.fromBits(1 shl 10).canRequest4kMovie)
        val movieOnly = SeerrPermissions.fromBits(1 shl 11)
        assertTrue(movieOnly.canRequest4kMovie)
        assertFalse(movieOnly.canRequest4kTv)
    }

    @Test
    fun `an unresolved user may do nothing`() {
        assertEquals(SeerrPermissions(), SeerrPermissions.fromBits(null))
        assertEquals(SeerrPermissions(), (null as SeerrUserDto?).toPermissions())
    }
}
