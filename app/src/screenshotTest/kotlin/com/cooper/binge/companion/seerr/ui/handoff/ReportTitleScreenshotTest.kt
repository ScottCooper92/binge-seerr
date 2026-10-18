package com.cooper.binge.companion.seerr.ui.handoff

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrComponentPreviews
import com.cooper.binge.companion.seerr.preview.SheetFrame
import com.cooper.binge.companion.seerr.preview.SideSheetFrame
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.requests.IssueReport

private fun readyState(
    is4k: Boolean = false,
    report: IssueReport = IssueReport.Idle,
) = ReportTitleUiState.Ready(title = "Severance", is4k = is4k, mediaId = 5, report = report)

@Composable
private fun Body(state: ReportTitleUiState) = ReportTitleContent(state = state, actions = ReportTitleActions())

/**
 * What a phone shows once it has scanned the code a TV gives for "Report an issue" (#1511). Modal windows do not capture, so
 * each frame renders the stateless body at a phone's width, and the form again in the side sheet at the expanded width.
 */
class ReportTitleScreenshotTest {
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun checking() = SheetFrame { Body(ReportTitleUiState.Loading) }

    /** The form for the title, with the version the TV's code named. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun readyFourK() = SheetFrame { Body(readyState(is4k = true)) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun idle() = SheetFrame { Body(readyState()) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun readyExpanded() = SideSheetFrame { Body(readyState()) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun sent() = SheetFrame { Body(readyState(report = IssueReport.Sent)) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun failed() = SheetFrame { Body(readyState(report = IssueReport.Failed(SeerrError.Unreachable))) }

    /** A code for another server: nothing is sent, and the way forward is the app. */
    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun otherServer() =
        SheetFrame {
            Body(ReportTitleUiState.DifferentServer(theirs = "192.168.86.38:30042", yours = "100.103.24.117:30042", confirmed = true))
        }

    @SeerrComponentPreviews
    @Composable
    fun differentServer() = SheetFrame { Body(ReportTitleUiState.DifferentServer(theirs = "seerr.example", yours = "192.168.1.10:5055")) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun signedOut() = SheetFrame { Body(ReportTitleUiState.SignedOut) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun cannotReport() = SheetFrame { Body(ReportTitleUiState.CannotReport) }

    @PreviewTest
    @SeerrComponentPreviews
    @Composable
    fun notTracked() = SheetFrame { Body(ReportTitleUiState.NotTracked("Severance")) }
}
