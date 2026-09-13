package io.github.scottcooper92.binge.seerr.seerr

import com.binge.integration.contracts.v1.MediaId
import com.binge.integration.contracts.v1.MediaType
import io.grpc.Status
import io.grpc.StatusException

/** The wire values Seerr's own API uses for a media type. */
private const val SEERR_MEDIA_TYPE_MOVIE = "movie"
private const val SEERR_MEDIA_TYPE_TV = "tv"

/**
 * The translation between the contract's identity — media type + TMDB id — and Seerr's. This is
 * the single most interesting thing a companion does, so it lives in one place.
 *
 * Seerr keys its request and blocklist endpoints by TMDB id and a `"movie"`/`"tv"` string, which
 * is a rename. Its issue endpoint keys by the server's OWN media record id, which only exists once
 * the server tracks the title — that is the translation that needs a round trip.
 */
class SeerrMediaIds(
    private val api: suspend () -> SeerrApi,
) {
    /** The server's internal id for [media], or `NOT_FOUND` when the server does not track the title. */
    suspend fun mediaRecordId(media: MediaId): Int =
        api()
            .details(media)
            .mediaInfo
            ?.id
            ?: throw StatusException(Status.NOT_FOUND.withDescription("Seerr has no record for ${media.mediaType} ${media.tmdbId}"))
}

/** Seerr's media-type string for a contract [MediaId]; anything but movie or TV is a bad argument. */
fun MediaId.seerrMediaType(): String =
    when (mediaType) {
        MediaType.MEDIA_TYPE_MOVIE -> SEERR_MEDIA_TYPE_MOVIE
        MediaType.MEDIA_TYPE_TV -> SEERR_MEDIA_TYPE_TV
        MediaType.MEDIA_TYPE_UNSPECIFIED, MediaType.UNRECOGNIZED ->
            throw StatusException(Status.INVALID_ARGUMENT.withDescription("media_type must be movie or tv"))
    }

/** True when a Seerr media-type string — [SeerrRequestMediaDto.mediaType] and its like — names a TV show. */
fun String.isSeerrTv(): Boolean = this == SEERR_MEDIA_TYPE_TV

/** The title lookup for [media]: the movie or the TV endpoint, both carrying `mediaInfo`. */
suspend fun SeerrApi.details(media: MediaId): SeerrMediaDetailsDto =
    when (media.seerrMediaType()) {
        SEERR_MEDIA_TYPE_MOVIE -> movieDetails(media.tmdbId)
        else -> tvDetails(media.tmdbId)
    }
