package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RequestPage
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultAccess
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaServer
import io.github.scottcooper92.binge.seerr.seerr.isWebUrl
import io.github.scottcooper92.binge.seerr.seerr.releaseNotesUrl
import io.github.scottcooper92.binge.seerr.ui.openInBrowser
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerAgent
import java.util.Locale

/** The Connection group: the server (opens in the browser), who is signed in, the version, and the way to edit. */
@Composable
internal fun connectionRows(
    connection: ConnectionSummary,
    server: ServerSummary,
    onEditConnection: () -> Unit,
): List<SettingsRow> {
    val context = LocalContext.current
    return listOf(
        SettingsRow(
            icon = Icons.Filled.Link,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_server),
            detail = connection.baseUrl,
            clickable = connection.baseUrl.isWebUrl(),
            onClick = { context.openInBrowser(connection.baseUrl) },
        ),
        SettingsRow(
            icon = Icons.Filled.Person,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_signed_in_as),
            detail =
                when (connection.signInKind) {
                    SignInKind.ApiKey -> stringResource(R.string.settings_signed_in_api_key)
                    SignInKind.Session -> connection.userName ?: stringResource(R.string.settings_value_unknown)
                },
            clickable = false,
        ),
        SettingsRow(
            icon = Icons.Filled.Dns,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_version),
            detail = server.versionDetail(),
            clickable = server.updateAvailable,
            onClick = { context.openInBrowser(server.variant.releaseNotesUrl()) },
        ),
        SettingsRow(
            icon = Icons.Filled.Edit,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_edit_connection),
            detail = stringResource(R.string.settings_edit_connection_caption),
            onClick = onEditConnection,
        ),
    )
}

@Composable
private fun ServerSummary.versionDetail(): String {
    val edition =
        versionLabel?.let { stringResource(R.string.setup_server_edition, variant.displayName, it) }
            ?: stringResource(R.string.setup_server_development, variant.displayName)
    return when {
        commitsBehind > 0 -> stringResource(R.string.settings_version_behind, edition, commitsBehind)
        updateAvailable -> stringResource(R.string.settings_version_update, edition)
        else -> edition
    }
}

/** The media server the admin connected, and the way into its page: address, libraries and scans. */
@Composable
internal fun mediaServerRows(
    server: ServerSummary,
    onOpen: () -> Unit,
): List<SettingsRow> =
    listOf(
        SettingsRow(
            icon = Icons.Filled.Storage,
            iconTint = BingeSentiment.Info.fill(),
            label =
                stringResource(
                    when (server.mediaServer) {
                        SeerrMediaServer.Jellyfin -> R.string.user_origin_jellyfin
                        SeerrMediaServer.Emby -> R.string.user_origin_emby
                        SeerrMediaServer.Plex, SeerrMediaServer.NotConfigured -> R.string.user_origin_plex
                    },
                ),
            detail = stringResource(R.string.server_settings_media_server_caption),
            onClick = onOpen,
        ),
    )

/** The general settings as read, and first the way into editing them: the page owns every field, these rows only summarise. */
@Composable
internal fun generalRows(
    general: GeneralSettings,
    onEdit: () -> Unit,
    onOpenSliders: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenMetadata: () -> Unit,
): List<SettingsRow> {
    val context = LocalContext.current
    return listOfNotNull(
        SettingsRow(
            icon = Icons.Filled.Tune,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.server_settings_edit),
            detail = stringResource(R.string.server_settings_edit_caption),
            onClick = onEdit,
        ),
        general.applicationUrl?.takeIf { it.isWebUrl() }?.let { url ->
            SettingsRow(
                icon = Icons.Filled.Link,
                iconTint = BingeSentiment.Info.fill(),
                label = stringResource(R.string.settings_application_url),
                detail = url,
                onClick = { context.openInBrowser(url) },
            )
        },
        SettingsRow(
            icon = Icons.Filled.Language,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_display_language),
            detail = general.displayLanguage?.let { displayLanguageName(it) } ?: stringResource(R.string.settings_value_unknown),
            clickable = false,
        ),
        general.hideAvailable?.let { hidden ->
            SettingsRow(
                icon = Icons.Filled.VisibilityOff,
                iconTint = BingeSentiment.Info.fill(),
                label = stringResource(R.string.settings_hide_available),
                detail = stringResource(onOffRes(hidden)),
                clickable = false,
            )
        },
        if (general.discoverSliders) {
            SettingsRow(
                icon = Icons.Filled.ViewCarousel,
                iconTint = BingeSentiment.Info.fill(),
                label = stringResource(R.string.server_settings_sliders),
                detail = stringResource(R.string.server_settings_sliders_caption),
                onClick = onOpenSliders,
            )
        } else {
            null
        },
        if (general.network) {
            SettingsRow(
                icon = Icons.Filled.Dns,
                iconTint = BingeSentiment.Info.fill(),
                label = stringResource(R.string.server_settings_network),
                detail = stringResource(R.string.server_settings_network_caption),
                onClick = onOpenNetwork,
            )
        } else {
            null
        },
        if (general.metadata) {
            SettingsRow(
                icon = Icons.Filled.Storage,
                iconTint = BingeSentiment.Info.fill(),
                label = stringResource(R.string.server_settings_metadata),
                detail = stringResource(R.string.server_settings_metadata_caption),
                onClick = onOpenMetadata,
            )
        } else {
            null
        },
    )
}

