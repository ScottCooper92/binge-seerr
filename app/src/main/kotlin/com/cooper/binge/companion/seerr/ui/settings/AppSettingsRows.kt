package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.hub.openBinge
import com.cooper.binge.companion.seerr.ui.hub.openBingeOnPlayStore
import com.cooper.binge.companion.seerr.ui.openInBrowser

/**
 * This app's own group: Binge first, as on TV, then report a bug in it on GitHub, whether a shake offers to, and the two things
 * it may report on its own. Unlike every other group it is about the app, not the server, so it shows
 * for every user.
 */
@Composable
internal fun appRows(
    app: AppSettings,
    onToggleShakeToReport: (Boolean) -> Unit,
    onToggleShareUsageData: (Boolean) -> Unit,
    onToggleSendCrashReports: (Boolean) -> Unit,
): List<ListItem> {
    val context = LocalContext.current
    return listOf(
        bingeRow(app.binge, onOpen = context::openBinge, onGet = context::openBingeOnPlayStore),
        ListItem(
            icon = Icons.Filled.BugReport,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_report_bug),
            detail = stringResource(R.string.settings_report_bug_caption),
            destination = ListItemDestination.External,
            onClick = { context.openInBrowser(app.bugReportUrl) },
        ),
        ListItem(
            icon = Icons.Filled.Vibration,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_shake_to_report),
            toggled = app.shakeToReport,
            onClick = { onToggleShakeToReport(!app.shakeToReport) },
        ),
        ListItem(
            icon = Icons.Filled.Insights,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_share_usage_data),
            detail = stringResource(R.string.settings_share_usage_data_caption),
            toggled = app.shareUsageData,
            onClick = { onToggleShareUsageData(!app.shareUsageData) },
        ),
        ListItem(
            icon = Icons.Filled.Healing,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_send_crash_reports),
            toggled = app.sendCrashReports,
            onClick = { onToggleSendCrashReports(!app.sendCrashReports) },
        ),
    )
}

/**
 * Binge, as the row that gets you to it: open it when it is installed, else its Play listing. The second line says where
 * this app stands with it, as the hub's tile does, so the row is still worth reading once the tile is dismissed.
 */
@Composable
private fun bingeRow(
    status: BingeStatus,
    onOpen: () -> Unit,
    onGet: () -> Unit,
): ListItem =
    ListItem(
        icon = Icons.Filled.Tv,
        iconTint = BingeSentiment.Info.fill(),
        label = stringResource(R.string.tv_settings_binge),
        detail =
            stringResource(
                when (status) {
                    BingeStatus.NotInstalled -> R.string.hub_binge_not_installed_detail
                    BingeStatus.NotConnected -> R.string.hub_binge_connect_hint
                    BingeStatus.Connected -> R.string.hub_binge_connected_hint
                },
            ),
        supportingMaxLines = BINGE_DETAIL_MAX_LINES,
        destination = ListItemDestination.External,
        onClick = if (status == BingeStatus.NotInstalled) onGet else onOpen,
    )

private const val BINGE_DETAIL_MAX_LINES = 2
