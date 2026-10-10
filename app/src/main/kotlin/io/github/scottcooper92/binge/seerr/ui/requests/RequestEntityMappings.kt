package io.github.scottcooper92.binge.seerr.ui.requests

import io.github.scottcooper92.binge.seerr.data.RequestEntity
import io.github.scottcooper92.binge.seerr.seerr.HydratedTitle
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaStatusCode
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode

/** The row as the mediator caches it, in the list it was loaded for; null for a request the app cannot show. */
suspend fun SeerrRequestDto.toRequestEntity(
    api: SeerrApi,
    hydrate: suspend (SeerrApi, String, Int) -> HydratedTitle?,
    listKey: String,
    orderIndex: Int,
    nowMillis: Long,
): RequestEntity? = toRequestItem(api, hydrate, nowMillis)?.toEntity(listKey, orderIndex)

fun RequestItem.toEntity(
    listKey: String,
    orderIndex: Int,
): RequestEntity =
    RequestEntity(
        listKey = listKey,
        id = id,
        tmdbId = tmdbId,
        mediaType = mediaType.name,
        title = title,
        posterUrl = posterUrl,
        year = year,
        requestedBy = requestedBy,
        requestedById = requestedById,
        requestedByAvatarUrl = requestedByAvatarUrl,
        requestedAtMillis = requestedAtMillis,
        status = status?.raw,
        mediaStatus = mediaStatus?.raw,
        downloadFraction = download?.fraction,
        downloadEtaMinutes = download?.etaMinutes,
        downloading = download?.downloading ?: false,
        seasonNumbers = seasonNumbers.joinToString(","),
        is4k = is4k,
        orderIndex = orderIndex,
        backdropUrl = backdropUrl,
        overview = overview,
        certification = certification,
    )

fun RequestEntity.toRequestItem(): RequestItem =
    RequestItem(
        id = id,
        tmdbId = tmdbId,
        mediaType = RequestMediaType.valueOf(mediaType),
        title = title,
        posterUrl = posterUrl,
        year = year,
        requestedBy = requestedBy,
        requestedById = requestedById,
        requestedByAvatarUrl = requestedByAvatarUrl,
        requestedAtMillis = requestedAtMillis,
        status = status?.let(::SeerrRequestStatusCode),
        mediaStatus = mediaStatus?.let(::SeerrMediaStatusCode),
        download = downloadFraction?.let { RequestDownload(fraction = it, etaMinutes = downloadEtaMinutes, downloading = downloading) },
        seasonNumbers = seasonNumbers.split(',').mapNotNull { it.toIntOrNull() },
        is4k = is4k,
        backdropUrl = backdropUrl,
        overview = overview,
        certification = certification,
    )
