package com.cooper.binge.companion.seerr.seerr

import com.binge.companion.contracts.request.v1.ApprovalState
import com.binge.companion.contracts.request.v1.Availability
import com.binge.companion.contracts.request.v1.DownloadProgress
import com.binge.companion.contracts.request.v1.DownloadState
import com.binge.companion.contracts.request.v1.RequestInfo
import com.binge.companion.contracts.request.v1.RequestStatus
import com.binge.companion.contracts.request.v1.SeasonAvailability
import com.binge.companion.contracts.request.v1.VersionStatus
import kotlin.math.ceil
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Seerr's view of a title as the contract's [RequestStatus]. No `mediaInfo` means `AVAILABILITY_NOT_REQUESTED`.
 *
 * One `VersionStatus` per version, never folded together (#704, #724). Both are always filled here; the
 * service drops the 4K one unless `SeerrPermissions.mayRequest4k`. Each carries its own watch link, so a
 * 4K server link goes out only to a user who may reach it (#1526).
 *
 * `allowed_actions` is left empty: the service fills it with the permissions in hand. [isTv] says which
 * *arr a request's ids point into, which [qualityLabels] needs to name its quality profile.
 */
fun SeerrMediaInfoDto?.toRequestStatus(
    nowMillis: Long,
    isTv: Boolean = false,
    qualityLabels: QualityLabels = QualityLabels.None,
): RequestStatus {
    val info =
        this ?: return RequestStatus
            .newBuilder()
            .addVersions(version(is4k = false, Availability.AVAILABILITY_NOT_REQUESTED))
            .addVersions(version(is4k = true, Availability.AVAILABILITY_NOT_REQUESTED))
            .build()
    val standard =
        version(is4k = false, info.status.toAvailability())
            .addAllSeasons(info.seasons.map { it.toSeasonAvailability(it.status) })
    info.downloadStatus.toDownloadProgress(nowMillis)?.let(standard::setDownload)
    watchLink(info.mediaUrl, info.jellyfinMediaUrl, info.plexUrl)?.let(standard::setWatchUrl)
    val fourK =
        version(is4k = true, info.status4k.toAvailability())
            .addAllSeasons(info.seasons.map { it.toSeasonAvailability(it.status4k) })
    info.downloadStatus4k.toDownloadProgress(nowMillis)?.let(fourK::setDownload)
    watchLink(info.mediaUrl4k, info.plexUrl4k)?.let(fourK::setWatchUrl)
    return RequestStatus
        .newBuilder()
        .addVersions(standard)
        .addVersions(fourK)
        .addAllRequests(info.requests.map { it.toRequestInfo(qualityLabels.labelOf(it.destination(isTv))) })
        .build()
}

/** The first of a version's links the server filled in, if it is http or https: the contract carries no other scheme. */
private fun watchLink(vararg candidates: String?): String? = candidates.firstOrNull { !it.isNullOrBlank() }?.takeIf { it.isWebUrl() }

private fun version(
    is4k: Boolean,
    availability: Availability,
): VersionStatus.Builder = VersionStatus.newBuilder().setVersionId(versionIdOf(is4k)).setAvailability(availability)

private fun SeerrSeasonStatusDto.toSeasonAvailability(status: SeerrMediaStatusCode?): SeasonAvailability =
    SeasonAvailability
        .newBuilder()
        .setSeasonNumber(seasonNumber)
        .setAvailability(status.toAvailability())
        .build()

/** Who made each request, by user id; a request whose requester the server did not name is left out. */
fun SeerrMediaInfoDto?.requesterIds(): Map<Int, Int> =
    this
        ?.requests
        .orEmpty()
        .mapNotNull { request -> request.requestedBy?.id?.let { request.id to it } }
        .toMap()

/**
 * Deleted reads as not requested, which is what the server means by it: Jellyseerr's request
 * creation groups Deleted with Unknown and resets the row to Pending, and its duplicate guard
 * exempts completed requests, so the rows that survive a delete do not block a fresh one. The
 * contract has no Deleted of its own and does not need one for this.
 */
fun SeerrMediaStatusCode?.toAvailability(): Availability =
    when (this) {
        SeerrMediaStatusCode.Pending -> Availability.AVAILABILITY_PENDING
        SeerrMediaStatusCode.Processing -> Availability.AVAILABILITY_PROCESSING
        SeerrMediaStatusCode.PartiallyAvailable -> Availability.AVAILABILITY_PARTIALLY_AVAILABLE
        SeerrMediaStatusCode.Available -> Availability.AVAILABILITY_AVAILABLE
        SeerrMediaStatusCode.Blocklisted -> Availability.AVAILABILITY_BLOCKLISTED
        SeerrMediaStatusCode.Deleted -> Availability.AVAILABILITY_NOT_REQUESTED
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

/** One request as the request list serves it, without `allowed_actions`: as for a status, the service fills those in. */
fun SeerrRequestDto.toRequestInfo(qualityLabels: QualityLabels = QualityLabels.None): RequestInfo =
    SeerrRequestSummaryDto(id = id, status = status, createdAt = createdAt, requestedBy = requestedBy, is4k = is4k, seasons = seasons)
        .toRequestInfo(qualityLabels.labelOf(destination()))

private fun SeerrRequestSummaryDto.toRequestInfo(qualityLabel: String?): RequestInfo {
    val builder =
        RequestInfo
            .newBuilder()
            .setId(id.toString())
            .setState(status.toApprovalState())
            .addAllSeasonNumbers(seasons.map { it.seasonNumber })
            .setVersionId(versionIdOf(is4k))
    requestedBy?.contractName()?.let(builder::setRequestedBy)
    createdAt?.toEpochMillisOrNull()?.let(builder::setRequestedAtEpochMs)
    qualityLabel?.let(builder::setQualityLabel)
    return builder.build()
}

private const val STATUS_DOWNLOADING = "downloading"
private const val TIME_LEFT_FIELD_COUNT = 3
private const val MILLIS_PER_MINUTE = 60_000.0

/** One aggregate bar across one version's active downloads: summed sizes, the slowest ETA, the first title. */
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

/**
 * The *arr queue's `hh:mm:ss` or `d.hh:mm:ss`; null on any other shape, including one where a
 * field fails to parse. The raw split is checked against the field count first, so a malformed
 * input can't lose a field to `mapNotNull` and disguise itself as a shorter, valid shape; the
 * count is checked again after parsing, so a field that drops out still fails the count. Only the
 * seconds field tolerates a fractional suffix (`substringBefore('.')`); hours and minutes are
 * parsed as plain integers, so a stray fraction there fails to parse rather than being truncated.
 */
private fun String.parseTimeLeftMillis(): Long? {
    val dayBreak = indexOf('.').takeIf { it in 1 until indexOf(':') }
    val days = ((dayBreak?.let { substring(0, it) } ?: "0").toLongOrNull() ?: return null).days
    return substring(dayBreak?.plus(1) ?: 0)
        .split(':')
        .takeIf { it.size == TIME_LEFT_FIELD_COUNT }
        ?.mapIndexed { index, field -> if (index == TIME_LEFT_FIELD_COUNT - 1) field.substringBefore('.') else field }
        ?.mapNotNull { it.toLongOrNull() }
        ?.takeIf { it.size == TIME_LEFT_FIELD_COUNT }
        ?.let { (hours, minutes, seconds) -> (days + hours.hours + minutes.minutes + seconds.seconds).inWholeMilliseconds }
}
