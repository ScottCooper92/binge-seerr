package com.cooper.binge.companion.seerr.ui.requests

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame
import com.cooper.binge.companion.seerr.preview.SideSheetFrame
import com.cooper.binge.companion.seerr.seerr.SeerrError

/**
 * The report-an-issue sheet's body in each state its [IssueReport] can be in. The type chips and the
 * message field are the form's own state, so every frame starts with Video selected and no message,
 * which leaves Send disabled.
 */
class ReportIssueScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun idle() = ReportIssueFrame(IssueReport.Idle)

    /** Sending disables the field and the button and shows the button's progress. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun sending() = ReportIssueFrame(IssueReport.Sending)

    /** At the expanded width the form is a side sheet. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun expanded() = ReportIssueFrame(IssueReport.Idle, side = true)

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun sent() = ReportIssueFrame(IssueReport.Sent)

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun failed() = ReportIssueFrame(IssueReport.Failed(SeerrError.Unreachable))
}

@Composable
private fun ReportIssueFrame(
    report: IssueReport,
    side: Boolean = false,
) {
    if (side) {
        SideSheetFrame { ReportIssueContent(report = report, onSend = { _, _ -> }) }
    } else {
        SheetFrame { ReportIssueContent(report = report, onSend = { _, _ -> }) }
    }
}
