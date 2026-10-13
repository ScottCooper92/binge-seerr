package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.component.TvSideSheetPanel
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.ui.requests.IssueReport
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord

/**
 * The bodies of the request page's Mark as and Report an issue sheets, on the panel. The sheets' scrim and focus
 * trap are the modal half and are not framed; what is framed is every row's icon: an instance, a state, a kind.
 */
class TvRequestDetailSheetsScreenshotTest {
    /** A title the server holds twice: which instance first, each with the glyph its web client gives it. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun MarkAsInstances() =
        SheetFrame {
            TvMarkAsSteps(
                media =
                    media(
                        MediaInstance(false, SeerrMediaStatusCode.Available, null, null, null),
                        MediaInstance(true, null, null, null, null),
                    ),
                onSelect = { _, _ -> },
                entryFocus = FocusRequester(),
            )
        }

    /** One instance: the states, each with its own glyph, the current one ticked. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun MarkAsStates() =
        SheetFrame {
            TvMarkAsSteps(
                media = media(MediaInstance(false, SeerrMediaStatusCode.Available, null, null, null)),
                onSelect = { _, _ -> },
                entryFocus = FocusRequester(),
            )
        }

    /** A pick being saved: the tick has moved to it, and the sheet says "Saving…" under the options before it closes (#1510). */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun MarkAsSaving() =
        SheetFrame {
            TvMarkAsSteps(
                media = media(MediaInstance(false, SeerrMediaStatusCode.Available, null, null, null)),
                onSelect = { _, _ -> },
                entryFocus = FocusRequester(),
                status = SaveStatus.Saving,
            )
        }

    /** A pick the server refused: the tick is back where it was, and the line offers Retry. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun MarkAsFailed() =
        SheetFrame {
            TvMarkAsSteps(
                media = media(MediaInstance(false, SeerrMediaStatusCode.Available, null, null, null)),
                onSelect = { _, _ -> },
                entryFocus = FocusRequester(),
                status = SaveStatus.Failed,
            )
        }

    /** The kinds of issue, each with its glyph, over the message field. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ReportKinds() =
        SheetFrame {
            TvReportIssueStep(
                report = IssueReport.Idle,
                draft = TvReportDraft(type = IssueType.Video, message = "", onTypeChange = {}, onMessageChange = {}),
                actions = noActions(),
                onDone = {},
                entryFocus = FocusRequester(),
            )
        }
}

private fun media(vararg instances: MediaInstance) =
    MediaRecord(
        mediaId = 900,
        isTv = false,
        instances = instances.toList(),
        canSetStatus = true,
        canClearData = false,
        canDeleteFiles = false,
    )

private fun noActions() =
    TvRequestDetailActions(
        onBack = {},
        onRetry = {},
        onOpenInBinge = null,
        onApprove = {},
        onRetryRequest = {},
        onDecline = {},
        onRemove = {},
        onBlock = {},
        onSetMediaStatus = { _, _, _ -> },
        onReportIssue = { _, _ -> },
        onDismissReport = {},
    )

@Composable
private fun SheetFrame(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        TvSideSheetPanel(modifier = Modifier.align(Alignment.CenterEnd), content = content)
    }
}
