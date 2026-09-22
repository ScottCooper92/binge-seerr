package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.SettingsRow
import com.binge.designsystem.component.SettingsRowDestination
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openInBrowser

/**
 * This app's own group: report a bug in it on GitHub, whether a shake offers to, and the two things
 * it may report on its own. Unlike every other group it is about the app, not the server, so it shows
 * for every user.
 */
@Composable
internal fun appRows(
    app: AppSettings,
    onToggleShakeToReport: (Boolean) -> Unit,
    onToggleShareUsageData: (Boolean) -> Unit,
    onToggleSendCrashReports: (Boolean) -> Unit,
): List<SettingsRow> {
    val context = LocalContext.current
    return listOf(
        SettingsRow(
            icon = Icons.Filled.BugReport,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_report_bug),
            detail = stringResource(R.string.settings_report_bug_caption),
            destination = SettingsRowDestination.External,
            onClick = { context.openInBrowser(app.bugReportUrl) },
        ),
        SettingsRow(
            icon = Icons.Filled.Vibration,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_shake_to_report),
            detail = stringResource(R.string.settings_shake_to_report_caption),
            trailingContent = { Switch(checked = app.shakeToReport, onCheckedChange = null) },
            onClick = { onToggleShakeToReport(!app.shakeToReport) },
        ),
        SettingsRow(
            icon = Icons.Filled.Insights,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_share_usage_data),
            detail = stringResource(R.string.settings_share_usage_data_caption),
            trailingContent = { Switch(checked = app.shareUsageData, onCheckedChange = null) },
            onClick = { onToggleShareUsageData(!app.shareUsageData) },
        ),
        SettingsRow(
            icon = Icons.Filled.Healing,
            iconTint = BingeSentiment.Info.fill(),
            label = stringResource(R.string.settings_send_crash_reports),
            detail = stringResource(R.string.settings_send_crash_reports_caption),
            trailingContent = { Switch(checked = app.sendCrashReports, onCheckedChange = null) },
            onClick = { onToggleSendCrashReports(!app.sendCrashReports) },
        ),
    )
}
