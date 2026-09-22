package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.BingeConfirmDialogContent
import com.binge.designsystem.component.SettingsGroup
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews

private val SampleApp =
    AppSettings(
        bugReportUrl = "https://github.com/ScottCooper92/binge-seerr/issues/new?template=bug.yml",
        shakeToReport = true,
    )

/**
 * This app's own Settings group, and the confirm a shake opens. The confirm is a modal window,
 * which does not capture, so its frame renders the dialog's stateless content with this app's copy.
 */
class SettingsAppGroupScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun appGroup() =
        SettingsGroup(title = stringResource(R.string.settings_group_app), rows = appRows(SampleApp, onToggleShakeToReport = {}))

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun appGroupToggled() =
        SettingsGroup(
            title = stringResource(R.string.settings_group_app),
            rows =
                appRows(
                    SampleApp.copy(shakeToReport = false, shareUsageData = true, sendCrashReports = false),
                    onToggleShakeToReport = {},
                ),
        )

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun shakeConfirm() =
        BingeConfirmDialogContent(
            title = stringResource(R.string.shake_report_title),
            message = stringResource(R.string.shake_report_message),
            confirmLabel = stringResource(R.string.settings_report_bug),
            onConfirm = {},
            onDismiss = {},
        )
}
