package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrComponentPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrError

private val SHEET_WIDTH = 411.dp

/**
 * The report-an-issue sheet's body in each state its [IssueReport] can be in. The type chips and the
 * message field are the form's own state, so every frame starts with Video selected and no message,
 * which leaves Send disabled.
 */
class ReportIssueScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun idle() = SheetFrame(IssueReport.Idle)

    /** Sending disables the field and the button and shows the button's progress. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun sending() = SheetFrame(IssueReport.Sending)

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun sent() = SheetFrame(IssueReport.Sent)

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun failed() = SheetFrame(IssueReport.Failed(SeerrError.Unreachable))
}

@Composable
private fun SheetFrame(report: IssueReport) {
    Box(Modifier.width(SHEET_WIDTH).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        ReportIssueContent(report = report, onSend = { _, _ -> })
    }
}
