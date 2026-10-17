package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One discover slider (`settings/discover`): the web client's Discover row, built-in or the
 * admin's own. [type] is the server's `DiscoverSliderType` number; [data] is what a custom slider
 * queries — ids, a code or a search — in the shape the web client stores.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeerrDiscoverSliderDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("type") val type: Int,
    @SerialName("title") val title: String? = null,
    @EncodeDefault @SerialName("isBuiltIn") val isBuiltIn: Boolean = false,
    @EncodeDefault @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("data") val data: String? = null,
)

/** `POST settings/discover/add` and `PUT settings/discover/{id}`: a custom slider's title, type and data. */
@Serializable
data class SeerrDiscoverSliderBody(
    @SerialName("title") val title: String,
    @SerialName("type") val type: Int,
    @SerialName("data") val data: String,
)

/** One country in TMDB's lists, by its ISO 3166-1 code. */
@Serializable
data class SeerrRegionDto(
    @SerialName("iso_3166_1") val code: String = "",
    @SerialName("english_name") val englishName: String? = null,
)

/** One language in TMDB's list, by its ISO 639-1 code. */
@Serializable
data class SeerrLanguageDto(
    @SerialName("iso_639_1") val code: String = "",
    @SerialName("english_name") val englishName: String? = null,
)

/** One TMDB genre: the id an override rule keeps, and its name in the language asked for. */
@Serializable
data class SeerrGenreDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String? = null,
)

/** One TMDB keyword: what a blocklisted tag is. */
@Serializable
data class SeerrKeywordDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String? = null,
)

/** A page of TMDB's keyword search, as the server passes it through. */
@Serializable
data class SeerrKeywordPageDto(
    @SerialName("results") val results: List<SeerrKeywordDto> = emptyList(),
)

/** A TMDB company or network: what a Discover slider's studio or network is, by id. */
@Serializable
data class SeerrCompanyDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String? = null,
)

/** A page of TMDB's company search, as the server passes it through. */
@Serializable
data class SeerrCompanyPageDto(
    @SerialName("results") val results: List<SeerrCompanyDto> = emptyList(),
)

/** One streaming provider TMDB lists in a region. */
@Serializable
data class SeerrWatchProviderDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("name") val name: String? = null,
)
