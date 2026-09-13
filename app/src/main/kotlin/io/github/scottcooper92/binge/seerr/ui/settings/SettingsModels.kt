package io.github.scottcooper92.binge.seerr.ui.settings

import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultAccess
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant

/** How the app is signed in, as the Connection group names it. */
enum class SignInKind { ApiKey, Session }

data class ConnectionSummary(
    val baseUrl: String,
    val signInKind: SignInKind,
    /** The signed-in user's name; null while `auth/me` has not answered. */
    val userName: String?,
)

data class ServerSummary(
    val title: String,
    val variant: SeerrVariant,
    val versionLabel: String?,
    val updateAvailable: Boolean,
    val commitsBehind: Int,
)

enum class ServiceType { Radarr, Sonarr }

/** One configured Radarr or Sonarr; [url] is the admin's external URL, else one built from host, port and base path. */
data class ServerService(
    val name: String,
    val type: ServiceType,
    val url: String?,
    val qualityProfile: String?,
    val rootFolder: String?,
    val is4k: Boolean,
    val isDefault: Boolean,
)

data class GeneralSettings(
    val applicationTitle: String?,
    val applicationUrl: String?,
    val displayLanguage: String?,
    val hideAvailable: Boolean?,
)

/** [count] requests per [days]-day window; the server's global default for one media type. */
data class RequestLimit(
    val count: Int,
    val days: Int,
)

data class RequestPolicy(
    val defaultAccess: SeerrDefaultAccess,
    val movieLimit: RequestLimit?,
    val tvLimit: RequestLimit?,
)

/** Null for an agent whose settings could not be read. */
data class NotificationAgents(
    val emailEnabled: Boolean?,
    val discordEnabled: Boolean?,
)

data class ScheduledJob(
    val id: String,
    val name: String,
    val running: Boolean,
    val nextRunMillis: Long?,
)

data class SystemInfo(
    val version: String?,
    val totalRequests: Int?,
    val totalMediaItems: Int?,
    val jobs: List<ScheduledJob>,
)

/** The admin-only reads; each section is null on its own failure so one refused endpoint drops only its rows. */
data class ServerConfig(
    val general: GeneralSettings? = null,
    val requestPolicy: RequestPolicy? = null,
    val agents: NotificationAgents? = null,
    val system: SystemInfo? = null,
    val services: List<ServerService>? = null,
)

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Ready(
        val connection: ConnectionSummary,
        val server: ServerSummary,
        /** Null for a user who may not read the server's settings, or until they load. */
        val config: ServerConfig?,
    ) : SettingsUiState
}
