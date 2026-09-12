package io.github.scottcooper92.binge.seerr.ui.requests

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ADMIN = 2
private const val REQUEST = 32
private const val MANAGE_BLOCKLIST = 1 shl 28

class RequestMappingsTest {
    private fun item(
        status: SeerrRequestStatusCode? = null,
        mediaStatus: SeerrMediaStatusCode? = null,
        download: RequestDownload? = null,
    ) = RequestItem(
        id = 1,
        tmdbId = 1,
        mediaType = RequestMediaType.Movie,
        title = null,
        posterUrl = null,
        year = null,
        requestedBy = null,
        requestedById = null,
        requestedAtMillis = null,
        status = status,
        mediaStatus = mediaStatus,
        download = download,
        seasonNumbers = emptyList(),
        is4k = false,
    )

    @Test
    fun `the row's chip is the decision, then the outcome, then the bare state`() {
        assertEquals(
            RequestRowChip(R.string.request_state_declined, RequestStateTone.Declined),
            item(SeerrRequestStatusCode.Declined).statusChip(),
        )
        assertEquals(
            RequestRowChip(R.string.request_state_failed, RequestStateTone.Declined),
            item(SeerrRequestStatusCode.Failed).statusChip(),
        )
        assertEquals(
            RequestRowChip(R.string.media_state_available, RequestStateTone.Success),
            item(SeerrRequestStatusCode.Approved, SeerrMediaStatusCode.Available).statusChip(),
        )
        assertEquals(
            RequestRowChip(R.string.media_state_processing, RequestStateTone.Active),
            item(SeerrRequestStatusCode.Approved, download = RequestDownload(0.5f, 3, downloading = true)).statusChip(),
        )
        assertEquals(
            RequestRowChip(R.string.request_state_queued, RequestStateTone.Pending),
            item(SeerrRequestStatusCode.Approved, download = RequestDownload(0f, null, downloading = false)).statusChip(),
        )
        assertEquals(
            RequestRowChip(R.string.request_state_approved, RequestStateTone.Success),
            item(SeerrRequestStatusCode.Completed).statusChip(),
        )
        assertEquals(RequestRowChip(R.string.request_state_pending, RequestStateTone.Pending), item(SeerrRequestStatusCode(1)).statusChip())
    }

    @Test
    fun `only the filters with a server bucket carry a count`() {
        val counts = RequestCounts(total = 10, pending = 2, approved = 5, processing = 1, available = 4)

        assertEquals(10, counts.countFor(RequestFilter.All))
        assertEquals(2, counts.countFor(RequestFilter.Pending))
        assertEquals(1, counts.countFor(RequestFilter.Processing))
        assertNull(counts.countFor(RequestFilter.Failed))
        assertNull(counts.countFor(RequestFilter.Unavailable))
    }

    @Test
    fun `a moderator may decide a pending request and retry a failed one, while a requester may only remove their own pending one`() {
        val moderator = ModerationScope(SeerrPermissions.fromBits(ADMIN), currentUserId = 1, hasBlocklist = true)
        val requester = ModerationScope(SeerrPermissions.fromBits(REQUEST), currentUserId = 7, hasBlocklist = true)
        val pending = item(SeerrRequestStatusCode(1)).copy(requestedById = 7)
        val failed = item(SeerrRequestStatusCode.Failed).copy(requestedById = 7)

        assertEquals(
            RequestActions(canApprove = true, canDecline = true, canRetry = false, canRemove = true, canBlock = true),
            pending.actions(moderator),
        )
        assertEquals(
            RequestActions(canApprove = false, canDecline = false, canRetry = true, canRemove = true, canBlock = true),
            failed.actions(moderator),
        )
        assertEquals(RequestActions(canRemove = true), pending.actions(requester))
        assertFalse(failed.actions(requester).any)
        assertFalse(pending.copy(requestedById = 8).actions(requester).any)
    }

    @Test
    fun `blocking needs the lineage's blocklist and the permission, and never a title already blocked`() {
        val blocker = ModerationScope(SeerrPermissions.fromBits(REQUEST or MANAGE_BLOCKLIST), currentUserId = 7, hasBlocklist = true)
        val pending = item(SeerrRequestStatusCode(1)).copy(requestedById = 7)

        assertTrue(pending.actions(blocker).canBlock)
        assertFalse(pending.actions(blocker.copy(hasBlocklist = false)).canBlock)
        assertFalse(pending.copy(mediaStatus = SeerrMediaStatusCode.Blocklisted).actions(blocker).canBlock)
        assertFalse(pending.actions(ModerationScope(SeerrPermissions.fromBits(ADMIN), 1, hasBlocklist = false)).canBlock)
    }
}
