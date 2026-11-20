package com.cooper.binge.companion.seerr.feedback

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.openInBrowser
import com.cooper.homelab.core.uiandroid.feedback.ShakeToReportPrompt

/**
 * The shared shake prompt with this app's own words and opener: the confirm button is the same "Report a bug" the Settings row
 * uses, and the confirmed link opens through [openInBrowser], in a Custom Tab, as the Settings row and setup's Report a problem do.
 */
@Composable
fun SeerrShakeToReportPrompt(
    enabled: Boolean,
    bugReportUrl: () -> String,
) {
    val context = LocalContext.current
    ShakeToReportPrompt(
        enabled = enabled,
        confirmLabel = stringResource(R.string.settings_report_bug),
        bugReportUrl = bugReportUrl,
        openUrl = { context.openInBrowser(it) },
    )
}
