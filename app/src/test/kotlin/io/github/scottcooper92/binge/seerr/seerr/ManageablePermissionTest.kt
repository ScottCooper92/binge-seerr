package io.github.scottcooper92.binge.seerr.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `VOTE`, which the server defines and no editor offers. */
private const val UNMANAGED_BIT = 1 shl 6

class ManageablePermissionTest {
    @Test
    fun `admin implies everything, request does not cover 4K or advanced, and the umbrellas chain`() {
        val admin = setOf(ManageablePermission.Admin)
        assertTrue(ManageablePermission.entries.all { ManageablePermission.isGranted(it, admin) })

        val requester = setOf(ManageablePermission.Request)
        assertFalse(ManageablePermission.isGranted(ManageablePermission.Request4k, requester))
        assertFalse(ManageablePermission.isGranted(ManageablePermission.AutoApprove4k, requester))
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
    fun `the blocklist and watchlist-era toggles follow the server, and a 4K one its 4K`() {
        val overseerr = ManageablePermission.offered(PermissionScope(blocklist = false, watchlist = true))
        assertFalse(ManageablePermission.ViewBlocklist in overseerr)
        assertTrue(ManageablePermission.AutoRequest in overseerr)
        assertTrue(ManageablePermission.Admin in overseerr)
        assertFalse(ManageablePermission.ViewWatchlists in ManageablePermission.offered(PermissionScope(watchlist = false)))

        val movies4kOnly = ManageablePermission.offered(PermissionScope(series4k = false))
        assertTrue(ManageablePermission.Request4kMovies in movies4kOnly)
        assertFalse(ManageablePermission.Request4kSeries in movies4kOnly)
        // The umbrella covers both, so it needs both on, as the web client has it.
        assertFalse(ManageablePermission.Request4k in movies4kOnly)
    }

    @Test
    fun `a parent covers its children, and Manage Requests every auto-approve`() {
        val requester = setOf(ManageablePermission.Request)
        assertTrue(ManageablePermission.isGranted(ManageablePermission.RequestMovies, requester))
        assertFalse(ManageablePermission.isGranted(ManageablePermission.Request4kMovies, requester))

        val manager = setOf(ManageablePermission.ManageRequests)
        assertTrue(ManageablePermission.isGranted(ManageablePermission.AutoApprove4kSeries, manager))
        assertTrue(ManageablePermission.isGranted(ManageablePermission.ViewWatchlists, manager))
        assertFalse(ManageablePermission.isGranted(ManageablePermission.AutoRequest, manager))
    }

    @Test
    fun `an auto-approve needs its request, which a media type's own bit can meet`() {
        assertFalse(ManageablePermission.requirementsMet(ManageablePermission.AutoApprove, emptySet()))
        assertTrue(ManageablePermission.requirementsMet(ManageablePermission.AutoApprove, setOf(ManageablePermission.Request)))

        val moviesOnly = setOf(ManageablePermission.RequestMovies)
        assertTrue(ManageablePermission.requirementsMet(ManageablePermission.AutoApproveMovies, moviesOnly))
        assertFalse(ManageablePermission.requirementsMet(ManageablePermission.AutoApproveSeries, moviesOnly))
        assertFalse(ManageablePermission.requirementsMet(ManageablePermission.AutoApprove, moviesOnly))

        assertTrue(ManageablePermission.requirementsMet(ManageablePermission.AutoApprove4k, setOf(ManageablePermission.Admin)))
    }

    @Test
    fun `manage requests covers an auto-approve as granted but does not meet its request`() {
        val manager = setOf(ManageablePermission.ManageRequests)
        assertTrue(ManageablePermission.isGranted(ManageablePermission.AutoApprove, manager))
        assertFalse(ManageablePermission.requirementsMet(ManageablePermission.AutoApprove, manager))
        assertFalse(ManageablePermission.requirementsMet(ManageablePermission.AutoApproveMovies, manager))
    }

    @Test
    fun `a bitmask with only child bits reads them back`() {
        val bits = ManageablePermission.RequestMovies.bit or ManageablePermission.AutoRequestSeries.bit
        assertEquals(
            setOf(ManageablePermission.RequestMovies, ManageablePermission.AutoRequestSeries),
            ManageablePermission.decode(bits),
        )
    }
}
