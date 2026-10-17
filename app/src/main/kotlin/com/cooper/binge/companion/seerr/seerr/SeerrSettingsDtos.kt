package com.cooper.binge.companion.seerr.seerr

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
    @SerialName("localLogin") val localLogin: Boolean? = null,
    /** The Jellyseerr lineage only; Overseerr has no switch for its media server sign-in. */
    @SerialName("mediaServerLogin") val mediaServerLogin: Boolean? = null,
    @SerialName("newPlexLogin") val newPlexLogin: Boolean? = null,
    @SerialName("hideBlocklisted") val hideBlocklisted: Boolean? = null,
    @SerialName("blocklistRegion") val blocklistRegion: String? = null,
    @SerialName("blocklistLanguage") val blocklistLanguage: String? = null,
    /** TMDB keyword ids, comma-separated, whose titles the "Process Blocklisted Tags" job blocklists. */
    @SerialName("blocklistedTags") val blocklistedTags: String? = null,
    @SerialName("blocklistedTagsLimit") val blocklistedTagsLimit: Int? = null,
    /** Jellyseerr 2.6 to 2.x's names for the same three settings, before Seerr 3.0 renamed them. */
    @SerialName("hideBlacklisted") val hideBlacklisted: Boolean? = null,
    @SerialName("blacklistedTags") val blacklistedTags: String? = null,
    @SerialName("blacklistedTagsLimit") val blacklistedTagsLimit: Int? = null,
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
    @SerialName("defaultQuotas") val defaultQuotas: SeerrDefaultQuotasDto? = null,
    @SerialName("localLogin") val localLogin: Boolean? = null,
    @SerialName("mediaServerLogin") val mediaServerLogin: Boolean? = null,
    @SerialName("newPlexLogin") val newPlexLogin: Boolean? = null,
    @SerialName("hideBlocklisted") val hideBlocklisted: Boolean? = null,
    @SerialName("blocklistRegion") val blocklistRegion: String? = null,
    @SerialName("blocklistLanguage") val blocklistLanguage: String? = null,
    /** TMDB keyword ids, comma-separated, whose titles the "Process Blocklisted Tags" job blocklists. */
    @SerialName("blocklistedTags") val blocklistedTags: String? = null,
    @SerialName("blocklistedTagsLimit") val blocklistedTagsLimit: Int? = null,
    /** Jellyseerr 2.6 to 2.x's names for the same three settings, before Seerr 3.0 renamed them. */
    @SerialName("hideBlacklisted") val hideBlacklisted: Boolean? = null,
    @SerialName("blacklistedTags") val blacklistedTags: String? = null,
    @SerialName("blacklistedTagsLimit") val blacklistedTagsLimit: Int? = null,
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
    @SerialName("tz") val tz: String? = null,
    @SerialName("appDataPath") val appDataPath: String? = null,
)

/** `GET status/appdata`: whether the config directory is a mounted volume, and on the Jellyseerr lineage whether it is writable. */
@Serializable
data class SeerrAppDataDto(
    @SerialName("appData") val appData: Boolean = true,
    @SerialName("appDataPath") val appDataPath: String? = null,
    @SerialName("appDataPermissions") val appDataPermissions: Boolean? = null,
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

/**
 * A notification agent's settings (`GET`/`POST settings/notifications/{agent}`, and the body of its
 * `test`): on or off, the events it is sent as a bitmask, and the agent's own options. The options
 * are kept as the object the server sent, since each agent has its own set and a server may carry
 * one this app does not show; an edit overlays the known keys and sends the rest back unchanged.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeerrNotificationAgentDto(
    @EncodeDefault @SerialName("enabled") val enabled: Boolean = false,
    @EncodeDefault @SerialName("types") val types: Int = 0,
    @EncodeDefault @SerialName("options") val options: JsonObject = JsonObject(emptyMap()),
)

/** One Pushover sound (`GET settings/notifications/pushover/sounds`). */
@Serializable
data class SeerrPushoverSoundDto(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
)

/** `POST settings/jobs/{id}/schedule`: a six-field cron expression, seconds first. */
@Serializable
data class SeerrJobScheduleBody(
    @SerialName("schedule") val schedule: String,
)
