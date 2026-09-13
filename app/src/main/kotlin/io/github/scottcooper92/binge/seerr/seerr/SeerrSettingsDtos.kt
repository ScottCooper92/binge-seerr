package io.github.scottcooper92.binge.seerr.seerr

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

/**
 * The network settings (`settings/network`, Jellyseerr 2.4+). Jellyseerr carries the two switches;
 * Seerr 3 adds the IPv4 preference, the outbound proxy and the DNS cache. A block the server did not
 * send stays null and is left out of the form and the body alike.
 */
@Serializable
data class SeerrNetworkSettingsDto(
    @SerialName("csrfProtection") val csrfProtection: Boolean? = null,
    @SerialName("trustProxy") val trustProxy: Boolean? = null,
    @SerialName("forceIpv4First") val forceIpv4First: Boolean? = null,
    @SerialName("proxy") val proxy: SeerrProxySettingsDto? = null,
    @SerialName("dnsCache") val dnsCache: SeerrDnsCacheSettingsDto? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeerrProxySettingsDto(
    @EncodeDefault @SerialName("enabled") val enabled: Boolean = false,
    @EncodeDefault @SerialName("hostname") val hostname: String = "",
    @EncodeDefault @SerialName("port") val port: Int = 8080,
    @EncodeDefault @SerialName("useSsl") val useSsl: Boolean = false,
    @EncodeDefault @SerialName("user") val user: String = "",
    @EncodeDefault @SerialName("password") val password: String = "",
    @EncodeDefault @SerialName("bypassFilter") val bypassFilter: String = "",
    @EncodeDefault @SerialName("bypassLocalAddresses") val bypassLocalAddresses: Boolean = true,
)

/** The DNS cache: on or off, and the TTL bounds it forces (`-1` for none). */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeerrDnsCacheSettingsDto(
    @EncodeDefault @SerialName("enabled") val enabled: Boolean = false,
    @EncodeDefault @SerialName("forceMinTtl") val forceMinTtl: Int = 0,
    @EncodeDefault @SerialName("forceMaxTtl") val forceMaxTtl: Int = -1,
)

/** The metadata providers (`settings/metadatas`, Seerr 3.0+): which of TMDB and TVDB serves series and anime. */
@Serializable
data class SeerrMetadataSettingsDto(
    @SerialName("settings") val settings: SeerrMetadataProvidersDto = SeerrMetadataProvidersDto(),
)

@Serializable
data class SeerrMetadataProvidersDto(
    @SerialName("tv") val tv: String? = null,
    @SerialName("anime") val anime: String? = null,
)

/** `POST settings/metadatas/test`: which providers to reach. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SeerrMetadataTestBody(
    @EncodeDefault @SerialName("tmdb") val tmdb: Boolean = false,
    @EncodeDefault @SerialName("tvdb") val tvdb: Boolean = false,
)

@Serializable
data class SeerrMessageDto(
    @SerialName("message") val message: String? = null,
)
