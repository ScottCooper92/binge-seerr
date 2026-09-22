package io.github.scottcooper92.binge.seerr.ui.tv.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

/**
 * This app's own group on a television: report a bug in it. A TV cannot open the form itself, so the
 * option shows a code the phone scans. There is no shake toggle here, because a TV has nothing to shake.
 */
@Composable
internal fun tvAppGroup(
    onShowBugReport: () -> Unit,
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
                                onSelect = onShowBugReport,
                                focusRequester = optionFocus,
                            ),
                        ),
                    icon = Icons.Filled.BugReport,
                ),
            ),
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
