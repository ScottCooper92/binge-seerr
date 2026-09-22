package io.github.scottcooper92.binge.seerr.ui.tv.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Insights
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvQrCode
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.AppSettings
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetBody
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetRow
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetStepFocus
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetTitle
import io.github.scottcooper92.binge.seerr.ui.tv.TvPaneGroup
import io.github.scottcooper92.binge.seerr.ui.tv.TvPaneOption
import io.github.scottcooper92.binge.seerr.ui.tv.TvPaneRow
import com.binge.designsystem.R as DesR

internal const val KEY_REPORT_BUG = "report-bug"
internal const val KEY_SHARE_USAGE_DATA = "share-usage-data"
internal const val KEY_SEND_CRASH_REPORTS = "send-crash-reports"

/** What this app's group on a television can do. */
internal class TvAppActions(
    val onShowBugReport: () -> Unit,
    val onToggleShareUsageData: (Boolean) -> Unit,
    val onToggleSendCrashReports: (Boolean) -> Unit,
)

/**
 * This app's own group on a television: report a bug in it, and the two things it may report on its
 * own. A TV cannot open the form itself, so that option shows a code the phone scans. There is no
 * shake toggle here, because a TV has nothing to shake.
 */
@Composable
internal fun tvAppGroup(
    app: AppSettings,
    actions: TvAppActions,
    optionFocus: FocusRequester,
): TvPaneGroup =
    TvPaneGroup(
        title = stringResource(R.string.settings_group_app),
        rows =
            listOf(
                TvPaneRow(
                    key = KEY_REPORT_BUG,
                    label = stringResource(R.string.settings_report_bug),
                    body = stringResource(R.string.tv_settings_report_bug_body),
                    options =
                        listOf(
                            TvPaneOption(
                                label = stringResource(R.string.tv_settings_report_bug_show_code),
                                onSelect = actions.onShowBugReport,
                                focusRequester = optionFocus,
                            ),
                        ),
                    icon = Icons.Filled.BugReport,
                ),
                toggleRow(
                    key = KEY_SHARE_USAGE_DATA,
                    label = stringResource(R.string.settings_share_usage_data),
                    body = stringResource(R.string.settings_share_usage_data_caption),
                    on = app.shareUsageData,
                    onToggle = actions.onToggleShareUsageData,
                    icon = Icons.Filled.Insights,
                ),
                toggleRow(
                    key = KEY_SEND_CRASH_REPORTS,
                    label = stringResource(R.string.settings_send_crash_reports),
                    body = stringResource(R.string.settings_send_crash_reports_caption),
                    on = app.sendCrashReports,
                    onToggle = actions.onToggleSendCrashReports,
                    icon = Icons.Filled.Healing,
                ),
            ),
    )

/** A switch as a pane row: the body says where it stands, and the one option flips it. */
@Composable
private fun toggleRow(
    key: String,
    label: String,
    body: String,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    icon: ImageVector,
): TvPaneRow =
    TvPaneRow(
        key = key,
        label = label,
        body = body,
        note = stringResource(if (on) R.string.tv_settings_toggle_on else R.string.tv_settings_toggle_off),
        options =
            listOf(
                TvPaneOption(
                    label = stringResource(if (on) R.string.tv_settings_turn_off else R.string.tv_settings_turn_on),
                    onSelect = { onToggle(!on) },
                ),
            ),
        icon = icon,
    )

/** The code for the bug form with this install's details filled in, and the way back. */
@Composable
internal fun ColumnScope.TvBugReportSheetContent(
    app: AppSettings,
    onClose: () -> Unit,
    entryFocus: FocusRequester,
) {
    TvActionSheetTitle(stringResource(R.string.settings_report_bug))
    TvActionSheetBody(stringResource(R.string.tv_settings_report_bug_scan))
    TvQrCode(
        content = app.bugReportUrl,
        contentDescription = stringResource(R.string.tv_settings_report_bug_code_description),
        modifier =
            Modifier
                .padding(vertical = dimensionResource(DesR.dimen.padding_s))
                .size(dimensionResource(R.dimen.tv_bug_report_code_size)),
    )
    TvActionSheetRow(
        label = stringResource(R.string.tv_settings_report_bug_close),
        onClick = onClose,
        modifier = Modifier.focusRequester(entryFocus),
    )
    TvActionSheetStepFocus(entryFocus)
}
