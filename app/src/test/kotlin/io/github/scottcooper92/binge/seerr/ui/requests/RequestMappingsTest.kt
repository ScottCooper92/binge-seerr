package io.github.scottcooper92.binge.seerr.ui.requests

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.state.RequestStateTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RequestMappingsTest {
    private fun item(
        status: SeerrRequestStatusCode? = null,
        mediaStatus: SeerrMediaStatusCode? = null,
        download: RequestDownload? = null,
    ) = RequestItem(1, 1, RequestMediaType.Movie, null, null, null, null, null, status, mediaStatus, download, emptyList(), false)

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
}
