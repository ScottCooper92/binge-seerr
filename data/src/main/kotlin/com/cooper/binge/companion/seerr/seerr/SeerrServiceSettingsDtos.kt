package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A Radarr or Sonarr instance as the admin configured it (`GET settings/radarr`, `settings/sonarr`),
 * and the body its `POST` and `PUT` take — the server stores what it is sent, so the record is the
 * body. Radarr's own field is [minimumAvailability]; Sonarr's are the series types, the anime
 * destination, the season folders and the language profile (Sonarr 3 only). Every field defaults so
 * a Sonarr record parses as a Radarr one where the reader does not care.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeerrServiceSettingsDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("hostname") val hostname: String? = null,
    @SerialName("port") val port: Int? = null,
    @SerialName("apiKey") val apiKey: String? = null,
    @EncodeDefault @SerialName("useSsl") val useSsl: Boolean = false,
    @SerialName("baseUrl") val baseUrl: String? = null,
    @SerialName("activeProfileId") val activeProfileId: Int? = null,
    @SerialName("activeProfileName") val activeProfileName: String? = null,
    @SerialName("activeDirectory") val activeDirectory: String? = null,
    @EncodeDefault @SerialName("tags") val tags: List<Int> = emptyList(),
    @EncodeDefault @SerialName("is4k") val is4k: Boolean = false,
    @EncodeDefault @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("externalUrl") val externalUrl: String? = null,
    @EncodeDefault @SerialName("syncEnabled") val syncEnabled: Boolean = false,
    @EncodeDefault @SerialName("preventSearch") val preventSearch: Boolean = false,
    @EncodeDefault @SerialName("tagRequests") val tagRequests: Boolean = false,
    @SerialName("minimumAvailability") val minimumAvailability: String? = null,
    @SerialName("seriesType") val seriesType: String? = null,
    @SerialName("animeSeriesType") val animeSeriesType: String? = null,
    @SerialName("activeAnimeProfileId") val activeAnimeProfileId: Int? = null,
    @SerialName("activeAnimeProfileName") val activeAnimeProfileName: String? = null,
    @SerialName("activeAnimeDirectory") val activeAnimeDirectory: String? = null,
    @SerialName("animeTags") val animeTags: List<Int>? = null,
    @SerialName("enableSeasonFolders") val enableSeasonFolders: Boolean? = null,
    @SerialName("activeLanguageProfileId") val activeLanguageProfileId: Int? = null,
    @SerialName("activeAnimeLanguageProfileId") val activeAnimeLanguageProfileId: Int? = null,
    @SerialName("monitorNewItems") val monitorNewItems: String? = null,
)

/** `POST settings/{radarr,sonarr}/test`: the address and key to reach the instance with. */
@Serializable
data class SeerrDvrTestBody(
    @SerialName("hostname") val hostname: String,
    @SerialName("port") val port: Int,
    @SerialName("useSsl") val useSsl: Boolean,
    @SerialName("baseUrl") val baseUrl: String? = null,
    @SerialName("apiKey") val apiKey: String,
)

/** What a reached instance offers: its profiles, folders and tags; Sonarr 3 adds language profiles, Sonarr 4 sends null. */
@Serializable
data class SeerrDvrTestResultDto(
    @SerialName("profiles") val profiles: List<SeerrProfileDto> = emptyList(),
    @SerialName("rootFolders") val rootFolders: List<SeerrRootFolderDto> = emptyList(),
    @SerialName("tags") val tags: List<SeerrTagDto> = emptyList(),
    @SerialName("languageProfiles") val languageProfiles: List<SeerrProfileDto>? = null,
    @SerialName("urlBase") val urlBase: String? = null,
)

/**
 * An override rule (`overrideRule`, Jellyseerr 2.2+): the conditions and the overrides for one
 * instance. The list-valued conditions are strings as the server stores them — ids joined with a
 * comma, except [language], whose ISO codes are joined with a pipe; [tags] are tag ids, comma-joined.
 */
@Serializable
data class SeerrOverrideRuleDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("radarrServiceId") val radarrServiceId: Int? = null,
    @SerialName("sonarrServiceId") val sonarrServiceId: Int? = null,
    @SerialName("users") val users: String? = null,
    @SerialName("genre") val genre: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("keywords") val keywords: String? = null,
    @SerialName("profileId") val profileId: Int? = null,
    @SerialName("rootFolder") val rootFolder: String? = null,
    @SerialName("tags") val tags: String? = null,
)
