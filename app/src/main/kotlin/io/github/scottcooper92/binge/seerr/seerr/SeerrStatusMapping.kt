package io.github.scottcooper92.binge.seerr.seerr

import com.binge.integration.contracts.request.v1.ApprovalState
import com.binge.integration.contracts.request.v1.Availability
import com.binge.integration.contracts.request.v1.DownloadProgress
import com.binge.integration.contracts.request.v1.DownloadState
import com.binge.integration.contracts.request.v1.RequestInfo
import com.binge.integration.contracts.request.v1.RequestStatus
import com.binge.integration.contracts.request.v1.SeasonAvailability
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.ceil

/**
 * Seerr's view of a title as the contract's [RequestStatus]. A title the server does not track has
 * no `mediaInfo`, which is the contract's `AVAILABILITY_NOT_REQUESTED` with nothing else set.
 *
 * `allowed_actions` is not filled here: it is the intersection of what the user may do and what
 * the title's state admits, which the service computes with the permissions in hand.
 */
fun SeerrMediaInfoDto?.toRequestStatus(nowMillis: Long): RequestStatus {
    val info = this ?: return RequestStatus.newBuilder().setAvailability(Availability.AVAILABILITY_NOT_REQUESTED).build()
    val builder =
        RequestStatus
            .newBuilder()
            .setAvailability(info.status.toAvailability())
            .addAllSeasons(
                info.seasons.map {
                    SeasonAvailability
                        .newBuilder()
                        .setSeasonNumber(it.seasonNumber)
                        .setAvailability(it.status.toAvailability())
                        .build()
                },
            ).addAllRequests(info.requests.map { it.toRequestInfo() })
    info.downloadStatus.toDownloadProgress(nowMillis)?.let(builder::setDownload)
    (info.mediaUrl ?: info.jellyfinMediaUrl ?: info.plexUrl)?.let(builder::setWatchUrl)
    return builder.build()
}

fun SeerrMediaStatusCode?.toAvailability(): Availability =
    when (this) {
        SeerrMediaStatusCode.Pending -> Availability.AVAILABILITY_PENDING
        SeerrMediaStatusCode.Processing -> Availability.AVAILABILITY_PROCESSING
        SeerrMediaStatusCode.PartiallyAvailable -> Availability.AVAILABILITY_PARTIALLY_AVAILABLE
        SeerrMediaStatusCode.Available -> Availability.AVAILABILITY_AVAILABLE
        SeerrMediaStatusCode.Blocklisted -> Availability.AVAILABILITY_BLOCKLISTED
        else -> Availability.AVAILABILITY_NOT_REQUESTED
    }

/**
 * Completed (an approved request that finished downloading) reads as approved; Failed is its own
 * terminal state. Only an unknown or missing status reads as pending — dropping the entry would
 * hide an existing request from the host entirely.
 */
fun SeerrRequestStatusCode?.toApprovalState(): ApprovalState =
    when (this) {
        SeerrRequestStatusCode.Approved, SeerrRequestStatusCode.Completed -> ApprovalState.APPROVAL_STATE_APPROVED
        SeerrRequestStatusCode.Declined -> ApprovalState.APPROVAL_STATE_DECLINED
        SeerrRequestStatusCode.Failed -> ApprovalState.APPROVAL_STATE_FAILED
        else -> ApprovalState.APPROVAL_STATE_PENDING
    }

private fun SeerrRequestSummaryDto.toRequestInfo(): RequestInfo {
    val builder =
        RequestInfo
            .newBuilder()
            .setId(id)
            .setState(status.toApprovalState())
            .addAllSeasonNumbers(seasons.map { it.seasonNumber })
    requestedBy?.displayString()?.let(builder::setRequestedBy)
    createdAt?.toEpochMillisOrNull()?.let(builder::setRequestedAtEpochMs)
    return builder.build()
}

/** Email is a last resort and masked to its local part, so a raw address never reaches the host. */
internal fun SeerrRequestUserDto.displayString(): String? =
    listOfNotNull(displayName, username).firstOrNull { it.isNotBlank() }
        ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }

private const val STATUS_DOWNLOADING = "downloading"
private const val TIME_LEFT_PART_COUNT = 3
private const val MILLIS_PER_MINUTE = 60_000.0

/** One aggregate bar across every active download: summed sizes, the slowest ETA, the first title. */
fun List<SeerrDownloadStatusDto>.toDownloadProgress(nowMillis: Long): DownloadProgress? {
    if (isEmpty()) return null
    val totalSize = sumOf { it.size ?: 0.0 }
    val totalLeft = sumOf { it.sizeLeft ?: 0.0 }
    val fraction = downloadFraction(totalSize, totalLeft)
    val builder =
        DownloadProgress
            .newBuilder()
            .setFraction(fraction)
            .setState(if (isDownloading(fraction)) DownloadState.DOWNLOAD_STATE_DOWNLOADING else DownloadState.DOWNLOAD_STATE_QUEUED)
            .setTotalBytes(totalSize.toLong().coerceAtLeast(0))
    firstOrNull()?.title?.let(builder::setLabel)
    etaMinutes(nowMillis)?.let(builder::setEtaMinutes)
    return builder.build()
}

internal fun List<SeerrDownloadStatusDto>.downloadFraction(
    totalSize: Double = sumOf { it.size ?: 0.0 },
    totalLeft: Double = sumOf { it.sizeLeft ?: 0.0 },
): Float = if (totalSize > 0.0) ((totalSize - totalLeft) / totalSize).toFloat().coerceIn(0f, 1f) else 0f

/** Downloading when any item is transferring, otherwise queued; older servers with no status fall back to progress. */
internal fun List<SeerrDownloadStatusDto>.isDownloading(fraction: Float): Boolean =
    when {
        any { it.status?.equals(STATUS_DOWNLOADING, ignoreCase = true) == true } -> true
        any { it.status != null } -> false
        else -> fraction > 0f
    }

/** The slowest active download's remaining whole minutes, or null when nothing reports one. */
fun List<SeerrDownloadStatusDto>.etaMinutes(nowMillis: Long): Int? =
    mapNotNull { it.remainingMillis(nowMillis) }
        .maxOrNull()
        ?.takeIf { it > 0 }
        ?.let { ceil(it / MILLIS_PER_MINUTE).toInt() }

private fun SeerrDownloadStatusDto.remainingMillis(nowMillis: Long): Long? =
    estimatedCompletionTime?.toEpochMillisOrNull()?.let { it - nowMillis }
        ?: timeLeft?.parseTimeLeftMillis()

internal fun String.toEpochMillisOrNull(): Long? =
    runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()

/** The *arr queue's `hh:mm:ss` or `d.hh:mm:ss`; null on any other shape. */
private fun String.parseTimeLeftMillis(): Long? {
    val dotIndex = indexOf('.')
    val (days, time) =
        if (dotIndex in 1 until indexOf(':')) {
            (substring(0, dotIndex).toLongOrNull() ?: return null) to substring(dotIndex + 1)
        } else {
            0L to this
        }
    val parts = time.split(':')
    if (parts.size != TIME_LEFT_PART_COUNT) return null
    val hours = parts[0].toLongOrNull() ?: return null
    val minutes = parts[1].toLongOrNull() ?: return null
    val seconds = parts[2].substringBefore('.').toLongOrNull() ?: return null
    return (((days * 24 + hours) * 60 + minutes) * 60 + seconds) * 1000
}
