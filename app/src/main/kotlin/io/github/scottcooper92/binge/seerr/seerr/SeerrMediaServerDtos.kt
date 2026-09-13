package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One library on the media server as Seerr lists it; [lastScan] is epoch millis. */
@Serializable
data class SeerrLibraryDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("type") val type: String? = null,
    @SerialName("lastScan") val lastScan: Long? = null,
)

/** `GET settings/plex`, and what its `POST` answers with once the server has been reached. */
@Serializable
data class SeerrPlexSettingsDto(
    @SerialName("name") val name: String? = null,
    @SerialName("machineId") val machineId: String? = null,
    @SerialName("ip") val ip: String? = null,
    @SerialName("port") val port: Int? = null,
    @SerialName("useSsl") val useSsl: Boolean? = null,
    @SerialName("libraries") val libraries: List<SeerrLibraryDto> = emptyList(),
    @SerialName("webAppUrl") val webAppUrl: String? = null,
)

/** `POST settings/plex`: the connection only; the server fills the name and machine id from the reply it gets. */
@Serializable
data class SeerrPlexSettingsBody(
    @SerialName("ip") val ip: String,
    @SerialName("port") val port: Int,
    @SerialName("useSsl") val useSsl: Boolean,
    @SerialName("webAppUrl") val webAppUrl: String? = null,
)

/** `GET settings/jellyfin` — the Jellyseerr lineage's, and Emby's too, since the server keeps one record for either. */
@Serializable
data class SeerrJellyfinSettingsDto(
    @SerialName("name") val name: String? = null,
    @SerialName("ip") val ip: String? = null,
    @SerialName("port") val port: Int? = null,
    @SerialName("useSsl") val useSsl: Boolean? = null,
    @SerialName("urlBase") val urlBase: String? = null,
    @SerialName("externalHostname") val externalHostname: String? = null,
    @SerialName("jellyfinForgotPasswordUrl") val jellyfinForgotPasswordUrl: String? = null,
    @SerialName("libraries") val libraries: List<SeerrLibraryDto> = emptyList(),
    @SerialName("serverId") val serverId: String? = null,
    @SerialName("apiKey") val apiKey: String? = null,
)

/** `POST settings/jellyfin`: the connection; the server fills the name and server id from the reply it gets. */
@Serializable
data class SeerrJellyfinSettingsBody(
    @SerialName("ip") val ip: String,
    @SerialName("port") val port: Int,
    @SerialName("useSsl") val useSsl: Boolean,
    @SerialName("urlBase") val urlBase: String? = null,
    @SerialName("externalHostname") val externalHostname: String? = null,
    @SerialName("jellyfinForgotPasswordUrl") val jellyfinForgotPasswordUrl: String? = null,
    @SerialName("apiKey") val apiKey: String? = null,
)

/** `GET`/`POST settings/tautulli`; every field optional because an unconfigured Tautulli is `{}`. */
@Serializable
data class SeerrTautulliSettingsDto(
    @SerialName("hostname") val hostname: String? = null,
    @SerialName("port") val port: Int? = null,
    @SerialName("useSsl") val useSsl: Boolean? = null,
    @SerialName("urlBase") val urlBase: String? = null,
    @SerialName("apiKey") val apiKey: String? = null,
    @SerialName("externalUrl") val externalUrl: String? = null,
)

/**
 * `GET settings/plex/devices/servers`: one of the admin's own Plex servers, with every way to reach
 * it. The server tests each connection before answering: [SeerrPlexConnectionDto.status] is 200 for
 * one that answered, and [SeerrPlexConnectionDto.message] says why one did not.
 */
@Serializable
data class SeerrPlexDeviceDto(
    @SerialName("name") val name: String? = null,
    @SerialName("clientIdentifier") val clientIdentifier: String? = null,
    @SerialName("owned") val owned: Boolean = false,
    @SerialName("connection") val connection: List<SeerrPlexConnectionDto> = emptyList(),
)

@Serializable
data class SeerrPlexConnectionDto(
    @SerialName("protocol") val protocol: String? = null,
    @SerialName("address") val address: String? = null,
    @SerialName("port") val port: Int? = null,
    @SerialName("uri") val uri: String? = null,
    @SerialName("local") val local: Boolean = false,
    @SerialName("status") val status: Int? = null,
    @SerialName("message") val message: String? = null,
)

/** `GET settings/{plex,jellyfin}/sync`: the full scan's progress, and the library it is on. */
@Serializable
data class SeerrScanStatusDto(
    @SerialName("running") val running: Boolean = false,
    @SerialName("progress") val progress: Int = 0,
    @SerialName("total") val total: Int = 0,
    @SerialName("currentLibrary") val currentLibrary: SeerrLibraryDto? = null,
    @SerialName("libraries") val libraries: List<SeerrLibraryDto> = emptyList(),
)

/** `POST settings/{plex,jellyfin}/sync`: one of the two, never both. */
@Serializable
data class SeerrScanCommandBody(
    @SerialName("start") val start: Boolean? = null,
    @SerialName("cancel") val cancel: Boolean? = null,
)

/** `PUT settings/{plex,jellyfin}/library/{id}` on `develop`. */
@Serializable
data class SeerrLibraryEnabledBody(
    @SerialName("enabled") val enabled: Boolean,
)
