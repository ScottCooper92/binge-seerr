package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.component.ListItemDestination
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.hub.openBinge
import com.cooper.binge.companion.seerr.ui.hub.openBingeCompanions
import com.cooper.binge.companion.seerr.ui.hub.openBingeOnPlayStore
import com.cooper.binge.companion.seerr.ui.search.SettingRows
import com.cooper.homelab.core.uiandroid.appsettings.reportBugRow
import com.cooper.homelab.core.uiandroid.appsettings.sendCrashReportsRow
import com.cooper.homelab.core.uiandroid.appsettings.shareUsageDataRow
import com.cooper.homelab.core.uiandroid.appsettings.tipRow

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
    onOpenTip: (() -> Unit)? = null,
    onToggleDiscovery: (Boolean) -> Unit = {},
    onOpenWatchPlayer: () -> Unit = {},
): List<ListItem> {
    val context = LocalContext.current
    return listOfNotNull(
        bingeRow(app.binge, onOpen = context::openBinge, onConnect = context::openBingeCompanions, onGet = context::openBingeOnPlayStore),
        watchPlayerRow(app.watchPlayer, onOpenWatchPlayer),
        reportBugRow(app.bugReportUrl, SettingRows.APP_REPORT_BUG),
        // Only while the remote flag offers discovery, which settings search cannot know, so the row has no search entry.
        app.discovery?.let { on ->
            ListItem(
                icon = Icons.Filled.Explore,
                label = stringResource(R.string.settings_discovery),
                detail = stringResource(R.string.settings_discovery_caption),
                toggled = on,
                onClick = { onToggleDiscovery(!on) },
            )
        },
        ListItem(
            icon = Icons.Filled.Vibration,
            label = stringResource(R.string.settings_shake_to_report),
            toggled = app.shakeToReport,
            onClick = { onToggleShakeToReport(!app.shakeToReport) },
        ).copy(key = SettingRows.APP_SHAKE_TO_REPORT),
        shareUsageDataRow(app.shareUsageData, onToggleShareUsageData, SettingRows.APP_SHARE_USAGE_DATA),
        sendCrashReportsRow(app.sendCrashReports, onToggleSendCrashReports, SettingRows.APP_SEND_CRASH_REPORTS),
        // The tip jar's row: only once Play has priced every step (see `TipState.Ready`).
        onOpenTip?.let { tipRow(it) },
    )
}

/**
 * Binge, as the row that gets you to it: its Play listing when it is missing, its page for this app when it has not connected
 * yet, else Binge itself. The second line says where
 * this app stands with it, as the hub's tile does, so the row is still worth reading once the tile is dismissed.
 */
@Composable
private fun bingeRow(
    status: BingeStatus,
    onOpen: () -> Unit,
    onConnect: () -> Unit,
    onGet: () -> Unit,
): ListItem? {
    val (detail, onClick) =
        when (status) {
            BingeStatus.NotInstalled -> R.string.hub_binge_not_installed_detail to onGet
            BingeStatus.NotConnected -> R.string.hub_binge_connect_hint to onConnect
            BingeStatus.Connected -> R.string.hub_binge_connected_hint to onOpen
            BingeStatus.Hidden -> return null
        }
    return ListItem(
        icon = Icons.Filled.Tv,
        label = stringResource(R.string.settings_binge_preferences),
        detail = stringResource(detail),
        supportingMaxLines = BINGE_DETAIL_MAX_LINES,
        destination = ListItemDestination.External,
        onClick = onClick,
    )
}

private const val BINGE_DETAIL_MAX_LINES = 2
