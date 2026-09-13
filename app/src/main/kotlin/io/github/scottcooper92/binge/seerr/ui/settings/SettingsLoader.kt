package io.github.scottcooper92.binge.seerr.ui.settings

import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.notifications.NotificationSignal
import io.github.scottcooper92.binge.seerr.seerr.SeerrApi
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultAccess
import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultQuotaDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrJobDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrMainSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrServiceSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject

/** A default quota with no window is a daily one, which is the server's own fallback. */
private const val DEFAULT_QUOTA_DAYS = 1

/**
 * The Settings screen's reads. The connection and server summaries come from what the connection
 * already holds; the admin config is fetched only when the user may read it, each endpoint
 * best-effort, so a restricted user never fires a call the server would refuse and one refused
 * endpoint drops only its rows.
 */
class SettingsLoader
    @Inject
    constructor(
        private val connection: SeerrConnection,
    ) {
        suspend fun connection(): ConnectionSummary {
            val saved = connection.current()
            val user = runCatching { connection.authenticatedUser() }.getOrNull()
            return ConnectionSummary(
                baseUrl = saved.baseUrl,
                signInKind = if (saved.auth is SeerrAuth.ApiKey) SignInKind.ApiKey else SignInKind.Session,
                userName = user?.let { listOfNotNull(it.displayName, it.username, it.email).firstOrNull { name -> name.isNotBlank() } },
            )
        }

        suspend fun server(): ServerSummary {
            val profile = connection.refreshProfile()
            return ServerSummary(
                title = profile.settings.applicationTitle?.takeIf { it.isNotBlank() } ?: profile.variant.displayName,
                variant = profile.variant,
                versionLabel = profile.version?.label,
                updateAvailable = profile.updateAvailable || profile.commitsBehind > 0,
                commitsBehind = profile.commitsBehind,
                mediaServer = profile.mediaServer,
            )
        }

        /** The signals this viewer may turn on: the feeds follow the moderator permissions, the user's own are everyone's. */
        suspend fun notificationSignals(): List<NotificationSignal> {
            val permissions = runCatching { connection.authenticatedUser() }.getOrNull().toPermissions()
            val hasIssues = runCatching { connection.profile().hasIssues }.getOrDefault(false)
            return NotificationSignal.entries.filter { signal ->
                when (signal) {
                    NotificationSignal.PendingRequests -> permissions.canManageRequests
                    NotificationSignal.OpenIssues -> permissions.canManageIssues && hasIssues
                    else -> true
                }
            }
        }

        /** Null for a user who may not manage settings; otherwise every section that answered. */
        suspend fun config(): ServerConfig? {
            val permissions = runCatching { connection.authenticatedUser() }.getOrNull().toPermissions()
            if (!permissions.canManageSettings) return null
            val api = runCatching { connection.api() }.getOrNull() ?: return null
            return coroutineScope {
                val main = async { runCatching { api.mainSettings() }.getOrNull() }
                val about = async { runCatching { api.about() }.getOrNull() }
                val jobs = async { runCatching { api.jobs() }.getOrNull() }
                val email = async { runCatching { api.emailAgent().enabled }.getOrNull() }
                val discord = async { runCatching { api.discordAgent().enabled }.getOrNull() }
                val radarr = async { runCatching { api.radarrServices() }.getOrNull() }
                val sonarr = async { runCatching { api.sonarrServices() }.getOrNull() }
                val mainDto = main.await()
                ServerConfig(
                    general = mainDto?.toGeneral(),
                    requestPolicy = mainDto?.toRequestPolicy(),
                    agents = agents(email.await(), discord.await()),
                    system = system(about.await(), jobs.await()),
                    services = services(radarr.await(), sonarr.await()),
                )
            }
        }

        private suspend fun SeerrApi.radarrServices(): List<ServerService> = radarrSettings().map { it.toService(ServiceType.Radarr) }

        private suspend fun SeerrApi.sonarrServices(): List<ServerService> = sonarrSettings().map { it.toService(ServiceType.Sonarr) }
    }

/** Null when both fetches failed; otherwise the services from whichever succeeded. */
private fun services(
    radarr: List<ServerService>?,
    sonarr: List<ServerService>?,
): List<ServerService>? = if (radarr == null && sonarr == null) null else radarr.orEmpty() + sonarr.orEmpty()

internal fun SeerrMainSettingsDto.toGeneral(): GeneralSettings =
    GeneralSettings(
        applicationTitle = applicationTitle?.takeIf { it.isNotBlank() },
        applicationUrl = applicationUrl?.takeIf { it.isNotBlank() }?.trimEnd('/'),
        displayLanguage = appLanguage?.takeIf { it.isNotBlank() },
        hideAvailable = hideAvailable,
    )

internal fun SeerrMainSettingsDto.toRequestPolicy(): RequestPolicy =
    RequestPolicy(
        defaultAccess = SeerrDefaultAccess.fromBits(defaultPermissions),
        movieLimit = defaultQuotas?.movie.toLimit(),
        tvLimit = defaultQuotas?.tv.toLimit(),
    )

/** A missing or zero limit is unlimited: no row. */
private fun SeerrDefaultQuotaDto?.toLimit(): RequestLimit? {
    val count = this?.quotaLimit?.takeIf { it > 0 } ?: return null
    return RequestLimit(count = count, days = quotaDays?.takeIf { it > 0 } ?: DEFAULT_QUOTA_DAYS)
}

private fun agents(
    email: Boolean?,
    discord: Boolean?,
): NotificationAgents? = if (email == null && discord == null) null else NotificationAgents(email, discord)

private fun system(
    about: io.github.scottcooper92.binge.seerr.seerr.SeerrAboutDto?,
    jobs: List<SeerrJobDto>?,
): SystemInfo? {
    if (about == null && jobs == null) return null
    return SystemInfo(
        version = about?.version?.takeIf { it.isNotBlank() },
        totalRequests = about?.totalRequests,
        totalMediaItems = about?.totalMediaItems,
        jobs = jobs.orEmpty().map { it.toJob() },
    )
}

private fun SeerrJobDto.toJob(): ScheduledJob =
    ScheduledJob(
        id = id,
        name = name?.takeIf { it.isNotBlank() } ?: id,
        running = running,
        nextRunMillis = nextExecutionTime?.toEpochMillisOrNull(),
    )

private fun String.toEpochMillisOrNull(): Long? =
    runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrNull()

internal fun SeerrServiceSettingsDto.toService(type: ServiceType): ServerService =
    ServerService(
        name = name?.takeIf { it.isNotBlank() } ?: type.name,
        type = type,
        url = browsableUrl(),
        qualityProfile = activeProfileName?.takeIf { it.isNotBlank() },
        rootFolder = activeDirectory?.takeIf { it.isNotBlank() },
        is4k = is4k,
        isDefault = isDefault,
    )

/** The admin's external URL where set, else the instance's own address; a LAN one opens fine from the same network. */
private fun SeerrServiceSettingsDto.browsableUrl(): String? {
    externalUrl?.takeIf { it.isNotBlank() }?.let { return it.trimEnd('/') }
    val host = hostname?.takeIf { it.isNotBlank() } ?: return null
    val scheme = if (useSsl) "https" else "http"
    val portPart = port?.let { ":$it" }.orEmpty()
    val path =
        baseUrl
            ?.trim('/')
            ?.takeIf { it.isNotEmpty() }
            ?.let { "/$it" }
            .orEmpty()
    return "$scheme://$host$portPart$path"
}
