package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.component.TvSideSheetPanel
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrMediaStatusCode
import com.cooper.binge.companion.seerr.ui.hub.HubQuotaBucket
import com.cooper.binge.companion.seerr.ui.requests.IssueReport
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.RequestEditorControls
import com.cooper.binge.companion.seerr.ui.requests.refusedEditState
import com.cooper.binge.companion.seerr.ui.requests.seasonListRow
import com.cooper.binge.companion.seerr.ui.requests.tvEditState
import com.cooper.binge.companion.seerr.ui.users.settings.QuotaDefault

/**
 * The bodies of the request page's Edit, Mark as, Report an issue and requester sheets, on the panel. The sheets'
 * scrim and focus trap are the modal half and are not framed; what is framed is every row's icon: an instance, a
 * state, a kind.
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

    /** A pick being saved: the tick has moved to it, and the sheet says "Saving…" under the options before it closes (#1519). */
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

    /** The requester sheet as a manager opens it: the quota left, both permissions, the override off, and a failed save. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Requester() =
        SheetFrame {
            TvRequesterStep(
                state =
                    requester(TvQuotaOverride(on = false, limit = 0, days = 7, default = QuotaDefault(10, 7)))
                        .copy(saveStatus = SaveStatus.Failed),
                isMovie = true,
                actions = noRequesterActions(),
                entryFocus = FocusRequester(),
            )
        }

    /** The override on, on a server that offers neither permission: the limit's stepper, then the windows, the current one ticked. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun RequesterOverride() =
        SheetFrame {
            TvRequesterStep(
                state =
                    requester(TvQuotaOverride(on = true, limit = 5, days = 3, default = QuotaDefault(10, 7)))
                        .copy(autoApprove = null, request4k = null),
                isMovie = true,
                actions = noRequesterActions(),
                entryFocus = FocusRequester(),
            )
        }

    /** A change the server refused: the control is back as it was, and the note under the rows says why, with no Retry. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun RequesterRefused() =
        SheetFrame {
            TvRequesterStep(
                state = requester(override = null).copy(saveStatus = SaveStatus.Idle),
                isMovie = true,
                actions = noRequesterActions(),
                entryFocus = FocusRequester(),
                note = stringResource(R.string.state_error_forbidden_message),
            )
        }

    /** Still loading: no name yet and no quota line, Request 4K locked, and no override for a viewer the server denies it. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun RequesterLoading() =
        SheetFrame {
            TvRequesterStep(
                state =
                    requester(override = null).copy(
                        name = null,
                        quotaKnown = false,
                        request4k = TvRequesterToggle(checked = true, enabled = false),
                    ),
                isMovie = false,
                actions = noRequesterActions(),
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

private fun requester(override: TvQuotaOverride?) =
    TvRequesterState(
        name = "ana",
        quota = HubQuotaBucket(limit = 10, remaining = 7, days = 7),
        quotaKnown = true,
        autoApprove = TvRequesterToggle(checked = true, enabled = true),
        request4k = TvRequesterToggle(checked = false, enabled = true),
        override = override,
        saveStatus = SaveStatus.Saved,
    )

private fun noRequesterActions() = TvRequesterActions({}, {}, {}, {})

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