/** Each instance opens its own editor; first, the way to the services page, where one is added and the rules live. */
@Composable
internal fun serviceRows(
    services: List<ServerService>,
    onOpenServices: () -> Unit,
    onOpenInstance: (ServiceType, Int) -> Unit,
): List<SettingsRow> {
    val context = LocalContext.current
    val manage =
        SettingsRow(
            icon = Icons.Filled.Tune,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.server_settings_services_manage),
            detail = stringResource(R.string.server_settings_services_manage_caption),
            onClick = onOpenServices,
        )
    return listOf(manage) +
        services.map { service ->
            val url = service.url?.takeIf { it.isWebUrl() }
            val id = service.id
            SettingsRow(
                icon = if (service.type == ServiceType.Radarr) Icons.Filled.Movie else Icons.Filled.Tv,
                iconTint = BingeSentiment.Info.fill(),
                label = service.label(),
                detail = service.detail(),
                clickable = id != null || url != null,
                onClick = { if (id != null) onOpenInstance(service.type, id) else url?.let { context.openInBrowser(it) } },
            )
        }
}

@Composable
private fun ServerService.label(): String {
    val markers =
        listOfNotNull(
            if (is4k) stringResource(R.string.settings_service_4k) else null,
            if (isDefault) stringResource(R.string.settings_service_default) else null,
        )
    return (listOf(name) + markers).joinToString(stringResource(R.string.hub_meta_separator))
}

@Composable
private fun ServerService.detail(): String =
    listOfNotNull(qualityProfile, rootFolder).takeIf { it.isNotEmpty() }?.joinToString(stringResource(R.string.hub_meta_separator))
        ?: url?.takeIf { it.isWebUrl() }
        ?: stringResource(R.string.settings_value_unknown)

@Composable
internal fun requestPolicyRows(policy: RequestPolicy): List<SettingsRow> =
    listOf(
        SettingsRow(
            icon = Icons.Filled.Shield,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_default_permissions),
            detail =
                stringResource(
                    when (policy.defaultAccess) {
                        SeerrDefaultAccess.NoRequests -> R.string.settings_default_access_none
                        SeerrDefaultAccess.RequestWithApproval -> R.string.settings_default_access_request
                        SeerrDefaultAccess.AutoApprove -> R.string.settings_default_access_auto
                    },
                ),
            clickable = false,
        ),
        SettingsRow(
            icon = Icons.Filled.RequestPage,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_request_limit),
            detail = requestLimitDetail(policy.movieLimit, policy.tvLimit),
            clickable = false,
        ),
    )

@Composable
private fun requestLimitDetail(
    movie: RequestLimit?,
    tv: RequestLimit?,
): String {
    if (movie == null && tv == null) return stringResource(R.string.settings_request_limit_unlimited)
    return listOf(
        stringResource(R.string.settings_request_limit_movie, movie.limitText()),
        stringResource(R.string.settings_request_limit_tv, tv.limitText()),
    ).joinToString(stringResource(R.string.hub_meta_separator))
}

@Composable
private fun RequestLimit?.limitText(): String =
    this?.let { stringResource(R.string.settings_request_limit_value, it.count, it.days) }
        ?: stringResource(R.string.settings_request_limit_unlimited)

@Composable
internal fun agentRows(
    agents: NotificationAgents,
    onOpenAgents: () -> Unit,
    onOpenAgent: (ServerAgent) -> Unit,
): List<SettingsRow> =
    listOfNotNull(
        SettingsRow(
            icon = Icons.Filled.Tune,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.server_settings_agents_manage),
            detail = stringResource(R.string.server_settings_agents_manage_caption),
            onClick = onOpenAgents,
        ),
        agents.emailEnabled?.let { on ->
            agentRow(Icons.Filled.Email, stringResource(R.string.settings_agent_email), on) { onOpenAgent(ServerAgent.Email) }
        },
        agents.discordEnabled?.let { on ->
            agentRow(Icons.Filled.Forum, stringResource(R.string.settings_agent_discord), on) { onOpenAgent(ServerAgent.Discord) }
        },
    )

@Composable
private fun agentRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    on: Boolean,
    onClick: () -> Unit,
): SettingsRow =
    SettingsRow(
        icon = icon,
        iconTint = BingeSentiment.Info.fill(),
        label = label,
        detail = stringResource(onOffRes(on)),
        detailColor = if (on) BingeSentiment.Positive.fill() else null,
        onClick = onClick,
    )

/** About first, then every scheduled job with its next run; a running job says so. */
@Composable
internal fun systemRows(system: SystemInfo): List<SettingsRow> =
    listOf(
        SettingsRow(
            icon = Icons.Filled.Public,
            iconTint = BingeSentiment.Neutral.fill(),
            label = stringResource(R.string.settings_about),
            detail =
                listOfNotNull(
                    system.version?.let { stringResource(R.string.settings_about_version, it) },
                    system.totalRequests?.let { stringResource(R.string.settings_about_requests, it) },
                    system.totalMediaItems?.let { stringResource(R.string.settings_about_media, it) },
                ).joinToString(stringResource(R.string.hub_meta_separator)).ifEmpty { stringResource(R.string.settings_value_unknown) },
            clickable = false,
        ),
    ) +
        system.jobs.map { job ->
            SettingsRow(
                icon = Icons.Filled.Cached,
                iconTint = BingeSentiment.Neutral.fill(),
                label = job.name,
                detail =
                    when {
                        job.running -> stringResource(R.string.settings_job_running)
                        else ->
                            formatRelativeOrAbsolute(job.nextRunMillis)?.let { stringResource(R.string.settings_job_next_run, it) }
                                ?: stringResource(R.string.settings_value_unknown)
                    },
                clickable = false,
            )
        }

internal fun onOffRes(on: Boolean): Int = if (on) R.string.settings_value_on else R.string.settings_value_off

/** The locale tag's own name in the device's language; the raw tag when the JVM cannot resolve it. */
private fun displayLanguageName(tag: String): String =
    Locale.forLanguageTag(tag).getDisplayName(Locale.getDefault()).takeIf { it.isNotBlank() } ?: tag
