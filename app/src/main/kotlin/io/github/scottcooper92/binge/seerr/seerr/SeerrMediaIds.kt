package io.github.scottcooper92.binge.seerr.seerr

import com.binge.companion.contracts.v1.MediaId
import com.binge.companion.contracts.v1.MediaType
import io.grpc.Status
import io.grpc.StatusException

/** The wire values Seerr's own API uses for a media type, for the contract translation and the app alike. */
const val SEERR_MEDIA_TYPE_MOVIE = "movie"
const val SEERR_MEDIA_TYPE_TV = "tv"

// The translation between the contract's identity — media type + TMDB id — and Seerr's. This is
// the single most interesting thing a companion does, so it lives in one place: the functions in this file.
//
// Seerr keys its request and blocklist endpoints by TMDB id and a `"movie"`/`"tv"` string, which
// is a rename ([seerrMediaType]). Its issue endpoint keys by the server's OWN media record id, which only exists
// once the server tracks the title — that is the translation that needs a round trip ([recordIdFor]).

/** The server's internal id from a title's [SeerrMediaInfoDto], or `NOT_FOUND` when the server does not track [media]. */
fun SeerrMediaInfoDto?.recordIdFor(media: MediaId): Int =
    this?.id
        ?: throw StatusException(Status.NOT_FOUND.withDescription("Seerr has no record for ${media.mediaType} ${media.tmdbId}"))

/**
 * Seerr's media-type string for a contract [MediaId]; anything but movie or TV is a bad argument. Every path
 * that sends a [MediaId] to Seerr passes through here, so it is also where an unusable one stops: a
 * `tmdb_id` <= 0 is INVALID_ARGUMENT, as the contract's header says, not a request Seerr answers 404 to (#682).
 */
fun MediaId.seerrMediaType(): String {
    if (tmdbId <= 0) throw StatusException(Status.INVALID_ARGUMENT.withDescription("tmdb_id must be positive, was $tmdbId"))
    return when (mediaType) {
        MediaType.MEDIA_TYPE_MOVIE -> SEERR_MEDIA_TYPE_MOVIE
        MediaType.MEDIA_TYPE_TV -> SEERR_MEDIA_TYPE_TV
        MediaType.MEDIA_TYPE_UNSPECIFIED, MediaType.UNRECOGNIZED ->
            throw StatusException(Status.INVALID_ARGUMENT.withDescription("media_type must be movie or tv"))
    }
}

/**
 * The other direction: the contract's [MediaId] for a title as Seerr's request list names it. Null for a
 * media type the contract has no value for, which a caller leaves out rather than guesses at.
 */
fun SeerrRequestMediaDto.toMediaId(): MediaId? {
    val type =
        when (mediaType) {
            SEERR_MEDIA_TYPE_MOVIE -> MediaType.MEDIA_TYPE_MOVIE
            SEERR_MEDIA_TYPE_TV -> MediaType.MEDIA_TYPE_TV
            else -> return null
        }
    return MediaId
        .newBuilder()
        .setMediaType(type)
        .setTmdbId(tmdbId)
        .build()
}

/** True when a Seerr media-type string — [SeerrRequestMediaDto.mediaType] and its like — names a TV show. */
fun String.isSeerrTv(): Boolean = this == SEERR_MEDIA_TYPE_TV

/** The title lookup for [media]: the movie or the TV endpoint, both carrying `mediaInfo`. */
suspend fun SeerrApi.details(media: MediaId): SeerrMediaDetailsDto = details(media.seerrMediaType(), media.tmdbId)

/** The title lookup keyed on Seerr's own raw wire values, for callers that only have those. */
suspend fun SeerrApi.details(
    mediaType: String,
    tmdbId: Int,
): SeerrMediaDetailsDto =
    when (mediaType) {
        SEERR_MEDIA_TYPE_MOVIE -> movieDetails(tmdbId)
        else -> tvDetails(tmdbId)
    }
