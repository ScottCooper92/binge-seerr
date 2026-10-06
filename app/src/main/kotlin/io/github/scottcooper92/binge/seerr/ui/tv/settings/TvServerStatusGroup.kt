package io.github.scottcooper92.binge.seerr.ui.tv.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.hub.BingeStatus
import io.github.scottcooper92.binge.seerr.ui.hub.ConnectionHealth
import io.github.scottcooper92.binge.seerr.ui.hub.HubUiState
import io.github.scottcooper92.binge.seerr.ui.tv.TvPaneGroup
import io.github.scottcooper92.binge.seerr.ui.tv.TvPaneOption
import io.github.scottcooper92.binge.seerr.ui.tv.TvPaneRow

internal const val KEY_STATUS = "status"
internal const val KEY_ACTIVITY = "activity"
internal const val KEY_USERS = "users"
internal const val KEY_OPEN_ISSUES = "open-issues"
internal const val KEY_BINGE = "binge"

/**
 * What the home board used to carry at its head: whether the server is answering, the counts it keeps, and how
 * Binge stands with this app. Read-outs, except Binge when it is missing, which opens its Play Store listing.
 * Null until the hub has read the server, so Settings never shows a status it has not checked.
 */
@Composable
internal fun serverStatusGroup(
    hub: HubUiState,
    onOpenBingeListing: () -> Unit,
): TvPaneGroup? {
    val ready = hub as? HubUiState.Ready ?: return null
    val overview = ready.overview
    val separator = stringResource(R.string.hub_meta_separator)
    val placeholder = stringResource(R.string.hub_stat_placeholder)
    val health =
        stringResource(
            when (ready.health) {
                ConnectionHealth.Healthy -> R.string.hub_health_connected
                ConnectionHealth.Checking -> R.string.tv_hub_health_checking
                ConnectionHealth.Unauthorized -> R.string.hub_unauthorized_headline
                ConnectionHealth.CouldNotLoad -> R.string.hub_couldnt_load_headline
                ConnectionHealth.LocalNetworkDenied -> R.string.hub_local_network_headline
                ConnectionHealth.Unreachable -> R.string.hub_unreachable_headline
            },
        )
    return TvPaneGroup(
        title = stringResource(R.string.tv_settings_group_server),
        rows =
            listOfNotNull(
                TvPaneRow(
                    key = KEY_STATUS,
                    label = stringResource(R.string.tv_settings_status),
                    body =
                        listOfNotNull(
                            health,
                            stringResource(R.string.hub_update_available).takeIf { ready.server.updateAvailable },
                        ).joinToString(separator),
                    icon = Icons.Filled.CheckCircle,
                ),
                TvPaneRow(
                    key = KEY_ACTIVITY,
                    label = stringResource(R.string.hub_section_requests),
                    body =
                        listOf(
                            "${overview.pendingRequestCount ?: placeholder} ${stringResource(R.string.hub_stat_pending)}",
                            "${overview.movieRequestCount ?: placeholder} ${stringResource(R.string.hub_quota_movies)}",
                            "${overview.tvRequestCount ?: placeholder} ${stringResource(R.string.hub_quota_tv)}",
                        ).joinToString(separator),
                    icon = Icons.Filled.Inbox,
                ),
                TvPaneRow(
                    key = KEY_USERS,
                    label = stringResource(R.string.hub_section_users),
                    body = overview.userCount?.toString() ?: placeholder,
                    icon = Icons.Filled.Group,
                ),
                TvPaneRow(
                    key = KEY_OPEN_ISSUES,
                    label = stringResource(R.string.tv_hub_stat_open_issues),
                    body = overview.openIssueCount?.toString() ?: placeholder,
                    icon = Icons.Filled.ReportProblem,
                ).takeIf { overview.hasIssues && overview.permissions.canSeeIssues },
                TvPaneRow(
                    key = KEY_BINGE,
                    label = stringResource(R.string.tv_settings_binge),
                    body =
                        stringResource(
                            when (ready.bingeStatus) {
                                BingeStatus.NotInstalled -> R.string.hub_binge_not_installed_detail
                                BingeStatus.NotConnected -> R.string.connected_hint
                                BingeStatus.Connected -> R.string.hub_binge_connected_hint
                            },
                        ),
                    options =
                        if (ready.bingeStatus == BingeStatus.NotInstalled) {
                            listOf(TvPaneOption(label = stringResource(R.string.tv_settings_binge_install), onSelect = onOpenBingeListing))
                        } else {
                            emptyList()
                        },
                    icon = Icons.Filled.Tv,
                ),
            ),
    )
}
