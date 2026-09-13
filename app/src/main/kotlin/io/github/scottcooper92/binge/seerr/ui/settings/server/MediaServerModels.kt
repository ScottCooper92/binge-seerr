package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrJellyfinSettingsBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrJellyfinSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrLibraryDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaServer
import io.github.scottcooper92.binge.seerr.seerr.SeerrPlexDeviceDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrPlexSettingsBody
import io.github.scottcooper92.binge.seerr.seerr.SeerrPlexSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrScanStatusDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrTautulliSettingsDto

private const val PORT_MAX = 65_535
private const val CONNECTION_OK = 200

/** Which media server the record describes; the server keeps one record for Jellyfin and Emby both. */
enum class MediaServerKind { Plex, Jellyfin, Emby }

/** The path segment Seerr keys the libraries and the scan on: `plex`, or `jellyfin` for either of the others. */
val MediaServerKind.apiSegment: String get() = if (this == MediaServerKind.Plex) "plex" else "jellyfin"

/**
 * The media-server form. A null field is one the [kind] does not have: Plex has a web app URL and
 * nothing else beside the address; Jellyfin and Emby add the URL base, the external host, the
 * forgot-password link and the API key.
 */
data class MediaServerForm(
    val kind: MediaServerKind,
    val serverName: String = "",
    val host: String = "",
    val port: String = "",
    val useSsl: Boolean = false,
    val externalUrl: String = "",
    val urlBase: String? = null,
    val forgotPasswordUrl: String? = null,
    val apiKey: String? = null,
) {
    val valid: Boolean get() = host.isNotBlank() && port.trim().toIntOrNull()?.let { it in 1..PORT_MAX } == true
}

enum class LibraryType { Movies, Shows }

data class MediaLibrary(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val type: LibraryType?,
    val lastScanMillis: Long?,
)

/** The full scan as the server reports it; [currentLibrary] is what it is on while running. */
data class LibraryScan(
    val running: Boolean,
    val progress: Int,
    val total: Int,
    val currentLibrary: String?,
)

/** One way to reach one of the admin's Plex servers; [reachable] is the server's own test, null where it did not say. */
data class PlexConnection(
    val address: String,
    val port: Int,
    val useSsl: Boolean,
    val local: Boolean,
    val reachable: Boolean?,
)

data class PlexServerChoice(
    val name: String,
    val connections: List<PlexConnection>,
)

sealed interface PlexServerPicker {
    data object Loading : PlexServerPicker

    data class Failed(
        val error: SeerrError,
    ) : PlexServerPicker

    data class Ready(
        val servers: List<PlexServerChoice>,
    ) : PlexServerPicker
}

/** What the media-server page shows beside its form: the libraries, the scan, and the Plex picker while open. */
data class MediaServerExtras(
    val libraries: List<MediaLibrary> = emptyList(),
    val scan: LibraryScan? = null,
    val syncingLibraries: Boolean = false,
    val busyLibraryIds: Set<String> = emptySet(),
    val picker: PlexServerPicker? = null,
)

/** The Tautulli form; the port and the SSL switch stay blank and off until an address is given. */
data class TautulliForm(
    val host: String = "",
    val port: String = "",
    val useSsl: Boolean = false,
    val urlBase: String = "",
    val apiKey: String = "",
    val externalUrl: String = "",
) {
    val valid: Boolean get() = host.isNotBlank() && port.trim().toIntOrNull()?.let { it in 1..PORT_MAX } == true && apiKey.isNotBlank()
}

/** Overseerr has no media-server type and is always Plex; a lineage server not yet set up is shown the Plex form too. */
internal fun SeerrMediaServer.toKind(): MediaServerKind =
    when (this) {
        SeerrMediaServer.Jellyfin -> MediaServerKind.Jellyfin
        SeerrMediaServer.Emby -> MediaServerKind.Emby
        SeerrMediaServer.Plex, SeerrMediaServer.NotConfigured -> MediaServerKind.Plex
    }

