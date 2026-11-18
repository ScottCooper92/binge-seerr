package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET movie/{id}`: everything the server returns for a film (`server/models/Movie.ts`, `MovieDetails`). Every field is
 * optional so a server that leaves one out, or a fork that renames it, still parses. [SeerrMediaDetailsDto] is the thin
 * lookup the exported Service reads; this is the whole page.
 */
@Serializable
data class SeerrMovieDetailsDto(
    @SerialName("id") val id: Int,
    @SerialName("title") val title: String = "",
    @SerialName("originalTitle") val originalTitle: String? = null,
    @SerialName("tagline") val tagline: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("runtime") val runtime: Int? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("voteAverage") val voteAverage: Double? = null,
    @SerialName("voteCount") val voteCount: Int? = null,
    @SerialName("budget") val budget: Long? = null,
    @SerialName("revenue") val revenue: Long? = null,
    @SerialName("originalLanguage") val originalLanguage: String? = null,
    @SerialName("homepage") val homepage: String? = null,
    @SerialName("genres") val genres: List<SeerrNamedDto> = emptyList(),
    @SerialName("keywords") val keywords: List<SeerrNamedDto> = emptyList(),
    @SerialName("spokenLanguages") val spokenLanguages: List<SeerrLanguageNameDto> = emptyList(),
    @SerialName("productionCompanies") val productionCompanies: List<SeerrNamedDto> = emptyList(),
    @SerialName("productionCountries") val productionCountries: List<SeerrCountryNameDto> = emptyList(),
    @SerialName("releases") val releases: SeerrReleasesDto? = null,
    @SerialName("credits") val credits: SeerrCreditsDto? = null,
    @SerialName("collection") val collection: SeerrCollectionRefDto? = null,
    @SerialName("relatedVideos") val relatedVideos: List<SeerrVideoDto> = emptyList(),
    @SerialName("watchProviders") val watchProviders: List<SeerrWatchRegionDto> = emptyList(),
    @SerialName("mediaInfo") val mediaInfo: SeerrMediaInfoDto? = null,
)

/** An id and a name: a genre, a keyword or a production company. */
@Serializable
data class SeerrNamedDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String = "",
)

@Serializable
data class SeerrLanguageNameDto(
    @SerialName("iso_639_1") val code: String? = null,
    @SerialName("name") val name: String = "",
)

@Serializable
data class SeerrCountryNameDto(
    @SerialName("iso_3166_1") val code: String? = null,
    @SerialName("name") val name: String = "",
)

@Serializable
data class SeerrCreditsDto(
    @SerialName("cast") val cast: List<SeerrCastDto> = emptyList(),
    @SerialName("crew") val crew: List<SeerrCrewDto> = emptyList(),
)

@Serializable
data class SeerrCastDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String = "",
    @SerialName("character") val character: String? = null,
    @SerialName("profilePath") val profilePath: String? = null,
    @SerialName("order") val order: Int? = null,
)

@Serializable
data class SeerrCrewDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String = "",
    @SerialName("job") val job: String? = null,
    @SerialName("department") val department: String? = null,
    @SerialName("profilePath") val profilePath: String? = null,
)

@Serializable
data class SeerrCollectionRefDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String = "",
    @SerialName("posterPath") val posterPath: String? = null,
)

/** A video the server lists for a title. Only YouTube is served. */
@Serializable
data class SeerrVideoDto(
    @SerialName("key") val key: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("site") val site: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("url") val url: String? = null,
)

/** Where a title streams in one region (an ISO 3166-1 code), as the server maps TMDB's watch providers. */
@Serializable
data class SeerrWatchRegionDto(
    @SerialName("iso_3166_1") val region: String = "",
    @SerialName("link") val link: String? = null,
    @SerialName("flatrate") val flatrate: List<SeerrMovieWatchProviderDto> = emptyList(),
    @SerialName("buy") val buy: List<SeerrMovieWatchProviderDto> = emptyList(),
)

@Serializable
data class SeerrMovieWatchProviderDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String = "",
    @SerialName("logoPath") val logoPath: String? = null,
    @SerialName("displayPriority") val displayPriority: Int? = null,
)

/** `GET movie/{id}/ratingscombined`: either score may be missing, and the call is a 404 when the server has neither. */
@Serializable
data class SeerrRatingsDto(
    @SerialName("rt") val rottenTomatoes: SeerrRottenTomatoesDto? = null,
    @SerialName("imdb") val imdb: SeerrImdbDto? = null,
)

@Serializable
data class SeerrRottenTomatoesDto(
    @SerialName("criticsScore") val criticsScore: Int? = null,
    @SerialName("audienceScore") val audienceScore: Int? = null,
    @SerialName("url") val url: String? = null,
)

@Serializable
data class SeerrImdbDto(
    @SerialName("criticsScore") val criticsScore: Double? = null,
    @SerialName("url") val url: String? = null,
)
