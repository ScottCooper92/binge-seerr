package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `user/{id}/watch_data`: Tautulli's plays for one user, and what they watched last. */
@Serializable
data class SeerrUserWatchDataDto(
    @SerialName("playCount") val playCount: Int? = null,
    @SerialName("recentlyWatched") val recentlyWatched: List<SeerrWatchedMediaDto> = emptyList(),
)

@Serializable
data class SeerrWatchedMediaDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("tmdbId") val tmdbId: Int,
    @SerialName("mediaType") val mediaType: String,
)

/** `user/{id}/watchlist`: the media server's watchlist, paged by page number rather than `skip`. */
@Serializable
data class SeerrWatchlistPageDto(
    @SerialName("page") val page: Int = 1,
    @SerialName("totalPages") val totalPages: Int = 1,
    @SerialName("totalResults") val totalResults: Int = 0,
    @SerialName("results") val results: List<SeerrWatchlistItemDto> = emptyList(),
)

@Serializable
data class SeerrWatchlistItemDto(
    @SerialName("tmdbId") val tmdbId: Int,
    @SerialName("mediaType") val mediaType: String,
    @SerialName("title") val title: String? = null,
)