internal fun SeerrPlexSettingsDto.toForm(): MediaServerForm =
    MediaServerForm(
        kind = MediaServerKind.Plex,
        serverName = name.orEmpty(),
        host = ip.orEmpty(),
        port = port?.toString().orEmpty(),
        useSsl = useSsl ?: false,
        externalUrl = webAppUrl.orEmpty(),
    )

internal fun SeerrJellyfinSettingsDto.toForm(kind: MediaServerKind): MediaServerForm =
    MediaServerForm(
        kind = kind,
        serverName = name.orEmpty(),
        host = ip.orEmpty(),
        port = port?.toString().orEmpty(),
        useSsl = useSsl ?: false,
        externalUrl = externalHostname.orEmpty(),
        urlBase = urlBase.orEmpty(),
        forgotPasswordUrl = jellyfinForgotPasswordUrl.orEmpty(),
        apiKey = apiKey.orEmpty(),
    )

internal fun MediaServerForm.toPlexBody(): SeerrPlexSettingsBody =
    SeerrPlexSettingsBody(
        ip = host.trim(),
        port = port.trim().toInt(),
        useSsl = useSsl,
        webAppUrl = externalUrl.trim().takeIf { it.isNotEmpty() },
    )

internal fun MediaServerForm.toJellyfinBody(): SeerrJellyfinSettingsBody =
    SeerrJellyfinSettingsBody(
        ip = host.trim(),
        port = port.trim().toInt(),
        useSsl = useSsl,
        urlBase = urlBase?.trim(),
        externalHostname = externalUrl.trim(),
        jellyfinForgotPasswordUrl = forgotPasswordUrl?.trim(),
        apiKey = apiKey?.trim(),
    )

internal fun SeerrLibraryDto.toLibrary(): MediaLibrary =
    MediaLibrary(
        id = id,
        name = name?.takeIf { it.isNotBlank() } ?: id,
        enabled = enabled,
        type =
            when (type) {
                "movie" -> LibraryType.Movies
                "show" -> LibraryType.Shows
                else -> null
            },
        lastScanMillis = lastScan?.takeIf { it > 0 },
    )

internal fun SeerrScanStatusDto.toScan(): LibraryScan =
    LibraryScan(running = running, progress = progress, total = total, currentLibrary = currentLibrary?.name?.takeIf { it.isNotBlank() })

/** Only the admin's own servers can be Seerr's; a friend's shared one is listed by plex.tv but is not offered. */
internal fun List<SeerrPlexDeviceDto>.toChoices(): List<PlexServerChoice> =
    filter { it.owned }.map { device ->
        PlexServerChoice(
            name = device.name?.takeIf { it.isNotBlank() } ?: device.clientIdentifier.orEmpty(),
            connections =
                device.connection.mapNotNull { connection ->
                    val address = connection.address?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    PlexConnection(
                        address = address,
                        port = connection.port ?: return@mapNotNull null,
                        useSsl = connection.protocol.equals("https", ignoreCase = true),
                        local = connection.local,
                        reachable = connection.status?.let { it == CONNECTION_OK },
                    )
                },
        )
    }

internal fun SeerrTautulliSettingsDto.toForm(): TautulliForm =
    TautulliForm(
        host = hostname.orEmpty(),
        port = port?.toString().orEmpty(),
        useSsl = useSsl ?: false,
        urlBase = urlBase.orEmpty(),
        apiKey = apiKey.orEmpty(),
        externalUrl = externalUrl.orEmpty(),
    )

internal fun TautulliForm.toDto(): SeerrTautulliSettingsDto =
    SeerrTautulliSettingsDto(
        hostname = host.trim(),
        port = port.trim().toInt(),
        useSsl = useSsl,
        urlBase = urlBase.trim(),
        apiKey = apiKey.trim(),
        externalUrl = externalUrl.trim(),
    )
