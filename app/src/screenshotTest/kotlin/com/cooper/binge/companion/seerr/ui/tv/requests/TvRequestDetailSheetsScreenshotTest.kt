package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.component.TvSideSheetPanel
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.MediaStatusChoice
import com.cooper.binge.companion.seerr.ui.requests.RequestEditorControls
import com.cooper.binge.companion.seerr.ui.requests.refusedEditState
import com.cooper.binge.companion.seerr.ui.requests.seasonListRow
import com.cooper.binge.companion.seerr.ui.requests.tvEditState

/**
 * The bodies of the request page's Edit, Mark as, Report an issue and Manage sheets, on the panel. The sheets' scrim
 * and focus trap are the modal half and are not framed; what is framed is every row's icon: an instance, a state, an
 * action.
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

    /** A pick being saved: its row spins in the tick's place, and the tick stays on the state the title is in until it lands. */
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
                initialPick = MediaStatusChoice.Processing,
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

    /** Reporting an issue on the phone: the title's page on the server as a code, under what to do with it. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ReportOnPhone() =
        SheetFrame {
            TvReportOnPhoneStep(webUrl = "https://seerr.example/movie/900", serverName = "Jellyseerr", entryFocus = FocusRequester())
        }

    /** Manage on a pending request: everything past Approve and Decline, with Block and Remove last, in red. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Manage() =
        SheetFrame {
            TvRequestManageStep(
                managed =
                    listOf(
                        managed(TvRequestAction.Edit, R.string.tv_detail_edit, Icons.Filled.Edit),
                        managed(TvRequestAction.MarkAs, R.string.media_mark_as, Icons.Filled.Done),
                        managed(TvRequestAction.Report, R.string.issue_report_title, Icons.Filled.ReportProblem),
                        managed(TvRequestAction.OpenInBinge, R.string.request_open_binge, Icons.AutoMirrored.Filled.OpenInNew),
                        managed(TvRequestAction.Block, R.string.tv_detail_block, Icons.Filled.Block),
                        managed(TvRequestAction.Remove, R.string.tv_detail_remove, Icons.Filled.Delete),
                    ),
                rowModifier = { Modifier },
                onSelect = {},
            )
        }
}

@Composable
private fun managed(
    action: TvRequestAction,
    @StringRes label: Int,
    icon: ImageVector,
) = TvRequestActionItem(action, stringResource(label), icon, onClick = {})

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

@Composable
private fun SheetFrame(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        TvSideSheetPanel(modifier = Modifier.align(Alignment.CenterEnd), content = content)
    }
}
