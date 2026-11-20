package com.cooper.binge.companion.seerr.ui.requests

import com.cooper.binge.companion.seerr.data.RequestEntity
import com.cooper.binge.companion.seerr.seerr.HydratedTitle
import com.cooper.binge.companion.seerr.seerr.SeerrApi
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.seerr.SeerrRequestDto
import com.cooper.binge.companion.seerr.seerr.SeerrRequestStatusCode

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
