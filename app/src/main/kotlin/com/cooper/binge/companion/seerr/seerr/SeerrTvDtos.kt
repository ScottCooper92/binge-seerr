package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET tv/{id}`: everything the server returns for a show (`server/models/Tv.ts`, `TvDetails`). Every field is optional so a
 * server that leaves one out, or a fork that renames it, still parses. [SeerrMediaDetailsDto] is the thin lookup the exported
 * Service reads; this is the whole page.
 */
@Serializable
data class SeerrTvDetailsDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String = "",
    @SerialName("tagline") val tagline: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("posterPath") val posterPath: String? = null,
    @SerialName("backdropPath") val backdropPath: String? = null,
    @SerialName("firstAirDate") val firstAirDate: String? = null,
    @SerialName("lastAirDate") val lastAirDate: String? = null,
    @SerialName("episodeRunTime") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("status") val status: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("inProduction") val inProduction: Boolean = false,
    @SerialName("voteAverage") val voteAverage: Double? = null,
    @SerialName("voteCount") val voteCount: Int? = null,
    @SerialName("numberOfSeasons") val numberOfSeasons: Int? = null,
    @SerialName("numberOfEpisodes") val numberOfEpisodes: Int? = null,
    @SerialName("originalLanguage") val originalLanguage: String? = null,
    @SerialName("spokenLanguages") val spokenLanguages: List<SeerrLanguageNameDto> = emptyList(),
    @SerialName("genres") val genres: List<SeerrNamedDto> = emptyList(),
    @SerialName("keywords") val keywords: List<SeerrNamedDto> = emptyList(),
    @SerialName("networks") val networks: List<SeerrNamedDto> = emptyList(),
    @SerialName("createdBy") val createdBy: List<SeerrNamedDto> = emptyList(),
    @SerialName("contentRatings") val contentRatings: SeerrContentRatingsDto? = null,
    @SerialName("seasons") val seasons: List<SeerrTvSeasonDto> = emptyList(),
    @SerialName("nextEpisodeToAir") val nextEpisodeToAir: SeerrEpisodeDto? = null,
    @SerialName("credits") val credits: SeerrCreditsDto? = null,
    @SerialName("relatedVideos") val relatedVideos: List<SeerrVideoDto> = emptyList(),
    @SerialName("watchProviders") val watchProviders: List<SeerrWatchRegionDto> = emptyList(),
    @SerialName("mediaInfo") val mediaInfo: SeerrMediaInfoDto? = null,
)

/** A season as the show lists it. Season 0 is the specials. */
@Serializable
data class SeerrTvSeasonDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("seasonNumber") val seasonNumber: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("overview") val overview: String? = null,
    @SerialName("airDate") val airDate: String? = null,
    @SerialName("episodeCount") val episodeCount: Int = 0,
    @SerialName("posterPath") val posterPath: String? = null,
)

/** `GET tv/{id}/season/{n}`: one season with its episodes. */
@Serializable
data class SeerrSeasonDetailsDto(
    @SerialName("seasonNumber") val seasonNumber: Int,
    @SerialName("name") val name: String? = null,
    @SerialName("airDate") val airDate: String? = null,
    @SerialName("episodes") val episodes: List<SeerrEpisodeDto> = emptyList(),
)

@Serializable
data class SeerrEpisodeDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String = "",
    @SerialName("overview") val overview: String? = null,
    @SerialName("airDate") val airDate: String? = null,
    @SerialName("episodeNumber") val episodeNumber: Int = 0,
    @SerialName("seasonNumber") val seasonNumber: Int = 0,
    @SerialName("stillPath") val stillPath: String? = null,
)
