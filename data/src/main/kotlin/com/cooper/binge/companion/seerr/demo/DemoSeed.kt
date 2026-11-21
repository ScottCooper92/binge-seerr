package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrDownloadStatusDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode

private const val QUEUED_TIME_LEFT = "01:10:00"

/** One request the demo starts with. */
internal class SeedRequest(
    val title: String,
    val by: Int,
    val hoursAgo: Long,
    val status: SeerrRequestStatusCode = SeerrRequestStatusCode.Pending,
    val seasons: List<Int> = emptyList(),
    val profileId: Int = DEMO_PROFILE_ID,
    val is4k: Boolean = false,
    /** Whether an approved request is being fetched; the seed leaves one approved and waiting. */
    val download: Boolean = true,
    val queue: List<SeerrDownloadStatusDto>? = null,
)

private fun queued(
    status: String,
    left: String,
) = SeerrDownloadStatusDto(title = "Caminandes", size = DOWNLOAD_BYTES, sizeLeft = DOWNLOAD_BYTES, timeLeft = left, status = status)

internal val SEED_REQUESTS =
    listOf(
        SeedRequest("Sintel", by = 2, hoursAgo = 26),
        SeedRequest("Nosferatu", by = DEMO_USER_ID, hoursAgo = 3, profileId = DEMO_HD_PROFILE_ID),
        SeedRequest("Severance", by = 3, hoursAgo = 7, seasons = listOf(2)),
        SeedRequest("Big Buck Bunny", by = 2, hoursAgo = 50, status = SeerrRequestStatusCode.Approved, download = false),
        SeedRequest(
            "The Expanse",
            by = 3,
            hoursAgo = 30,
            status = SeerrRequestStatusCode.Approved,
            seasons = listOf(1, 2),
            profileId = DEMO_HD_PROFILE_ID,
        ),
        SeedRequest("Metropolis", by = 1, hoursAgo = 120, status = SeerrRequestStatusCode.Completed),
        SeedRequest("Night of the Living Dead", by = 2, hoursAgo = 144, status = SeerrRequestStatusCode.Completed),
        SeedRequest("Spirited Away", by = 1, hoursAgo = 216, status = SeerrRequestStatusCode.Completed),
        SeedRequest("Tears of Steel", by = 3, hoursAgo = 96, status = SeerrRequestStatusCode.Declined),
        SeedRequest("The Matrix", by = 2, hoursAgo = 60, status = SeerrRequestStatusCode.Failed),
    ) + SHEET_STATE_REQUESTS

/** What the request sheet's other states need, older than the rest so the lists keep their first rows. */
private val SHEET_STATE_REQUESTS =
    listOf(
        SeedRequest("Severance", by = DEMO_USER_ID, hoursAgo = 300, status = SeerrRequestStatusCode.Completed, seasons = listOf(1)),
        SeedRequest("Severance", by = 2, hoursAgo = 240, status = SeerrRequestStatusCode.Approved, seasons = listOf(3)),
        SeedRequest(
            "Caminandes",
            by = 2,
            hoursAgo = 245,
            status = SeerrRequestStatusCode.Approved,
            queue = listOf(queued("queued", QUEUED_TIME_LEFT), queued("paused", QUEUED_TIME_LEFT)),
        ),
        SeedRequest("Spirited Away", by = 2, hoursAgo = 230, is4k = true),
        SeedRequest("Dune", by = 2, hoursAgo = 250, seasons = listOf(1)),
        SeedRequest("Dune", by = 3, hoursAgo = 255, status = SeerrRequestStatusCode.Approved, seasons = listOf(2), download = false),
        SeedRequest(
            "Dune",
            by = DEMO_USER_ID,
            hoursAgo = 260,
            status = SeerrRequestStatusCode.Approved,
            seasons = listOf(1, 2),
            is4k = true,
            download = false,
        ),
    )
