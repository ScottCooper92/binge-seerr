package io.github.scottcooper92.binge.seerr.service

import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.DownloadProgress
import com.binge.companion.contracts.request.v1.DownloadState
import com.binge.companion.contracts.request.v1.RequestStatus
import io.github.scottcooper92.binge.seerr.data.CachedStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val POLL = 15_000L
private const val MINUTE = 60_000L

class MediaStatusFreshnessTest {
    private val freshness = MediaStatusFreshness(POLL)

    private fun status(
        availability: Availability,
        download: DownloadState? = null,
    ): RequestStatus =
        RequestStatus
            .newBuilder()
            .setAvailability(availability)
            .apply { download?.let { setDownload(DownloadProgress.newBuilder().setState(it)) } }
            .build()

    @Test
    fun `something in flight goes stale at the poll interval`() {
        assertEquals(POLL, freshness.maxAgeMillis(status(Availability.AVAILABILITY_PROCESSING)))
        // Queued counts: what it waits on is the client picking it up, which can happen at any moment.
        assertEquals(
            POLL,
            freshness.maxAgeMillis(status(Availability.AVAILABILITY_PENDING, DownloadState.DOWNLOAD_STATE_QUEUED)),
        )
        assertEquals(
            POLL,
            freshness.maxAgeMillis(status(Availability.AVAILABILITY_AVAILABLE, DownloadState.DOWNLOAD_STATE_DOWNLOADING)),
        )
    }

    @Test
    fun `waiting on a person or on the next episode is minutes, and a settled title is much longer`() {
        val waiting = freshness.maxAgeMillis(status(Availability.AVAILABILITY_PENDING))
        val partial = freshness.maxAgeMillis(status(Availability.AVAILABILITY_PARTIALLY_AVAILABLE))
        val settled = freshness.maxAgeMillis(status(Availability.AVAILABILITY_AVAILABLE))

        assertEquals(waiting, partial)
        assertEquals(3 * MINUTE, waiting)
        assertEquals(30 * MINUTE, settled)
        assertEquals(settled, freshness.maxAgeMillis(status(Availability.AVAILABILITY_NOT_REQUESTED)))
        assertEquals(settled, freshness.maxAgeMillis(status(Availability.AVAILABILITY_BLOCKLISTED)))
    }

    /** A downloading title goes stale sooner than an available one — the whole point of grading it. */
    @Test
    fun `a downloading title goes stale sooner than an available one`() {
        val downloading = status(Availability.AVAILABILITY_PROCESSING, DownloadState.DOWNLOAD_STATE_DOWNLOADING)
        val available = status(Availability.AVAILABILITY_AVAILABLE)
        val aMinuteOld = 60_000L

        assertFalse(freshness.isFresh(CachedStatus(downloading, fetchedAtMillis = 0), nowMillis = aMinuteOld))
        assertTrue(freshness.isFresh(CachedStatus(available, fetchedAtMillis = 0), nowMillis = aMinuteOld))
    }

    @Test
    fun `a status that says nothing is not cached and never fresh`() {
        val nothing = status(Availability.AVAILABILITY_UNSPECIFIED)

        assertNull(freshness.maxAgeMillis(nothing))
        assertFalse(freshness.isFresh(CachedStatus(nothing, fetchedAtMillis = 0), nowMillis = 0))
    }

    /** A clock that moved backwards is not a reason to trust a row; it is a reason to ask again. */
    @Test
    fun `a row from the future is not fresh`() {
        val available = CachedStatus(status(Availability.AVAILABILITY_AVAILABLE), fetchedAtMillis = 10_000)

        assertFalse(freshness.isFresh(available, nowMillis = 9_000))
        assertTrue(freshness.isFresh(available, nowMillis = 10_000))
    }
}
