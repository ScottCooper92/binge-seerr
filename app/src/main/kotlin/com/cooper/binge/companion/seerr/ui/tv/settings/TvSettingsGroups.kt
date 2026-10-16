package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.tv.template.TvPaneGroup
import com.binge.designsystem.tv.template.TvPaneRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.settings.ServerService
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.JobsUiState

internal const val KEY_DISCONNECT = "disconnect"
internal const val KEY_MEDIA_SERVER = "media-server"
internal const val KEY_SERVICES = "services"
internal const val KEY_SCHEDULE = "schedule"

/** What the settings sheet can ask of its host, in one place so the entry stays a wiring. */
internal class TvSettingsActions(
    val onDisconnect: () -> Unit = {},
    val jobs: TvJobActions = TvJobActions.None,
    val onToggleShareUsageData: (Boolean) -> Unit = {},
    val onToggleSendCrashReports: (Boolean) -> Unit = {},
    val onOpenBinge: () -> Unit = {},
    val onConnectBinge: () -> Unit = {},
    val onOpenBingeListing: () -> Unit = {},
    /** The household's switches, for an admin; null leaves the group out. */
    val household: TvHousehold? = null,
    val agents: TvAgents? = null,
)

/** One of the sheet's groups: its page, and the [icon] its row on the first page leads with. */
internal class TvSettingsGroup(
    val page: TvPaneGroup,
    val icon: ImageVector,
)

/**
 * The sheet's groups, in order: the media server and the services requests go to, the server's maintenance jobs, the
 * household's switches and the notification agents (an admin's), and this app. The summary leads the first page and
 * Disconnect ends it; neither is a group. A group with nothing in it is left out.
 */
@Composable
internal fun tvSettingGroups(
    state: SettingsUiState.Ready,
    actions: TvSettingsActions,
    jobs: TvJobs,
    onShowBugReport: () -> Unit,
    agents: TvAgents? = actions.agents,
): List<TvSettingsGroup> {
    val config = state.config
    val app =
        state.app?.let {
            tvAppGroup(
                app = it,
                binge = it.binge,
                actions =
                    TvAppActions(
                        onShowBugReport = onShowBugReport,
                        onToggleShareUsageData = actions.onToggleShareUsageData,
                        onToggleSendCrashReports = actions.onToggleSendCrashReports,
                        onOpenBinge = actions.onOpenBinge,
                        onConnectBinge = actions.onConnectBinge,
                        onOpenBingeListing = actions.onOpenBingeListing,
                    ),
            )
        }
    return listOfNotNull(
        config?.let {
            TvSettingsGroup(
                TvPaneGroup(
                    title = stringResource(R.string.tv_settings_group_media),
                    rows = listOf(mediaServerRow(state.server, jobs, state.now)) + it.services.orEmpty().readOuts(),
                ),
                Icons.Filled.Storage,
            )
        },
        config?.system?.let { system ->
            val readyJobs = jobs.state as? JobsUiState.Ready
            val rows =
                if (readyJobs != null) {
                    tvJobRows(jobs, readyJobs, state.now)
                } else {
                    // The settings read of the jobs, until the jobs list is in: something to read, nothing to press yet.
                    factsFor(
                        KEY_SCHEDULE,
                        stringResource(R.string.settings_group_system),
                        Icons.Filled.Cached,
                        systemRows(system, state.now),
                    )
                }
            TvSettingsGroup(TvPaneGroup(title = stringResource(R.string.tv_settings_group_maintenance), rows = rows), Icons.Filled.Build)
        },
        actions.household?.let { TvSettingsGroup(householdGroup(it), Icons.Filled.Home) },
        agents?.let { agentsGroup(it) }?.let { TvSettingsGroup(it, Icons.Filled.Notifications) },
        app?.let { TvSettingsGroup(it, Icons.Filled.Apps) },
    ).filter { it.page.rows.isNotEmpty() }
}

/**
 * A page's read-outs, to be drawn as one card: the first row names it, and each after it is a line. Each row is keyed by
 * place, not label: Seerr doesn't make instance names unique, and two rows sharing a key share focus (#865).
 */
internal fun factsFor(
    key: String,
    title: String,
    icon: ImageVector,
    rows: List<ListItem>,
): List<TvPaneRow> =
    if (rows.isEmpty()) {
        emptyList()
    } else {
        listOf(TvPaneRow(key = key, label = title, icon = icon)) +
            rows.mapIndexed { index, row ->
                TvPaneRow(key = "$key/$index", label = row.label, body = row.detail.orEmpty(), icon = row.icon)
            }
    }

@Composable
private fun List<ServerService>.readOuts(): List<TvPaneRow> =
    factsFor(KEY_SERVICES, stringResource(R.string.settings_group_services), Icons.Filled.Dns, serviceRows(this))
