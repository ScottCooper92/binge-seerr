package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrDownloadStatusDto
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestDto
import com.cooper.binge.companion.seerr.seerr.displayString
import com.cooper.binge.companion.seerr.seerr.downloadFraction
import com.cooper.binge.companion.seerr.seerr.etaMinutes
import com.cooper.binge.companion.seerr.seerr.isDownloading
import com.cooper.binge.companion.seerr.seerr.toAvatarUrl
import com.cooper.binge.companion.seerr.seerr.toEpochMillisOrNull

const val REQUESTS_PAGE_SIZE = 20

/** Null for a request whose media type this app does not render; there is nothing to show. */
suspend fun SeerrRequestDto.toRequestItem(
    api: SeerrApi,
    hydrate: suspend (SeerrApi, String, Int) -> HydratedTitle?,
    nowMillis: Long,
): RequestItem? {
    val mediaType = media.mediaType.toRequestMediaTypeOrNull() ?: return null
    val details = hydrate(api, media.mediaType, media.tmdbId)
    val statuses = if (is4k) media.downloadStatus4k else media.downloadStatus
    return RequestItem(
        id = id,
        tmdbId = media.tmdbId,
        mediaType = mediaType,
        title = details?.title,
        posterUrl = details?.posterUrl,
        year = details?.year,
        requestedBy = requestedBy?.displayString(),
        requestedById = requestedBy?.id,
        requestedByAvatarUrl = requestedBy?.avatar.toAvatarUrl(),
        requestedAtMillis = createdAt?.toEpochMillisOrNull(),
        status = status,
        mediaStatus = mediaStatusOfRequestedVersion(),
        download = statuses.toDownload(nowMillis),
        seasonNumbers = seasons.map { it.seasonNumber },
        is4k = is4k,
        backdropUrl = details?.backdropUrl,
        overview = details?.overview,
        certification = details?.certification,
    )
}

/** The media's status for the version this request asked for: the 4K one for a 4K request, the regular one otherwise. */
internal fun SeerrRequestDto.mediaStatusOfRequestedVersion(): SeerrMediaStatusCode? = if (is4k) media.status4k else media.status

private fun List<SeerrDownloadStatusDto>.toDownload(nowMillis: Long): RequestDownload? {
    if (isEmpty()) return null
    val fraction = downloadFraction()
    return RequestDownload(fraction = fraction, etaMinutes = etaMinutes(nowMillis), downloading = isDownloading(fraction))
}
