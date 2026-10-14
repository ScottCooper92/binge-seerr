package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.tv.component.TvSideSheetPanel
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.ui.requests.IssueReport
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.RequestEditorControls
import com.cooper.binge.companion.seerr.ui.requests.refusedEditState
import com.cooper.binge.companion.seerr.ui.requests.seasonListRow
import com.cooper.binge.companion.seerr.ui.requests.tvEditState

/**
 * The bodies of the request page's Edit, Mark as and Report an issue sheets, on the panel. The sheets' scrim and focus
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

    /** The edit: a held season locked with the server's state, the others to choose, and the destination rows. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun EditForm() =
        SheetFrame {
            TvEditRequestSheet(
                item = seasonListRow(),
                edit = tvEditState(),
                controls = NoEditorControls,
                onClosed = {},
                entryFocus = FocusRequester(),
            )
        }

    /** A save the server refused: the status line offers Retry above Save, and the edit stays as the user left it. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun EditRefusedSave() =
        SheetFrame {
            TvEditRequestSheet(
                item = seasonListRow(),
                edit = refusedEditState(),
                controls = NoEditorControls,
                onClosed = {},
                entryFocus = FocusRequester(),
            )
        }

    /** One destination field's choices swapped in for the form, the current one ticked. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun EditServerPicker() =
        SheetFrame {
            TvEditRequestSheet(
                item = seasonListRow(),
                edit = tvEditState(),
                controls = NoEditorControls,
                onClosed = {},
                entryFocus = FocusRequester(),
                initialStep = EditStep.Server,
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

private object NoEditorControls : RequestEditorControls {
    override fun cancel() = Unit

    override fun toggleSeason(number: Int) = Unit

    override fun selectAllSeasons(selected: Boolean) = Unit

    override fun selectServer(id: Int) = Unit

    override fun selectProfile(id: Int) = Unit

    override fun selectRootFolder(path: String) = Unit

    override fun toggleTag(id: Int) = Unit

    override fun save() = Unit
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
