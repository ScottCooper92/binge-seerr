package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The `pageInfo` every paged list carries; [results] is the total across pages, which is what a count probe reads. */
@Serializable
data class SeerrPageInfoDto(
    @SerialName("results") val results: Int = 0,
    @SerialName("pages") val pages: Int = 0,
    @SerialName("page") val page: Int = 0,
)

/** A paged list read only for its total: `take=1` and the `pageInfo`. */
@Serializable
data class SeerrCountProbeDto(
    @SerialName("pageInfo") val pageInfo: SeerrPageInfoDto = SeerrPageInfoDto(),
)

@Serializable
data class SeerrRequestsPageDto(
    @SerialName("pageInfo") val pageInfo: SeerrPageInfoDto = SeerrPageInfoDto(),
    @SerialName("results") val results: List<SeerrRequestDto> = emptyList(),
)

/** One request as the list serves it: the title is only a TMDB id here, hydrated by a details call. */
@Serializable
data class SeerrRequestDto(
    @SerialName("id") val id: Int,
    @SerialName("status") val status: SeerrRequestStatusCode? = null,
    @SerialName("media") val media: SeerrRequestMediaDto,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("requestedBy") val requestedBy: SeerrRequestUserDto? = null,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("seasons") val seasons: List<SeerrSeasonStatusDto> = emptyList(),
)

@Serializable
data class SeerrRequestMediaDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("tmdbId") val tmdbId: Int,
    @SerialName("mediaType") val mediaType: String,
    @SerialName("status") val status: SeerrMediaStatusCode? = null,
    @SerialName("status4k") val status4k: SeerrMediaStatusCode? = null,
    @SerialName("downloadStatus") val downloadStatus: List<SeerrDownloadStatusDto> = emptyList(),
    @SerialName("downloadStatus4k") val downloadStatus4k: List<SeerrDownloadStatusDto> = emptyList(),
)

/** `GET request/count`: the server-wide totals, per status and per type. */
@Serializable
data class SeerrRequestCountDto(
    @SerialName("total") val total: Int = 0,
    @SerialName("movie") val movie: Int = 0,
    @SerialName("tv") val tv: Int = 0,
    @SerialName("pending") val pending: Int = 0,
    @SerialName("approved") val approved: Int = 0,
    @SerialName("declined") val declined: Int = 0,
    @SerialName("processing") val processing: Int = 0,
    @SerialName("available") val available: Int = 0,
)

/** `GET issue/count`: Overseerr 1.30+ and the whole Jellyseerr lineage. */
@Serializable
data class SeerrIssueCountDto(
    @SerialName("total") val total: Int = 0,
    @SerialName("open") val open: Int = 0,
    @SerialName("closed") val closed: Int = 0,
)

/** `GET user/{id}/quota`: a bucket with no limit is unlimited. */
@Serializable
data class SeerrQuotaDto(
    @SerialName("movie") val movie: SeerrQuotaBucketDto? = null,
    @SerialName("tv") val tv: SeerrQuotaBucketDto? = null,
)

@Serializable
data class SeerrQuotaBucketDto(
    @SerialName("days") val days: Int? = null,
    @SerialName("limit") val limit: Int? = null,
    @SerialName("used") val used: Int? = null,
    @SerialName("remaining") val remaining: Int? = null,
    @SerialName("restricted") val restricted: Boolean = false,
)
