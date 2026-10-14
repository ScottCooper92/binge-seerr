package com.cooper.binge.companion.seerr.seerr

import com.binge.companion.contracts.request.v1.ApprovalState
import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val NOW = 1_700_000_000_000L

class SeerrStatusMappingTest {
    @Test
    fun `an untracked title is not requested, with nothing else set`() {
        val status = (null as SeerrMediaInfoDto?).toRequestStatus(NOW)

        assertEquals(Availability.AVAILABILITY_NOT_REQUESTED, status.standardVersion().availability)
        assertFalse(status.standardVersion().hasDownload())
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
                            requestedBy = SeerrRequestUserDto(displayName = "scott@example.com", email = "scott@example.com"),
                            seasons = listOf(SeerrSeasonStatusDto(1)),
                        ),
                    ),
                jellyfinMediaUrl = "https://jellyfin.local/web/#/details?id=1",
            )

        val status = info.toRequestStatus(NOW)

        val standard = status.standardVersion()
        assertEquals(Availability.AVAILABILITY_PARTIALLY_AVAILABLE, standard.availability)
        assertEquals(listOf(1, 2), standard.seasonsList.map { it.seasonNumber })
        assertEquals(Availability.AVAILABILITY_PENDING, standard.seasonsList[1].availability)
        val request = status.requestsList.single()
        assertEquals(ApprovalState.APPROVAL_STATE_APPROVED, request.state)
        // Only an email: the contract never sends any part of one, so the field stays empty (#695).
        assertEquals("", request.requestedBy)
        assertEquals(1_781_253_000_000L, request.requestedAtEpochMs)
        assertEquals(listOf(1), request.seasonNumbersList)
        assertEquals("4", request.id)
        assertEquals("https://jellyfin.local/web/#/details?id=1", standard.watchUrl)
    }

    /** Seerr and Jellyseerr name the 4K server's link `mediaUrl4k`, Overseerr `plexUrl4k`; each version keeps its own (#1526). */
    @Test
    fun `each version carries its own server's watch link`() {
        val seerr =
            SeerrMediaInfoDto(mediaUrl = "https://jellyfin.local/a", mediaUrl4k = "https://jellyfin4k.local/b").toRequestStatus(NOW)
        val overseerr = SeerrMediaInfoDto(plexUrl = "https://app.plex.tv/a", plexUrl4k = "https://app.plex.tv/b").toRequestStatus(NOW)

        assertEquals("https://jellyfin.local/a", seerr.standardVersion().watchUrl)
        assertEquals("https://jellyfin4k.local/b", seerr.version4k().watchUrl)
        assertEquals("https://app.plex.tv/a", overseerr.standardVersion().watchUrl)
        assertEquals("https://app.plex.tv/b", overseerr.version4k().watchUrl)
    }

    @Test
    fun `a version the 4k server does not hold has no watch link`() {
        val status = SeerrMediaInfoDto(status = SeerrMediaStatusCode.Available, mediaUrl = "https://jellyfin.local/a").toRequestStatus(NOW)

        assertEquals("", status.version4k().watchUrl)
    }

    /** The contract carries http and https only; a server's link in any other scheme is left out rather than sent. */
    @Test
    fun `a watch link that is not http or https is not sent`() {
        val status = SeerrMediaInfoDto(mediaUrl = "plex://preplay/?metadataKey=1", mediaUrl4k = "file:///media/x.mkv").toRequestStatus(NOW)

        assertEquals("", status.standardVersion().watchUrl)
        assertEquals("", status.version4k().watchUrl)
    }

    @Test
    fun `a standard and a 4K request against one title are told apart`() {
        val info =
            SeerrMediaInfoDto(
                id = 9,
                status = SeerrMediaStatusCode.Available,
                requests =
                    listOf(
                        SeerrRequestSummaryDto(id = 4, status = SeerrRequestStatusCode.Completed, is4k = false),
                        SeerrRequestSummaryDto(id = 5, status = SeerrRequestStatusCode.Approved, is4k = true),
                    ),
            )

        val requests = info.toRequestStatus(NOW).requestsList

        assertEquals(listOf("4", "5"), requests.map { it.id })
        assertEquals(listOf(false, true), requests.map { it.is4K })
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
    fun `a deleted title is offered to the host as not requested`() {
        assertEquals(Availability.AVAILABILITY_NOT_REQUESTED, SeerrMediaStatusCode.Deleted.toAvailability())
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

    @Test
    fun `a time left the queue did not write in one of the two shapes is read as no eta at all`() {
        // `1:xx:03:04` is the one that matters and the one the review found: four fields with one
        // that will not parse, which reads as a valid three if the bad field is dropped before the
        // count is taken. `02:03.5:04` is the one the next review round found: a fractional suffix
        // outside the seconds field, which a uniform `substringBefore('.')` truncates to a valid
        // field instead of refusing. The rest are the other shapes the parser must refuse rather
        // than guess at.
        listOf("1:xx:03:04", "02:03.5:04", "1.02:03.5:04", "00:05", "00:05:00:00", "xx:05:00", "00:xx:00", "", "abc")
            .forEach { assertEquals(it, null, listOf(SeerrDownloadStatusDto(timeLeft = it)).etaMinutes(NOW)) }

        // And the two it must still accept, so the refusals above cannot pass by refusing everything.
        assertEquals(1_562, listOf(SeerrDownloadStatusDto(timeLeft = "1.02:01:30")).etaMinutes(NOW))
        assertEquals(5, listOf(SeerrDownloadStatusDto(timeLeft = "00:04:30.5")).etaMinutes(NOW))
    }

    @Test
    fun `requested_by is the requester's display name or username, never their email`() {
        fun nameOf(user: SeerrRequestUserDto): String =
            SeerrMediaInfoDto(
                status = SeerrMediaStatusCode.Pending,
                requests = listOf(SeerrRequestSummaryDto(id = 1, status = SeerrRequestStatusCode.Pending, requestedBy = user)),
            ).toRequestStatus(NOW).requestsList.single().requestedBy

        assertEquals("Grace", nameOf(SeerrRequestUserDto(displayName = "Grace", email = "grace@example.com")))
        assertEquals("grace", nameOf(SeerrRequestUserDto(username = "grace", email = "grace@example.com")))
        assertEquals("", nameOf(SeerrRequestUserDto(email = "grace@example.com")))
        // What a server sends for an email-only user: displayName falls back to the email.
        assertEquals("", nameOf(SeerrRequestUserDto(displayName = "grace@example.com", email = "grace@example.com")))
        assertEquals("", nameOf(SeerrRequestUserDto(displayName = "grace@example.com")))
        val both = SeerrRequestUserDto(displayName = "grace@example.com", username = "grace", email = "grace@example.com")
        assertEquals("grace", nameOf(both))
    }

    @Test
    fun `the 4k version's state is carried apart from the standard one, title and seasons`() {
        val info =
            SeerrMediaInfoDto(
                status = SeerrMediaStatusCode.Unknown,
                status4k = SeerrMediaStatusCode.Processing,
                seasons = listOf(SeerrSeasonStatusDto(1, status = SeerrMediaStatusCode.Available, status4k = SeerrMediaStatusCode.Pending)),
            )

        val status = info.toRequestStatus(NOW)

        assertEquals(listOf(false, true), status.versionsList.map { it.is4K })
        assertEquals(Availability.AVAILABILITY_NOT_REQUESTED, status.standardVersion().availability)
        assertEquals(Availability.AVAILABILITY_PROCESSING, status.version4k().availability)
        assertEquals(
            Availability.AVAILABILITY_AVAILABLE,
            status
                .standardVersion()
                .seasonsList
                .single()
                .availability,
        )
        assertEquals(
            Availability.AVAILABILITY_PENDING,
            status
                .version4k()
                .seasonsList
                .single()
                .availability,
        )
        assertEquals(Availability.AVAILABILITY_NOT_REQUESTED, (null as SeerrMediaInfoDto?).toRequestStatus(NOW).version4k().availability)
    }

    @Test
    fun `each version carries its own downloads`() {
        val info =
            SeerrMediaInfoDto(
                downloadStatus = listOf(SeerrDownloadStatusDto(title = "Standard", size = 1_000.0, sizeLeft = 500.0)),
                downloadStatus4k = listOf(SeerrDownloadStatusDto(title = "UHD", size = 3_000.0, sizeLeft = 3_000.0)),
            )

        val status = info.toRequestStatus(NOW)
        assertEquals(1_000L, status.standardVersion().download.totalBytes)
        assertEquals(3_000L, status.version4k().download.totalBytes)
    }

    @Test
    fun `a title downloading only in 4k has a 4k download but no standard one`() {
        val info = SeerrMediaInfoDto(downloadStatus4k = listOf(SeerrDownloadStatusDto(size = 3_000.0, sizeLeft = 1_500.0)))

        val status = info.toRequestStatus(NOW)
        assertEquals(0.5f, status.version4k().download.fraction, 0.0001f)
        assertFalse(status.standardVersion().hasDownload())
    }
}
