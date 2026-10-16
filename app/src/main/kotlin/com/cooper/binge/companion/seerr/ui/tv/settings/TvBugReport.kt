package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvQrCode
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.binge.designsystem.tv.template.TvPaneGroup
import com.binge.designsystem.tv.template.TvPaneOption
import com.binge.designsystem.tv.template.TvPaneRow
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.settings.AppSettings
import com.binge.designsystem.tv.R as TvR

internal const val KEY_BINGE = "binge"
internal const val KEY_REPORT_BUG = "report-bug"
internal const val KEY_SHARE_USAGE_DATA = "share-usage-data"
internal const val KEY_SEND_CRASH_REPORTS = "send-crash-reports"

/** What this app's group on a television can do. */
internal class TvAppActions(
    val onShowBugReport: () -> Unit,
    val onToggleShareUsageData: (Boolean) -> Unit,
    val onToggleSendCrashReports: (Boolean) -> Unit,
    val onOpenBinge: () -> Unit = {},
    val onConnectBinge: () -> Unit = {},
    val onOpenBingeListing: () -> Unit = {},
)

/**
 * This app's own group on a television: Binge first, then report a bug in this app, and the two things it may report on
 * its own. A TV cannot open the bug form itself, so that row shows a code the phone scans. There is no shake toggle here,
 * because a TV has nothing to shake. [binge] is Settings' own reading of Binge, as the phone's group has (#1599).
 */
@Composable
internal fun tvAppGroup(
    app: AppSettings,
    binge: BingeStatus,
    actions: TvAppActions,
): TvPaneGroup =
    TvPaneGroup(
        title = stringResource(R.string.settings_group_app),
        rows =
            listOf(
                bingeRow(binge, actions),
                TvPaneRow(
                    key = KEY_REPORT_BUG,
                    label = stringResource(R.string.settings_report_bug),
                    options =
                        listOf(
                            TvPaneOption(
                                label = stringResource(R.string.tv_settings_report_bug_show_code),
                                icon = Icons.Filled.QrCode,
                                onSelect = actions.onShowBugReport,
                            ),
                        ),
                    icon = Icons.Filled.BugReport,
                ),
                switchRow(
                    key = KEY_SHARE_USAGE_DATA,
                    label = stringResource(R.string.settings_share_usage_data),
                    on = app.shareUsageData,
                    icon = Icons.Filled.Insights,
                    onToggle = { actions.onToggleShareUsageData(!app.shareUsageData) },
                ),
                switchRow(
                    key = KEY_SEND_CRASH_REPORTS,
                    label = stringResource(R.string.settings_send_crash_reports),
                    on = app.sendCrashReports,
                    icon = Icons.Filled.Healing,
                    onToggle = { actions.onToggleSendCrashReports(!app.sendCrashReports) },
                ),
            ),
    )

/**
 * Binge, as the row that gets you to it: its Play Store listing when it is missing, its page for this app when it has not
 * connected yet (#1601), else Binge itself. Its option is the
 * action, so the row reads as what pressing it does.
 */
@Composable
private fun bingeRow(
    status: BingeStatus,
    actions: TvAppActions,
): TvPaneRow {
    val missing = status == BingeStatus.NotInstalled
    val action = stringResource(if (missing) R.string.tv_settings_binge_install else R.string.hub_binge_open)
    return TvPaneRow(
        key = KEY_BINGE,
        label = stringResource(R.string.settings_binge),
        options =
            listOf(
                TvPaneOption(
                    label = action,
                    icon = if (missing) Icons.Filled.Download else Icons.Filled.Tv,
                    onSelect =
                        when (status) {
                            BingeStatus.NotInstalled -> actions.onOpenBingeListing
                            BingeStatus.NotConnected -> actions.onConnectBinge
                            BingeStatus.Connected -> actions.onOpenBinge
                        },
                ),
            ),
        icon = Icons.Filled.Tv,
    )
}

/**
 * The code for the bug form with this install's details filled in, centred in what the sheet has left. Back is the way
 * out, so there is no Close: the code holds focus meanwhile, through [codeModifier].
 */
@Composable
internal fun ColumnScope.TvBugReportSheetContent(
    app: AppSettings,
    codeModifier: Modifier,
) {
    TvSideSheetTitle(stringResource(R.string.settings_report_bug))
    TvSideSheetBody(stringResource(R.string.tv_settings_report_bug_scan))
    val codeSize = dimensionResource(R.dimen.tv_bug_report_code_size)
    // The code takes what the sheet has left, up to its size, so a larger font shrinks it rather than pushing it off the edge.
    Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = dimensionResource(TvR.dimen.tv_overscan_vertical)),
        contentAlignment = Alignment.Center,
    ) {
        TvQrCode(
            content = app.bugReportUrl,
            contentDescription = stringResource(R.string.tv_settings_report_bug_code_description),
            modifier = codeModifier.focusable().sizeIn(maxWidth = codeSize, maxHeight = codeSize).aspectRatio(1f),
        )
    }
}
