package com.cooper.binge.companion.seerr.demo

import com.cooper.binge.companion.seerr.seerr.SeerrDownloadStatusDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrSeasonStatusDto
import com.cooper.binge.companion.seerr.seerr.SeerrWatchDataDto
import com.cooper.binge.companion.seerr.seerr.SeerrWatchStatsDto
import java.time.Duration

private const val DOWNLOAD_MINUTES_LEFT = 18L
internal const val DOWNLOAD_BYTES = 4_300_000_000.0
private const val DOWNLOAD_LEFT_BYTES = 1_900_000_000.0
private const val DOWNLOAD_TIME_LEFT = "00:18:00"

/** The two titles whose watch counts the demo answers; a Jellyfin server has none, so this is a demo-only answer. */
private val WATCHED_TMDB_IDS = setOf(19, 129)
private const val PLAYS = 12
private const val SAM = 2
private const val JORDAN = 3
private const val PLAYS_LAST_WEEK = 2
private const val PLAYS_LAST_MONTH = 5

/** A series is partly available when some of its requested seasons are in the library and others are still on their way. */
internal fun DemoData.partlyAvailable(
    title: DemoTitle,
    own: List<DemoRequest>,
) = !title.isMovie &&
    own.any { it.status == SeerrRequestStatusCode.Completed } &&
    own.any { it.status == SeerrRequestStatusCode.Pending || it.status == SeerrRequestStatusCode.Approved }

/** The 4K version's status, which Seerr tracks apart from the standard one; null while nothing 4K has been requested. */
internal fun DemoData.mediaStatus4k(title: DemoTitle): SeerrMediaStatusCode? {
    val own = requests.filter { it.title == title && it.is4k }
    return when {
        own.any { it.status == SeerrRequestStatusCode.Completed } -> SeerrMediaStatusCode.Available
        own.any { it.status == SeerrRequestStatusCode.Approved } -> SeerrMediaStatusCode.Processing
        own.any { it.status == SeerrRequestStatusCode.Pending } -> SeerrMediaStatusCode.Pending
        else -> null
    }
}

/** Each requested season of a series with where it is: in the library, on its way, or waiting for approval. */
internal fun DemoData.seasonStatuses(title: DemoTitle): List<SeerrSeasonStatusDto> =
    requests
        .filter { it.title == title && !it.is4k }
        .flatMap { request -> request.seasons.map { SeerrSeasonStatusDto(it, status = seasonStatus(request.status)) } }
        .sortedBy { it.seasonNumber }

internal fun DemoData.seasonStatus(status: SeerrRequestStatusCode): SeerrMediaStatusCode? =
    when (status) {
        SeerrRequestStatusCode.Completed -> SeerrMediaStatusCode.Available
        SeerrRequestStatusCode.Approved -> SeerrMediaStatusCode.Processing
        SeerrRequestStatusCode.Pending -> SeerrMediaStatusCode.Pending
        else -> null
    }

internal fun DemoData.downloads(title: DemoTitle): List<SeerrDownloadStatusDto> = queueOf(title, is4k = false)

internal fun DemoData.downloads4k(title: DemoTitle): List<SeerrDownloadStatusDto> = queueOf(title, is4k = true)

private fun DemoData.queueOf(
    title: DemoTitle,
    is4k: Boolean,
): List<SeerrDownloadStatusDto> =
    requests
        .filter { it.title == title && it.is4k == is4k && it.download }
        .flatMap { it.queue ?: listOf(plainDownload(title)) }

private fun DemoData.plainDownload(title: DemoTitle) =
    SeerrDownloadStatusDto(
        title = title.title,
        size = DOWNLOAD_BYTES,
        sizeLeft = DOWNLOAD_LEFT_BYTES,
        estimatedCompletionTime = now().plus(Duration.ofMinutes(DOWNLOAD_MINUTES_LEFT)).toString(),
        timeLeft = DOWNLOAD_TIME_LEFT,
        status = "downloading",
    )

/** When the title reached the library: the latest of its completed standard requests, or null while it is not there. */
internal fun DemoData.mediaAddedAt(title: DemoTitle): String? {
    val completed = requests.filter { it.title == title && !it.is4k && it.status == SeerrRequestStatusCode.Completed }
    return completed.maxOfOrNull { it.updatedAt }?.toString()
}

/** The watch counts for a title in the library. A Jellyfin server returns none; the demo answers two titles so the row can be seen. */
internal fun DemoData.watchData(title: DemoTitle): SeerrWatchDataDto? =
    title.takeIf { it.tmdbId in WATCHED_TMDB_IDS && mediaAddedAt(it) != null }?.let {
        SeerrWatchDataDto(
            data =
                SeerrWatchStatsDto(
                    playCount = PLAYS,
                    playCount7Days = PLAYS_LAST_WEEK,
                    playCount30Days = PLAYS_LAST_MONTH,
                    users = listOfNotNull(user(SAM), user(JORDAN)),
                ),
        )
    }
