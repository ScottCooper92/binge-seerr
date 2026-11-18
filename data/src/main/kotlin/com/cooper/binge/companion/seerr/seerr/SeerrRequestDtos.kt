package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A media request. The advanced-option overrides are omitted when absent, so a plain request posts
 * exactly as one and the server applies its per-instance defaults. `is4k` is not one of those
 * overrides — it's a flag the server always reads off the body, so it stays encoded even at its
 * `false` default (as [SeerrMediaStatusBody.is4k] already does).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeerrRequestBody(
    @SerialName("mediaType") val mediaType: String,
    @SerialName("mediaId") val mediaId: Int,
    @SerialName("seasons") val seasons: List<Int>? = null,
    @EncodeDefault @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("serverId") val serverId: Int? = null,
    @SerialName("profileId") val profileId: Int? = null,
    @SerialName("rootFolder") val rootFolder: String? = null,
)

/** `PUT request/{id}`: only the fields sent change; the seasons list is a show's whole new set. */
@Serializable
data class SeerrEditRequestBody(
    @SerialName("mediaType") val mediaType: String,
    @SerialName("seasons") val seasons: List<Int>? = null,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("serverId") val serverId: Int? = null,
    @SerialName("profileId") val profileId: Int? = null,
    @SerialName("rootFolder") val rootFolder: String? = null,
    @SerialName("tags") val tags: List<Int>? = null,
)

/** A Radarr or Sonarr instance as Seerr lists it; the `active*` fields are what a plain request gets. */
@Serializable
data class SeerrServerDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("activeProfileId") val activeProfileId: Int? = null,
    @SerialName("activeDirectory") val activeDirectory: String? = null,
)

@Serializable
data class SeerrServerDetailsDto(
    /** The instance itself, as far as its name; every field optional so an older shape still parses. */
    @SerialName("server") val server: SeerrServerRefDto? = null,
    @SerialName("profiles") val profiles: List<SeerrProfileDto> = emptyList(),
    @SerialName("rootFolders") val rootFolders: List<SeerrRootFolderDto> = emptyList(),
    @SerialName("tags") val tags: List<SeerrTagDto> = emptyList(),
)

@Serializable
data class SeerrServerRefDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String? = null,
)

@Serializable
data class SeerrProfileDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
)

@Serializable
data class SeerrRootFolderDto(
    @SerialName("id") val id: Int,
    @SerialName("path") val path: String,
)

@Serializable
data class SeerrRequestResultDto(
    @SerialName("id") val id: Int? = null,
)
