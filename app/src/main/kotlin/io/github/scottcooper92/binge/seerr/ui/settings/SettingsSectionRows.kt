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
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultAccess
import io.github.scottcooper92.binge.seerr.seerr.SeerrMediaServer
import io.github.scottcooper92.binge.seerr.ui.settings.server.JobsUiState
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerSettingsPage

/**
 * The server's settings as the web client lays them out: one row per section of its Settings menu, in its order, each
 * opening the page that edits that section, with a line saying what is set there now. PROTOTYPE copy, not yet strings.
 */
@Composable
internal fun serverSectionRows(
    config: ServerConfig,
    server: ServerSummary,
    jobs: JobsUiState,
    onOpenPage: (ServerSettingsPage) -> Unit,
): List<ListItem> {
    val general = config.general
    val tint = BingeSentiment.Info.fill()
    return listOfNotNull(
        general?.let {
            ListItem(
                icon = Icons.Filled.Tune,
                iconTint = tint,
                label = "General",
                detail = listOfNotNull(it.applicationTitle, it.applicationUrl).joinToString(" · ").ifEmpty { "Title, URL and discovery" },
                onClick = { onOpenPage(ServerSettingsPage.General) },
            )
        },
        config.requestPolicy?.let { policy ->
            ListItem(
                icon = Icons.Filled.Group,
                iconTint = tint,
                label = "Users",
                detail = policy.summary(),
                onClick = { onOpenPage(ServerSettingsPage.DefaultPermissions) },
            )
        },
        ListItem(
            icon = Icons.Filled.Storage,
            iconTint = tint,
            label =
                when (server.mediaServer) {
                    SeerrMediaServer.Jellyfin -> "Jellyfin"
                    SeerrMediaServer.Emby -> "Emby"
                    SeerrMediaServer.Plex -> "Plex"
                    SeerrMediaServer.NotConfigured, SeerrMediaServer.Unknown -> "Media server"
                },
            detail = "Server, libraries and sync",
            onClick = { onOpenPage(ServerSettingsPage.MediaServer) },
        ),
        config.services?.let { services ->
            ListItem(
                icon = Icons.Filled.Hub,
                iconTint = tint,
                label = "Services",
                detail =
                    if (services.isEmpty()) {
                        "No Radarr or Sonarr yet"
                    } else {
                        services.joinToString(", ") { it.name }
                    },
                onClick = { onOpenPage(ServerSettingsPage.Services) },
            )
        },
        general?.takeIf { it.network }?.let {
            ListItem(
                icon = Icons.Filled.Dns,
                iconTint = tint,
                label = "Network",
                detail = "Proxy, CSRF and DNS",
                onClick = { onOpenPage(ServerSettingsPage.Network) },
            )
        },
        general?.takeIf { it.metadata }?.let {
            ListItem(
                icon = Icons.Filled.Public,
                iconTint = tint,
                label = "Metadata providers",
                detail = "Where titles' details come from",
                onClick = { onOpenPage(ServerSettingsPage.Metadata) },
            )
        },
        config.agents?.let { agents ->
            val on = listOfNotNull("Email".takeIf { agents.emailEnabled == true }, "Discord".takeIf { agents.discordEnabled == true })
            ListItem(
                icon = Icons.Filled.Notifications,
                iconTint = tint,
                label = "Notifications",
                detail = if (on.isEmpty()) "No agents on" else on.joinToString(", ") + " on",
                onClick = { onOpenPage(ServerSettingsPage.NotificationAgents) },
            )
        },
        ListItem(
            icon = Icons.AutoMirrored.Filled.Article,
            iconTint = tint,
            label = "Logs",
            detail = stringResource(R.string.server_settings_logs_caption),
            onClick = { onOpenPage(ServerSettingsPage.Logs) },
        ),
        ListItem(
            icon = Icons.Filled.Cached,
            iconTint = tint,
            label = "Jobs & cache",
            detail = jobs.summary(config),
            onClick = { onOpenPage(ServerSettingsPage.Cache) },
        ),
        ListItem(
            icon = Icons.Filled.Info,
            iconTint = tint,
            label = "About",
            detail = server.versionLabel?.let { "${server.variant.displayName} $it" } ?: stringResource(R.string.settings_about_caption),
            onClick = { onOpenPage(ServerSettingsPage.About) },
        ),
    )
}

private fun RequestPolicy.summary(): String {
    val access =
        when (defaultAccess) {
            SeerrDefaultAccess.NoRequests -> "New users can't request"
            SeerrDefaultAccess.RequestWithApproval -> "New users request with approval"
            SeerrDefaultAccess.AutoApprove -> "New users' requests auto-approve"
        }
    return if (movieLimit == null && tvLimit == null) "$access · no limits" else "$access · limits set"
}

private fun JobsUiState.summary(config: ServerConfig): String {
    val jobs = (this as? JobsUiState.Ready)?.jobs
    val running = jobs?.count { it.running } ?: config.system?.jobs?.count { it.running } ?: 0
    val total = jobs?.size ?: config.system?.jobs?.size
    return when {
        running > 0 -> "$running running"
        total != null -> "$total scheduled jobs and the caches"
        else -> "Scheduled jobs and the caches"
    }
}
