package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A title lookup; [mediaInfo] is absent when the title is not known to the server. A movie carries
 * [title] and [releaseDate], a show [name] and [firstAirDate]; [posterPath] is a TMDB path.
 */
@Serializable
data class SeerrMediaDetailsDto(
    @SerialName("mediaInfo") val mediaInfo: SeerrMediaInfoDto? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("firstAirDate") val firstAirDate: String? = null,
    /** A show's seasons as TMDB lists them; specials are season 0. */
    @SerialName("seasons") val seasons: List<SeerrSeasonDto> = emptyList(),
    /** A movie's release dates by country, which carry its certification. */
    @SerialName("releases") val releases: SeerrReleasesDto? = null,
    /** A show's age ratings by country. */
    @SerialName("contentRatings") val contentRatings: SeerrContentRatingsDto? = null,
) {
    val displayTitle: String? get() = title ?: name

    /** The age rating for [region] (`US`, `GB`), falling back to the US one; null when the title has none there. */
    fun certification(region: String): String? =
        listOf(region, FALLBACK_CERTIFICATION_REGION).firstNotNullOfOrNull { country ->
            val movie =
                releases
                    ?.results
                    ?.firstOrNull { it.country == country }
                    ?.releaseDates
                    ?.firstNotNullOfOrNull { it.certification.takeIf(String::isNotBlank) }
            val show =
                contentRatings
                    ?.results
                    ?.firstOrNull { it.country == country }
                    ?.rating
                    ?.takeIf(String::isNotBlank)
            movie ?: show
        }

    val year: String? get() = (releaseDate ?: firstAirDate)?.take(YEAR_LENGTH)?.takeIf { it.length == YEAR_LENGTH }
}

/** A `yyyy-mm-dd` date's year; a serializable class cannot hide this in a private companion. */
private const val YEAR_LENGTH = 4
private const val FALLBACK_CERTIFICATION_REGION = "US"

@Serializable
data class SeerrReleasesDto(
    @SerialName("results") val results: List<SeerrCountryReleasesDto> = emptyList(),
)

@Serializable
data class SeerrCountryReleasesDto(
    @SerialName("iso_3166_1") val country: String = "",
    @SerialName("release_dates") val releaseDates: List<SeerrReleaseDateDto> = emptyList(),
)

@Serializable
data class SeerrReleaseDateDto(
    @SerialName("certification") val certification: String = "",
)

@Serializable
data class SeerrContentRatingsDto(
    @SerialName("results") val results: List<SeerrContentRatingDto> = emptyList(),
)

@Serializable
data class SeerrContentRatingDto(
    @SerialName("iso_3166_1") val country: String = "",
    @SerialName("rating") val rating: String = "",
)

@Serializable
data class SeerrMediaInfoDto(
    /** The server's internal media id — the issue endpoint keys on this, not the TMDB id. */
    @SerialName("id") val id: Int? = null,
    @SerialName("status") val status: SeerrMediaStatusCode? = null,
    /** The 4K version's status, which Seerr tracks apart from [status]: a 4K request moves only this one. */
    @SerialName("status4k") val status4k: SeerrMediaStatusCode? = null,
    @SerialName("seasons") val seasons: List<SeerrSeasonStatusDto> = emptyList(),
    @SerialName("downloadStatus") val downloadStatus: List<SeerrDownloadStatusDto> = emptyList(),
    /** The 4K version's active downloads, which Seerr tracks apart from [downloadStatus] on the 4K server. */
    @SerialName("downloadStatus4k") val downloadStatus4k: List<SeerrDownloadStatusDto> = emptyList(),
    @SerialName("requests") val requests: List<SeerrRequestSummaryDto> = emptyList(),
    /** Link to the title in the media server's web UI; [mediaUrl] is general, the others server-specific. */
    @SerialName("mediaUrl") val mediaUrl: String? = null,
    @SerialName("jellyfinMediaUrl") val jellyfinMediaUrl: String? = null,
    @SerialName("plexUrl") val plexUrl: String? = null,
    /**
     * Link to the 4K version on the 4K media server: [mediaUrl4k] on Seerr and Jellyseerr, [plexUrl4k] on Overseerr. The
     * server sets it only once the 4K version is in that server's library.
     */
    @SerialName("mediaUrl4k") val mediaUrl4k: String? = null,
    @SerialName("plexUrl4k") val plexUrl4k: String? = null,
)

@Serializable
data class SeerrRequestSummaryDto(
    @SerialName("id") val id: Int,
    @SerialName("status") val status: SeerrRequestStatusCode? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("requestedBy") val requestedBy: SeerrRequestUserDto? = null,
    @SerialName("is4k") val is4k: Boolean = false,
    /** The seasons this request covers; empty for movie requests. */
    @SerialName("seasons") val seasons: List<SeerrSeasonStatusDto> = emptyList(),
    /** The destination the request was sent with; ids into the `service/{radarr,sonarr}` lists. */
    @SerialName("serverId") val serverId: Int? = null,
    @SerialName("profileId") val profileId: Int? = null,
)

/** Whichever of these the server filled in names the requester. */
@Serializable
data class SeerrRequestUserDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("username") val username: String? = null,
    @SerialName("email") val email: String? = null,
    /** The author's bitmask, present on a comment's user; what an Admin tag on a comment reads. */
    @SerialName("permissions") val permissions: Int? = null,
    /** The user's picture, as the users list and a user's own page read it; see [toAvatarUrl]. */
    @SerialName("avatar") val avatar: String? = null,
)

@Serializable
data class SeerrSeasonDto(
    @SerialName("seasonNumber") val seasonNumber: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("episodeCount") val episodeCount: Int = 0,
)

@Serializable
data class SeerrSeasonStatusDto(
    @SerialName("seasonNumber") val seasonNumber: Int,
    @SerialName("status") val status: SeerrMediaStatusCode? = null,
    @SerialName("status4k") val status4k: SeerrMediaStatusCode? = null,
)

/** A single in-flight download from the *arr backend. Sizes are bytes, read as Double to tolerate either form. */
@Serializable
data class SeerrDownloadStatusDto(
    @SerialName("title") val title: String? = null,
    @SerialName("size") val size: Double? = null,
    @SerialName("sizeLeft") val sizeLeft: Double? = null,
    /** ISO timestamp the backend expects the download to finish. */
    @SerialName("estimatedCompletionTime") val estimatedCompletionTime: String? = null,
    /** Remaining time as the *arr queue reports it: `hh:mm:ss`, or `d.hh:mm:ss` beyond a day. */
    @SerialName("timeLeft") val timeLeft: String? = null,
    /** The *arr queue status, e.g. `downloading`, `queued`, `delay`, `paused`; absent on older servers. */
    @SerialName("status") val status: String? = null,
)

/**
 * `POST media/{id}/{status}`: which instance the new status is for. Always encoded, since the
 * server reads the flag off the body and a missing body is what the 500 comes from.
 *
 * [seasons] only takes effect for `status == available` on a TV title - the server marks each
 * numbered season available there and ignores the field for every other status and for a movie.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeerrMediaStatusBody(
    @EncodeDefault @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("seasons") val seasons: List<SeerrMediaStatusSeasonBody>? = null,
)

@Serializable
data class SeerrMediaStatusSeasonBody(
    @SerialName("seasonNumber") val seasonNumber: Int,
)
