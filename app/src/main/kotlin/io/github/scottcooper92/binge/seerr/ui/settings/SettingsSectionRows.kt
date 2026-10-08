package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultAccess
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaServer
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerSettingsPage

/**
 * The server's settings as the web client's Settings menu lays them out: one row per section, in its order, each
 * opening the page that edits it, with a line saying what is set there now. A section the server doesn't have
 * (Network before Jellyseerr 2.4, Metadata before Seerr 3.0) or that this viewer can't read has no row.
 */
@Composable
internal fun serverSectionRows(
    config: ServerConfig,
    server: ServerSummary,
    onOpenPage: (ServerSettingsPage) -> Unit,
): List<ListItem> {
    val general = config.general
    val tint = BingeSentiment.Info.fill()
    val section = { icon: ImageVector, label: String, detail: String, page: ServerSettingsPage ->
        ListItem(icon = icon, iconTint = tint, label = label, detail = detail, onClick = { onOpenPage(page) })
    }
    return listOfNotNull(
        general?.let {
            section(Icons.Filled.Tune, stringResource(R.string.settings_group_general), it.sectionSummary(), ServerSettingsPage.General)
        },
        general?.takeIf { it.discoverSliders }?.let {
            section(
                Icons.Filled.ViewCarousel,
                stringResource(R.string.server_settings_sliders),
                stringResource(R.string.server_settings_sliders_caption),
                ServerSettingsPage.DiscoverSliders,
            )
        },
        config.requestPolicy?.let { policy ->
            section(
                Icons.Filled.Group,
                stringResource(R.string.hub_section_users),
                policy.policySummary(),
                ServerSettingsPage.DefaultPermissions,
            )
        },
        section(
            Icons.Filled.Storage,
            stringResource(server.mediaServer.labelRes()),
            stringResource(R.string.server_settings_media_server_caption),
            ServerSettingsPage.MediaServer,
        ),
        config.services?.let { services ->
            section(
                Icons.Filled.Hub,
                stringResource(R.string.settings_section_services),
                services.joinToString(", ") { it.name }.ifEmpty { stringResource(R.string.settings_section_services_none) },
                ServerSettingsPage.Services,
            )
        },
        general?.takeIf { it.network }?.let {
            section(
                Icons.Filled.Dns,
                stringResource(R.string.server_settings_network),
                stringResource(R.string.server_settings_network_caption),
                ServerSettingsPage.Network,
            )
        },
        general?.takeIf { it.metadata }?.let {
            section(
                Icons.Filled.Public,
                stringResource(R.string.server_settings_metadata),
                stringResource(R.string.server_settings_metadata_caption),
                ServerSettingsPage.Metadata,
            )
        },
        config.agents?.let { agents ->
            section(
                Icons.Filled.Notifications,
                stringResource(R.string.settings_section_notifications),
                agents.agentsSummary(),
                ServerSettingsPage.NotificationAgents,
            )
        },
        section(
            Icons.AutoMirrored.Filled.Article,
            stringResource(R.string.server_settings_logs),
            stringResource(R.string.server_settings_logs_caption),
            ServerSettingsPage.Logs,
        ),
        section(Icons.Filled.Cached, stringResource(R.string.server_settings_cache), config.system.jobsSummary(), ServerSettingsPage.Cache),
        section(
            Icons.Filled.Info,
            stringResource(R.string.settings_about),
            server.versionLabel
                ?.let { stringResource(R.string.setup_server_edition, server.variant.displayName, it) }
                ?: stringResource(R.string.settings_about_caption),
            ServerSettingsPage.About,
        ),
    )
}

/** The application's title and address, or what the page holds when neither is set. */
@Composable
private fun GeneralSettings.sectionSummary(): String =
    listOfNotNull(applicationTitle, applicationUrl)
        .filter(String::isNotBlank)
        .joinToString(" · ")
        .ifEmpty { stringResource(R.string.settings_section_general_detail) }

/** Which of the agents this app can read are on, or what the page is for when neither is: it never claims none are. */
@Composable
private fun NotificationAgents.agentsSummary(): String {
    val on =
        listOfNotNull(
            stringResource(R.string.settings_agent_email).takeIf { emailEnabled == true },
            stringResource(R.string.settings_agent_discord).takeIf { discordEnabled == true },
        )
    return if (on.isEmpty()) {
        stringResource(R.string.settings_section_agents_detail)
    } else {
        stringResource(R.string.settings_section_agents_on, on.joinToString(", "))
    }
}

internal fun SeerrMediaServer.labelRes(): Int =
    when (this) {
        SeerrMediaServer.Jellyfin -> R.string.user_origin_jellyfin
        SeerrMediaServer.Emby -> R.string.user_origin_emby
        SeerrMediaServer.Plex -> R.string.user_origin_plex
        SeerrMediaServer.NotConfigured, SeerrMediaServer.Unknown -> R.string.server_settings_media_server
    }

/** What a new user can do, and whether everyone's requests are limited: the Users page at a glance. */
@Composable
private fun RequestPolicy.policySummary(): String {
    val access =
        stringResource(
            when (defaultAccess) {
                SeerrDefaultAccess.NoRequests -> R.string.settings_users_access_none
                SeerrDefaultAccess.RequestWithApproval -> R.string.settings_users_access_request
                SeerrDefaultAccess.AutoApprove -> R.string.settings_users_access_auto
            },
        )
    return stringResource(
        if (movieLimit == null && tvLimit == null) R.string.settings_users_summary_unlimited else R.string.settings_users_summary_limited,
        access,
    )
}

/** A running job if there is one, else how many there are; the jobs list itself is on the Jobs & cache page. */
@Composable
private fun SystemInfo?.jobsSummary(): String {
    val jobs = this?.jobs ?: return stringResource(R.string.server_settings_cache_caption)
    val running = jobs.count { it.running }
    return if (running > 0) {
        pluralStringResource(R.plurals.settings_jobs_running, running, running)
    } else {
        pluralStringResource(R.plurals.settings_jobs_scheduled, jobs.size, jobs.size)
    }
}
