package io.github.scottcooper92.binge.seerr.ui.state

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
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
        assertEquals(RequestStateTone.Pending, SeerrMediaStatusCode.Pending.tone())
        assertEquals(RequestStateTone.Active, SeerrMediaStatusCode.Processing.tone())
        assertEquals(RequestStateTone.Success, SeerrMediaStatusCode.PartiallyAvailable.tone())
        assertEquals(RequestStateTone.Success, SeerrMediaStatusCode.Available.tone())
        assertEquals(RequestStateTone.Blocked, SeerrMediaStatusCode.Blocklisted.tone())
    }

    @Test
    fun `a deleted title reads as deleted and blocked, not as pending`() {
        assertEquals(RequestStateTone.Blocked, SeerrMediaStatusCode.Deleted.tone())
        assertEquals(R.string.media_state_deleted, SeerrMediaStatusCode.Deleted.labelRes())
        assertNotEquals(R.string.request_state_pending, SeerrMediaStatusCode.Deleted.labelRes())
    }

    @Test
    fun `a media status past the ones we model still falls back to pending`() {
        assertEquals(RequestStateTone.Pending, SeerrMediaStatusCode(99).tone())
        assertEquals(R.string.request_state_pending, SeerrMediaStatusCode(99).labelRes())
    }
}
