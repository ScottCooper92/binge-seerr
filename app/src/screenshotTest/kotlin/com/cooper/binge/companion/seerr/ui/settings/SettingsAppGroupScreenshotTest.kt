package com.cooper.binge.companion.seerr.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.component.BingeConfirmDialogContent
import com.binge.designsystem.component.ItemGroup
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.homelab.core.uiandroid.R as CoreR

private val SampleApp =
    AppSettings(
        bugReportUrl = "https://github.com/ScottCooper92/app-feedback/issues/new?template=seerr-bug.yml",
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
        ItemGroup(
            title = stringResource(R.string.settings_group_app),
            rows =
                appRows(SampleApp, onToggleShakeToReport = {
                }, onToggleShareUsageData = {}, onToggleSendCrashReports = {}),
        )

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun appGroupToggled() =
        ItemGroup(
            title = stringResource(R.string.settings_group_app),
            rows =
                appRows(
                    SampleApp.copy(shakeToReport = false, shareUsageData = true, sendCrashReports = false),
                    onToggleShakeToReport = {},
                    onToggleShareUsageData = {},
                    onToggleSendCrashReports = {},
                ),
        )

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun shakeConfirm() =
        BingeConfirmDialogContent(
            title = stringResource(CoreR.string.shake_report_title),
            message = stringResource(CoreR.string.shake_report_message),
            confirmLabel = stringResource(R.string.settings_report_bug),
            onConfirm = {},
            onDismissRequest = {},
        )
}
