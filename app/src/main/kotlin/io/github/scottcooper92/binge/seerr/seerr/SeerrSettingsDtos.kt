package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET settings/main`, admin-only, and what `POST settings/main` and `settings/main/regenerate`
 * answer with. Every field defaults because the two lineages disagree on which exist: Overseerr
 * has one `region` and the proxy switches, the Jellyseerr lineage split the region in two and
 * added the streaming and special-episode ones. [apiKey] is here only for the general settings
 * page, which shows it behind a reveal; nothing else reads it.
 */
@Serializable
data class SeerrMainSettingsDto(
    @SerialName("apiKey") val apiKey: String? = null,
    @SerialName("applicationTitle") val applicationTitle: String? = null,
    @SerialName("applicationUrl") val applicationUrl: String? = null,
    @SerialName("appLanguage") val appLanguage: String? = null,
    @SerialName("locale") val locale: String? = null,
    @SerialName("region") val region: String? = null,
    @SerialName("discoverRegion") val discoverRegion: String? = null,
    @SerialName("streamingRegion") val streamingRegion: String? = null,
    @SerialName("originalLanguage") val originalLanguage: String? = null,
    @SerialName("hideAvailable") val hideAvailable: Boolean? = null,
    @SerialName("hideRequested") val hideRequested: Boolean? = null,
    @SerialName("partialRequestsEnabled") val partialRequestsEnabled: Boolean? = null,
    @SerialName("enableSpecialEpisodes") val enableSpecialEpisodes: Boolean? = null,
    @SerialName("versionCheck") val versionCheck: Boolean? = null,
    @SerialName("cacheImages") val cacheImages: Boolean? = null,
    @SerialName("youtubeUrl") val youtubeUrl: String? = null,
    @SerialName("trustProxy") val trustProxy: Boolean? = null,
    @SerialName("csrfProtection") val csrfProtection: Boolean? = null,
    @SerialName("defaultPermissions") val defaultPermissions: Int? = null,
    @SerialName("defaultQuotas") val defaultQuotas: SeerrDefaultQuotasDto? = null,
)

/**
 * `POST settings/main`: the server merges what is sent over what it holds, so a body carries only
 * the fields a page edits and a null is omitted rather than written. The API key is never sent;
 * only `settings/main/regenerate` changes it.
 */
@Serializable
data class SeerrMainSettingsUpdateBody(
    @SerialName("applicationTitle") val applicationTitle: String? = null,
    @SerialName("applicationUrl") val applicationUrl: String? = null,
    @SerialName("locale") val locale: String? = null,
    @SerialName("region") val region: String? = null,
    @SerialName("discoverRegion") val discoverRegion: String? = null,
    @SerialName("streamingRegion") val streamingRegion: String? = null,
    @SerialName("originalLanguage") val originalLanguage: String? = null,
    @SerialName("hideAvailable") val hideAvailable: Boolean? = null,
    @SerialName("hideRequested") val hideRequested: Boolean? = null,
    @SerialName("partialRequestsEnabled") val partialRequestsEnabled: Boolean? = null,
    @SerialName("enableSpecialEpisodes") val enableSpecialEpisodes: Boolean? = null,
    @SerialName("versionCheck") val versionCheck: Boolean? = null,
    @SerialName("cacheImages") val cacheImages: Boolean? = null,
    @SerialName("youtubeUrl") val youtubeUrl: String? = null,
    @SerialName("trustProxy") val trustProxy: Boolean? = null,
    @SerialName("csrfProtection") val csrfProtection: Boolean? = null,
    @SerialName("defaultPermissions") val defaultPermissions: Int? = null,
)

@Serializable
data class SeerrDefaultQuotasDto(
    @SerialName("movie") val movie: SeerrDefaultQuotaDto? = null,
    @SerialName("tv") val tv: SeerrDefaultQuotaDto? = null,
)

@Serializable
data class SeerrDefaultQuotaDto(
    @SerialName("quotaLimit") val quotaLimit: Int? = null,
    @SerialName("quotaDays") val quotaDays: Int? = null,
)

/** `GET settings/about`, admin-only: the build and the lifetime totals. */
@Serializable
data class SeerrAboutDto(
    @SerialName("version") val version: String? = null,
    @SerialName("totalRequests") val totalRequests: Int? = null,
    @SerialName("totalMediaItems") val totalMediaItems: Int? = null,
)

/** One scheduled job (`GET settings/jobs`); [nextExecutionTime] is an ISO timestamp. */
@Serializable
data class SeerrJobDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("interval") val interval: String? = null,
    @SerialName("nextExecutionTime") val nextExecutionTime: String? = null,
    @SerialName("running") val running: Boolean = false,
)

/** A notification agent's settings (`GET settings/notifications/{agent}`); only whether it is on is read. */
@Serializable
data class SeerrNotificationAgentDto(
    @SerialName("enabled") val enabled: Boolean = false,
)

/** A Radarr or Sonarr instance as the admin configured it (`GET settings/radarr`, `settings/sonarr`). */
@Serializable
data class SeerrServiceSettingsDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("hostname") val hostname: String? = null,
    @SerialName("port") val port: Int? = null,
    @SerialName("useSsl") val useSsl: Boolean = false,
    @SerialName("baseUrl") val baseUrl: String? = null,
    @SerialName("activeProfileName") val activeProfileName: String? = null,
    @SerialName("activeDirectory") val activeDirectory: String? = null,
    @SerialName("is4k") val is4k: Boolean = false,
    @SerialName("isDefault") val isDefault: Boolean = false,
    @SerialName("externalUrl") val externalUrl: String? = null,
)
