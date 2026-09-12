package io.github.scottcooper92.binge.seerr.seerr

import com.binge.integration.contracts.request.v1.ApprovalState
import com.binge.integration.contracts.request.v1.Availability
import com.binge.integration.contracts.request.v1.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 1_700_000_000_000L

class SeerrStatusMappingTest {
    @Test
    fun `an untracked title is not requested, with nothing else set`() {
        val status = (null as SeerrMediaInfoDto?).toRequestStatus(NOW)

        assertEquals(Availability.AVAILABILITY_NOT_REQUESTED, status.availability)
        assertFalse(status.hasDownload())
        assertTrue(status.requestsList.isEmpty())
    }

    @Test
    fun `a tracked title carries its seasons, requests and watch link`() {
        val info =
            SeerrMediaInfoDto(
                id = 9,
                status = SeerrMediaStatusCode.PartiallyAvailable,
                seasons =
                    listOf(
                        SeerrSeasonStatusDto(1, SeerrMediaStatusCode.Available),
                        SeerrSeasonStatusDto(2, SeerrMediaStatusCode.Pending),
                    ),
                requests =
                    listOf(
                        SeerrRequestSummaryDto(
                            id = 4,
                            status = SeerrRequestStatusCode.Completed,
                            createdAt = "2026-06-12T08:30:00.000Z",
                            requestedBy = SeerrRequestUserDto(email = "scott@example.com"),
                            seasons = listOf(SeerrSeasonStatusDto(1)),
                        ),
                    ),
                jellyfinMediaUrl = "https://jellyfin.local/web/#/details?id=1",
            )

        val status = info.toRequestStatus(NOW)

        assertEquals(Availability.AVAILABILITY_PARTIALLY_AVAILABLE, status.availability)
        assertEquals(listOf(1, 2), status.seasonsList.map { it.seasonNumber })
        assertEquals(Availability.AVAILABILITY_PENDING, status.seasonsList[1].availability)
        val request = status.requestsList.single()
        assertEquals(ApprovalState.APPROVAL_STATE_APPROVED, request.state)
        assertEquals("scott", request.requestedBy)
        assertEquals(1_781_253_000_000L, request.requestedAtEpochMs)
        assertEquals(listOf(1), request.seasonNumbersList)
        assertEquals("https://jellyfin.local/web/#/details?id=1", status.watchUrl)
    }

    @Test
    fun `downloads aggregate into one bar with the slowest eta`() {
        val downloads =
            listOf(
                SeerrDownloadStatusDto(
                    title = "Some.Release",
                    size = 1_000.0,
                    sizeLeft = 250.0,
                    timeLeft = "00:10:00",
                    status = "downloading",
                ),
                SeerrDownloadStatusDto(size = 1_000.0, sizeLeft = 1_000.0, timeLeft = "1.02:00:00", status = "queued"),
            )

        val progress = checkNotNull(downloads.toDownloadProgress(NOW))

        assertEquals(0.375f, progress.fraction, 0.0001f)
        assertEquals("Some.Release", progress.label)
        assertEquals(2_000L, progress.totalBytes)
        assertEquals(26 * 60, progress.etaMinutes)
        assertEquals(DownloadState.DOWNLOAD_STATE_DOWNLOADING, progress.state)
    }

    @Test
    fun `unknown statuses read as the safe defaults`() {
        assertEquals(Availability.AVAILABILITY_NOT_REQUESTED, SeerrMediaStatusCode(99).toAvailability())
        assertEquals(ApprovalState.APPROVAL_STATE_PENDING, SeerrRequestStatusCode(99).toApprovalState())
        assertEquals(ApprovalState.APPROVAL_STATE_FAILED, SeerrRequestStatusCode.Failed.toApprovalState())
    }

    @Test
    fun `the eta is the slowest active download's remaining minutes, rounded up`() {
        val downloads =
            listOf(
                SeerrDownloadStatusDto(estimatedCompletionTime = "2023-11-14T22:40:30Z"),
                SeerrDownloadStatusDto(timeLeft = "00:05:00"),
            )

        assertEquals(28, downloads.etaMinutes(NOW))
        assertEquals(null, emptyList<SeerrDownloadStatusDto>().etaMinutes(NOW))
    }
}
