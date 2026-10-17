package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A page of `GET {blocklistPath}`, newest first. */
@Serializable
data class SeerrBlocklistPageDto(
    @SerialName("pageInfo") val pageInfo: SeerrPageInfoDto = SeerrPageInfoDto(),
    @SerialName("results") val results: List<SeerrBlocklistEntryDto> = emptyList(),
)

/**
 * One blocklisted title. The payload carries the title the blocker gave but no artwork;
 * [blocklistedTags] is Seerr's comma-separated list of the tags that blocked it, absent for a
 * manual block.
 */
@Serializable
data class SeerrBlocklistEntryDto(
    @SerialName("id") val id: Int,
    @SerialName("tmdbId") val tmdbId: Int? = null,
    @SerialName("mediaType") val mediaType: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("createdAt") val createdAt: String? = null,
    @SerialName("user") val user: SeerrRequestUserDto? = null,
    @SerialName("blocklistedTags") val blocklistedTags: String? = null,
)

@Serializable
data class SeerrAddToBlocklistBody(
    @SerialName("tmdbId") val tmdbId: Int,
    @SerialName("mediaType") val mediaType: String,
    @SerialName("title") val title: String,
    /** The blocking user's id. The server's own schema requires a number here; omitting it is a 500, not a 400. */
    @SerialName("user") val user: Int,
)
