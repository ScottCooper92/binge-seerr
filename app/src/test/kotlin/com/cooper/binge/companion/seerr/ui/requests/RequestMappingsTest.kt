package com.cooper.binge.companion.seerr.ui.requests

import com.binge.designsystem.component.MediaTypeTagType
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrDownloadStatusDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.ui.state.DownloadActivity
import com.cooper.binge.companion.seerr.ui.state.RequestStateTone
import com.cooper.binge.companion.seerr.util.requestItem
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
    ) = requestItem(
        title = null,
        year = null,
        requestedBy = null,
        requestedById = null,
        status = status,
        mediaStatus = mediaStatus,
        download = download,
    )

    @Test
    fun `the Mark as sheet draws each state with the glyph its status chip draws`() {
        val approved = SeerrRequestStatusCode.Approved
        assertEquals(MediaStatusChoice.Available.icon(), item(approved, SeerrMediaStatusCode.Available).statusChip().icon)
        assertEquals(MediaStatusChoice.PartiallyAvailable.icon(), item(approved, SeerrMediaStatusCode.PartiallyAvailable).statusChip().icon)
        assertEquals(MediaStatusChoice.Pending.icon(), item(SeerrRequestStatusCode(1)).statusChip().icon)
    }

    @Test
    fun `the row's chip is the decision, then the media outcome, then the bare approval state`() {
        assertEquals(
            RequestRowChip(R.string.request_state_declined, RequestStateTone.Declined, StatusGlyphs.Declined),
            item(SeerrRequestStatusCode.Declined).statusChip(),
        )
        assertEquals(
            RequestRowChip(R.string.request_state_failed, RequestStateTone.Declined, StatusGlyphs.Failed),
            item(SeerrRequestStatusCode.Failed).statusChip(),
        )
        assertEquals(
            RequestRowChip(R.string.media_state_available, RequestStateTone.Success, StatusGlyphs.Available),
            item(SeerrRequestStatusCode.Approved, SeerrMediaStatusCode.Available).statusChip(),
        )
        assertEquals(
            RequestRowChip(R.string.request_state_approved, RequestStateTone.Success, StatusGlyphs.Approved),
            item(SeerrRequestStatusCode.Completed).statusChip(),
        )
        assertEquals(
            RequestRowChip(R.string.request_state_pending, RequestStateTone.Pending, StatusGlyphs.Pending),
            item(SeerrRequestStatusCode(1)).statusChip(),
        )
    }

    @Test
    fun `every mediaStatus value against an approved request, independent of download`() {
        val activeDownload = RequestDownload(fraction = 0.5f, etaMinutes = 3, downloading = true)

        fun approved(
            mediaStatus: SeerrMediaStatusCode?,
            download: RequestDownload? = null,
        ) = item(SeerrRequestStatusCode.Approved, mediaStatus, download).statusChip()

        // Only a still-null or genuinely-unrecognised mediaStatus falls back to the bare "Approved" chip.
        assertEquals(
            RequestRowChip(R.string.request_state_approved, RequestStateTone.Success, StatusGlyphs.Approved),
            approved(mediaStatus = null),
        )
        assertEquals(
            RequestRowChip(R.string.request_state_approved, RequestStateTone.Success, StatusGlyphs.Approved),
            approved(SeerrMediaStatusCode.Unknown),
        )
        assertEquals(
            RequestRowChip(R.string.request_state_approved, RequestStateTone.Success, StatusGlyphs.Approved),
            approved(null, activeDownload),
        )

        assertEquals(
            RequestRowChip(R.string.request_state_pending, RequestStateTone.Pending, StatusGlyphs.Pending),
            approved(SeerrMediaStatusCode.Pending),
        )
        assertEquals(
            RequestRowChip(R.string.media_state_not_downloading, RequestStateTone.Pending, StatusGlyphs.Approved),
            approved(SeerrMediaStatusCode.Processing),
        )
        assertEquals(
            RequestRowChip(R.string.media_state_partially_available, RequestStateTone.Success, StatusGlyphs.PartiallyAvailable),
            approved(SeerrMediaStatusCode.PartiallyAvailable),
        )
        assertEquals(
            RequestRowChip(R.string.media_state_available, RequestStateTone.Success, StatusGlyphs.Available),
            approved(SeerrMediaStatusCode.Available),
        )
        assertEquals(
            RequestRowChip(R.string.media_state_blocklisted, RequestStateTone.Blocked, StatusGlyphs.Blocked),
            approved(SeerrMediaStatusCode.Blocklisted),
        )
        assertEquals(
            RequestRowChip(R.string.media_state_deleted, RequestStateTone.Blocked, StatusGlyphs.Blocked),
            approved(SeerrMediaStatusCode.Deleted),
        )

        // download never overrides a media outcome that isn't Processing.
        assertEquals(
            RequestRowChip(R.string.media_state_blocklisted, RequestStateTone.Blocked, StatusGlyphs.Blocked),
            approved(SeerrMediaStatusCode.Blocklisted, activeDownload),
        )
    }

    @Test
    fun `download refines a Processing chip rather than standing in for mediaStatus`() {
        val activeDownload = RequestDownload(fraction = 0.5f, etaMinutes = 3, downloading = true)
        val queuedDownload = RequestDownload(fraction = 0f, etaMinutes = null, downloading = false)

        assertEquals(
            RequestRowChip(R.string.media_state_downloading, RequestStateTone.Active, StatusGlyphs.Processing),
            item(SeerrRequestStatusCode.Approved, SeerrMediaStatusCode.Processing, activeDownload).statusChip(),
        )
        assertEquals(
            RequestRowChip(R.string.request_state_queued, RequestStateTone.Pending, StatusGlyphs.Queued),
            item(SeerrRequestStatusCode.Approved, SeerrMediaStatusCode.Processing, queuedDownload).statusChip(),
        )
        // Processing with nothing in the queue: files deleted, or never grabbed (#1719).
        assertEquals(
            RequestRowChip(R.string.media_state_not_downloading, RequestStateTone.Pending, StatusGlyphs.Approved),
            item(SeerrRequestStatusCode.Approved, SeerrMediaStatusCode.Processing).statusChip(),
        )
        assertTrue(item(SeerrRequestStatusCode.Approved, SeerrMediaStatusCode.Processing).isProcessingWithoutDownload)
        assertFalse(item(SeerrRequestStatusCode.Approved, SeerrMediaStatusCode.Processing, queuedDownload).isProcessingWithoutDownload)
        assertFalse(item(SeerrRequestStatusCode.Approved, SeerrMediaStatusCode.Available).isProcessingWithoutDownload)
    }

    @Test
    fun `a version's queue reads as nothing, queued or downloading`() {
        assertEquals(DownloadActivity.None, emptyList<SeerrDownloadStatusDto>().downloadActivity())
        assertEquals(
            DownloadActivity.Queued,
            listOf(SeerrDownloadStatusDto(size = 10.0, sizeLeft = 10.0, status = "queued")).downloadActivity(),
        )
        assertEquals(
            DownloadActivity.Downloading,
            listOf(SeerrDownloadStatusDto(size = 10.0, sizeLeft = 4.0, status = "downloading")).downloadActivity(),
        )
        assertEquals(DownloadActivity.None, (null as RequestDownload?).activity())
    }

    @Test
    fun `each request media type maps to its own design-system tag type`() {
        assertEquals(MediaTypeTagType.Movie, RequestMediaType.Movie.toTagType())
        assertEquals(MediaTypeTagType.Tv, RequestMediaType.Tv.toTagType())
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
