package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.seerr.SeerrRequestDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestUserDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that tells a request the server approved as it was made from one a moderator answered. Seerr names the
 * requester in `modifiedBy` for the first and the moderator for the second (`server/entity/MediaRequest.ts`).
 */
class ApprovedAutomaticallyTest {
    private fun request(
        status: SeerrRequestStatusCode?,
        requestedById: Int? = 7,
        modifiedById: Int? = 7,
    ) = SeerrRequestDto(
        id = 1,
        status = status,
        media = SeerrRequestMediaDto(tmdbId = 1, mediaType = "movie"),
        createdAt = "2026-06-01T10:00:00.000Z",
        updatedAt = "2026-06-04T10:00:00.000Z",
        requestedBy = SeerrRequestUserDto(id = requestedById, displayName = "ana"),
        modifiedBy = modifiedById?.let { SeerrRequestUserDto(id = it, displayName = "ana") },
    )

    @Test
    fun `the requester as modifiedBy on an answered request is the server's own approval`() {
        assertTrue(request(SeerrRequestStatusCode.Approved).approvedAutomatically())
    }

    @Test
    fun `it stays automatic after completing or failing moves updatedAt`() {
        assertTrue(request(SeerrRequestStatusCode.Completed).approvedAutomatically())
        assertTrue(request(SeerrRequestStatusCode.Failed).approvedAutomatically())
    }

    @Test
    fun `someone else as modifiedBy is a moderator`() {
        assertFalse(request(SeerrRequestStatusCode.Approved, modifiedById = 9).approvedAutomatically())
    }

    @Test
    fun `no modifiedBy names no approval`() {
        assertFalse(request(SeerrRequestStatusCode.Approved, modifiedById = null).approvedAutomatically())
    }

    @Test
    fun `an unknown requester is not taken for the moderator`() {
        assertFalse(request(SeerrRequestStatusCode.Approved, requestedById = null).approvedAutomatically())
    }

    @Test
    fun `a pending, declined or unknown status is never automatic`() {
        assertFalse(request(SeerrRequestStatusCode.Pending).approvedAutomatically())
        assertFalse(request(SeerrRequestStatusCode.Declined).approvedAutomatically())
        assertFalse(request(null).approvedAutomatically())
    }
}
