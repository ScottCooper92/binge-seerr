package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One page of a discover, search or watchlist call; pages count from 1. */
@Serializable
data class SeerrTitlePageDto(
    @SerialName("page") val page: Int = 1,
    @SerialName("totalPages") val totalPages: Int = 1,
    @SerialName("totalResults") val totalResults: Int = 0,
    @SerialName("results") val results: List<SeerrTitleResultDto> = emptyList(),
)

/**
 * One row of a mixed result. [mediaType] is `movie`, `tv`, `person` or `collection` on trending, search and the watchlist, and
 * absent from the lists that hold one kind only. A movie has [title] and [releaseDate], a show [name] and [firstAirDate], a
 * person [name] and [profilePath]. [mediaInfo] is what the server knows of the title, so a card shows its status without
 * another call. The watchlist names the title by [tmdbId] where the other lists use [id].
 */
@Serializable
data class SeerrTitleResultDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("tmdbId") val tmdbId: Int? = null,
    @SerialName("mediaType") val mediaType: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("profilePath") val profilePath: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("firstAirDate") val firstAirDate: String? = null,
    @SerialName("voteAverage") val voteAverage: Double? = null,
    @SerialName("mediaInfo") val mediaInfo: SeerrMediaInfoDto? = null,
)

/** A genre slider tile: the genre and the backdrops TMDB holds for it. */
@Serializable
data class SeerrGenreSliderDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String = "",
    @SerialName("backdrops") val backdrops: List<String> = emptyList(),
)
