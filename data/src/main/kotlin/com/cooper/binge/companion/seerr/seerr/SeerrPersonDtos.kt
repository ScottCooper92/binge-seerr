package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET person/{id}` (`server/models/Person.ts`, `PersonDetails`). Every field is optional so a server that leaves one out, or a
 * fork that renames it, still parses. Dates are `yyyy-mm-dd` and an empty string where TMDB has none.
 */
@Serializable
data class SeerrPersonDetailsDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String = "",
    @SerialName("biography") val biography: String? = null,
    @SerialName("birthday") val birthday: String? = null,
    @SerialName("deathday") val deathday: String? = null,
    @SerialName("knownForDepartment") val knownForDepartment: String? = null,
    @SerialName("alsoKnownAs") val alsoKnownAs: List<String> = emptyList(),
    @SerialName("placeOfBirth") val placeOfBirth: String? = null,
    @SerialName("profilePath") val profilePath: String? = null,
    /** TMDB's code: 0 unknown, 1 female, 2 male, 3 non-binary. */
    @SerialName("gender") val gender: Int? = null,
    @SerialName("popularity") val popularity: Double? = null,
    @SerialName("imdbId") val imdbId: String? = null,
    @SerialName("homepage") val homepage: String? = null,
)

/** `GET person/{id}/combined_credits`: the titles a person is in, as cast and as crew. The server drops adult titles. */
@Serializable
data class SeerrPersonCreditsDto(
    @SerialName("cast") val cast: List<SeerrPersonCreditDto> = emptyList(),
    @SerialName("crew") val crew: List<SeerrPersonCreditDto> = emptyList(),
)

/** One credit: [mediaType] is `movie` or `tv`, [character] is set on a cast credit and [job] on a crew one. */
@Serializable
data class SeerrPersonCreditDto(
    @SerialName("id") val id: Int,
    @SerialName("mediaType") val mediaType: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("releaseDate") val releaseDate: String? = null,
    @SerialName("firstAirDate") val firstAirDate: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("voteAverage") val voteAverage: Double? = null,
    @SerialName("character") val character: String? = null,
    @SerialName("job") val job: String? = null,
    @SerialName("mediaInfo") val mediaInfo: SeerrMediaInfoDto? = null,
)
