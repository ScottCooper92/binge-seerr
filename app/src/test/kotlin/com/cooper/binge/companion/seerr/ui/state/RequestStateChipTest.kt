package com.cooper.binge.companion.seerr.ui.state

import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RequestStateChipTest {
    @Test
    fun `a request's tone follows its status, with unknown codes reading as pending`() {
        assertEquals(RequestStateTone.Pending, SeerrRequestStatusCode(1).tone())
        assertEquals(RequestStateTone.Success, SeerrRequestStatusCode.Approved.tone())
        assertEquals(RequestStateTone.Success, SeerrRequestStatusCode.Completed.tone())
        assertEquals(RequestStateTone.Declined, SeerrRequestStatusCode.Declined.tone())
        assertEquals(RequestStateTone.Declined, SeerrRequestStatusCode.Failed.tone())
        assertEquals(RequestStateTone.Pending, SeerrRequestStatusCode(99).tone())
    }

    @Test
    fun `a title's tone follows its media status`() {
        assertEquals(RequestStateTone.Pending, SeerrMediaStatusCode.Pending.stateLabel(DownloadActivity.None).tone)
        assertEquals(RequestStateTone.Success, SeerrMediaStatusCode.PartiallyAvailable.stateLabel(DownloadActivity.None).tone)
        assertEquals(RequestStateTone.Blocked, SeerrMediaStatusCode.Blocklisted.stateLabel(DownloadActivity.None).tone)
    }

    @Test
    fun `processing reads downloading only while something transfers`() {
        assertEquals(
            MediaStateLabel(R.string.media_state_downloading, RequestStateTone.Active),
            SeerrMediaStatusCode.Processing.stateLabel(DownloadActivity.Downloading),
        )
        assertEquals(
            MediaStateLabel(R.string.request_state_queued, RequestStateTone.Pending),
            SeerrMediaStatusCode.Processing.stateLabel(DownloadActivity.Queued),
        )
        assertEquals(
            MediaStateLabel(R.string.media_state_not_downloading, RequestStateTone.Pending),
            SeerrMediaStatusCode.Processing.stateLabel(DownloadActivity.None),
        )
    }

    @Test
    fun `a download never changes a state that is not processing`() {
        DownloadActivity.entries.forEach { download ->
            assertEquals(
                MediaStateLabel(R.string.media_state_available, RequestStateTone.Success),
                SeerrMediaStatusCode.Available.stateLabel(download),
            )
        }
    }

    @Test
    fun `a deleted title reads as deleted and blocked, not as pending`() {
        val state = SeerrMediaStatusCode.Deleted.stateLabel(DownloadActivity.None)
        assertEquals(RequestStateTone.Blocked, state.tone)
        assertEquals(R.string.media_state_deleted, state.labelRes)
        assertNotEquals(R.string.request_state_pending, state.labelRes)
    }

    @Test
    fun `a media status past the ones we model still falls back to pending`() {
        assertEquals(
            MediaStateLabel(R.string.request_state_pending, RequestStateTone.Pending),
            SeerrMediaStatusCode(99).stateLabel(DownloadActivity.None),
        )
    }
}
