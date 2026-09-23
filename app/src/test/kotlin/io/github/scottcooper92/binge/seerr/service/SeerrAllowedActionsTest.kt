package io.github.scottcooper92.binge.seerr.service

import com.binge.companion.contracts.request.v1.ApprovalState
import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.Capability
import com.binge.companion.contracts.request.v1.RequestInfo
import com.binge.companion.contracts.request.v1.RequestStatus
import io.github.scottcooper92.binge.seerr.data.CachedStatus
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrPublicSettings
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import io.github.scottcooper92.binge.seerr.seerr.SeerrStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val VIEWER = 1
private const val SOMEONE_ELSE = 2

/** The per-request rules are Seerr's own, from `server/routes/request.ts`; each case names the check it mirrors. */
class SeerrAllowedActionsTest {
    private val profile = SeerrServerProfile.from(SeerrStatusDto(version = "2.7.0"), SeerrPublicSettings())
    private val requester = SeerrPermissions(canRequest = true)
    private val moderator = SeerrPermissions(canRequest = true, canManageRequests = true)

    private fun request(
        id: Int,
        state: ApprovalState,
        seasons: List<Int> = emptyList(),
    ): RequestInfo =
        RequestInfo
            .newBuilder()
            .setId(id)
            .setState(state)
            .addAllSeasonNumbers(seasons)
            .build()

    private fun server(
        vararg requests: Pair<RequestInfo, Int>,
        availability: Availability = Availability.AVAILABILITY_PENDING,
    ) = CachedStatus(
        status =
            RequestStatus
                .newBuilder()
                .setAvailability(availability)
                .addAllRequests(requests.map { it.first })
                .build(),
        fetchedAtMillis = 0,
        requesterIds = requests.associate { (request, user) -> request.id to user },
    )

    private fun RequestStatus.actionsOf(id: Int): List<Capability> = requestsList.single { it.id == id }.allowedActionsList

    @Test
    fun `a requester may cancel their own pending request and nothing else`() {
        val status =
            requester.withAllowedActions(
                server(
                    request(4, ApprovalState.APPROVAL_STATE_PENDING) to VIEWER,
                    request(5, ApprovalState.APPROVAL_STATE_APPROVED) to VIEWER,
                    request(6, ApprovalState.APPROVAL_STATE_PENDING) to SOMEONE_ELSE,
                ),
                viewerId = VIEWER,
                profile = profile,
            )

        assertEquals(listOf(Capability.CAPABILITY_CANCEL), status.actionsOf(4))
        assertTrue("only a pending request may be withdrawn", status.actionsOf(5).isEmpty())
        assertTrue("another user's request is theirs to withdraw", status.actionsOf(6).isEmpty())
    }

    /** #443: two users requested the same title, and the second was offered a cancel the server answers 401. */
    @Test
    fun `a title holding only someone else's request offers the requester no cancel at all`() {
        val status =
            requester.withAllowedActions(
                server(request(6, ApprovalState.APPROVAL_STATE_PENDING) to SOMEONE_ELSE),
                viewerId = VIEWER,
                profile = profile,
            )

        assertFalse(Capability.CAPABILITY_CANCEL in status.allowedActionsList)
        assertTrue(status.actionsOf(6).isEmpty())
    }

    @Test
    fun `a moderator may cancel any request and decide only the pending ones`() {
        val status =
            moderator.withAllowedActions(
                server(
                    request(4, ApprovalState.APPROVAL_STATE_PENDING) to SOMEONE_ELSE,
                    request(5, ApprovalState.APPROVAL_STATE_APPROVED) to SOMEONE_ELSE,
                    request(7, ApprovalState.APPROVAL_STATE_FAILED) to SOMEONE_ELSE,
                ),
                viewerId = VIEWER,
                profile = profile,
            )

        assertEquals(
            setOf(Capability.CAPABILITY_APPROVE, Capability.CAPABILITY_DECLINE, Capability.CAPABILITY_CANCEL),
            status.actionsOf(4).toSet(),
        )
        assertEquals(listOf(Capability.CAPABILITY_CANCEL), status.actionsOf(5))
        assertEquals(setOf(Capability.CAPABILITY_RETRY, Capability.CAPABILITY_CANCEL), status.actionsOf(7).toSet())
    }

    @Test
    fun `seasons are editable on a pending TV request its requester or a moderator holds`() {
        val tv = listOf(1, 2)
        val asRequester =
            requester.withAllowedActions(
                server(
                    request(4, ApprovalState.APPROVAL_STATE_PENDING, tv) to VIEWER,
                    request(5, ApprovalState.APPROVAL_STATE_APPROVED, tv) to VIEWER,
                    request(6, ApprovalState.APPROVAL_STATE_PENDING, tv) to SOMEONE_ELSE,
                ),
                viewerId = VIEWER,
                profile = profile,
            )
        assertTrue(Capability.CAPABILITY_EDIT_SEASONS in asRequester.actionsOf(4))
        assertFalse("the server answers 409 for anything but pending", Capability.CAPABILITY_EDIT_SEASONS in asRequester.actionsOf(5))
        assertFalse(Capability.CAPABILITY_EDIT_SEASONS in asRequester.actionsOf(6))

        val asModerator =
            moderator.withAllowedActions(
                server(request(6, ApprovalState.APPROVAL_STATE_PENDING, tv) to SOMEONE_ELSE),
                viewerId = VIEWER,
                profile = profile,
            )
        assertTrue(Capability.CAPABILITY_EDIT_SEASONS in asModerator.actionsOf(6))
    }

    @Test
    fun `a movie request has no seasons to edit`() {
        val status =
            moderator.withAllowedActions(
                server(request(4, ApprovalState.APPROVAL_STATE_PENDING) to VIEWER),
                viewerId = VIEWER,
                profile = profile,
            )

        assertFalse(Capability.CAPABILITY_EDIT_SEASONS in status.actionsOf(4))
        assertFalse(Capability.CAPABILITY_EDIT_SEASONS in status.allowedActionsList)
    }

    /** A request the server named no requester for is nobody's, so only a moderator's actions reach it. */
    @Test
    fun `an unknown requester is never read as the viewer`() {
        val status =
            requester.withAllowedActions(
                CachedStatus(
                    RequestStatus
                        .newBuilder()
                        .addRequests(request(4, ApprovalState.APPROVAL_STATE_PENDING))
                        .build(),
                    fetchedAtMillis = 0,
                ),
                viewerId = VIEWER,
                profile = profile,
            )

        assertTrue(status.actionsOf(4).isEmpty())
    }

    @Test
    fun `the title's request-scoped actions are the union of its requests'`() {
        val status =
            moderator.withAllowedActions(
                server(
                    request(4, ApprovalState.APPROVAL_STATE_APPROVED) to SOMEONE_ELSE,
                    request(7, ApprovalState.APPROVAL_STATE_FAILED) to SOMEONE_ELSE,
                    availability = Availability.AVAILABILITY_AVAILABLE,
                ),
                viewerId = VIEWER,
                profile = profile,
            )

        assertTrue(Capability.CAPABILITY_RETRY in status.allowedActionsList)
        assertTrue(Capability.CAPABILITY_CANCEL in status.allowedActionsList)
        assertFalse("nothing is pending", Capability.CAPABILITY_APPROVE in status.allowedActionsList)
    }
}
