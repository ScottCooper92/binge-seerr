package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.component.TvConfirmDialog
import com.binge.designsystem.tv.component.TvOptionRow
import com.binge.designsystem.tv.component.TvSideSheet
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetStepFocus
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.binge.designsystem.tv.component.TvTextField
import com.binge.designsystem.tv.focus.TvOverlayCloser
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.requests.EditState
import com.cooper.binge.companion.seerr.ui.requests.IssueReport
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.MediaStatusChoice
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.binge.companion.seerr.ui.requests.icon
import com.cooper.binge.companion.seerr.ui.requests.labelRes
import com.cooper.binge.companion.seerr.ui.tv.TvFormNote
import com.cooper.binge.companion.seerr.ui.tv.TvFormNoteTone
import com.binge.designsystem.R as DesR

/** What the page opens over itself: the two confirm dialogs, and the sheets for editing the request, marking the title's state and reporting an issue. */
internal enum class DetailSheet { Remove, Block, Edit, MarkAs, Report }

/** What the sheets read from the page: the request, its title's record, the open editor and the issue report. */
internal class TvDetailSheetContent(
    val item: RequestItem,
    val media: MediaRecord?,
    val edit: EditState?,
    val report: IssueReport,
)

/** The issue being written, kept by the page so a sheet closed before sending opens again on the same words. */
internal class TvReportDraft(
    val type: IssueType,
    val message: String,
    val onTypeChange: (IssueType) -> Unit,
    val onMessageChange: (String) -> Unit,
)

@Composable
internal fun rememberTvReportDraft(): TvReportDraft {
    var type by rememberSaveable { mutableStateOf(IssueType.Video) }
    var message by rememberSaveable { mutableStateOf("") }
    return TvReportDraft(type = type, message = message, onTypeChange = { type = it }, onMessageChange = { message = it })
}

/** The open sheet, and the requester its closer hands focus back to. */
internal class TvDetailSheetHost(
    val step: DetailSheet,
    val closer: TvOverlayCloser,
)

/** The sheet [host] names, over the page. Each closes through its own closer, so focus returns to the button that opened it. */
@Composable
internal fun TvRequestDetailSheet(
    host: TvDetailSheetHost,
    actions: TvRequestDetailActions,
    content: TvDetailSheetContent,
    draft: TvReportDraft,
) {
    val closer = host.closer
    val media = content.media
    // A report closed while it is still sending keeps its guard: the view model refuses a second send until it answers.
    // An edit closed unsaved is dropped, as the phone's sheet drops it.
    val dismiss = {
        when (host.step) {
            DetailSheet.Report -> actions.onDismissReport()
            DetailSheet.Edit -> actions.edit?.controls?.cancel()
            else -> Unit
        }
        closer.close()
    }
    // The two confirms are a question and nothing else, so each is a dialog; Mark as and Report carry a list and a form, so
    // they stay sheets.
    when (host.step) {
        DetailSheet.Remove ->
            TvConfirmDialog(
                title = stringResource(R.string.request_remove_confirm_title),
                message = stringResource(R.string.request_remove_confirm_message),
                confirmLabel = stringResource(R.string.request_remove),
                onConfirm = {
                    actions.onRemove(false)
                    closer.close()
                },
                onDismissRequest = closer::close,
                destructive = true,
            )
        DetailSheet.Block ->
            TvConfirmDialog(
                title = stringResource(R.string.request_block_confirm_title),
                message = stringResource(R.string.request_block_confirm_message),
                confirmLabel = stringResource(R.string.tv_detail_block),
                onConfirm = {
                    actions.onBlock()
                    closer.close()
                },
                onDismissRequest = closer::close,
                destructive = true,
            )
        DetailSheet.Edit ->
            TvSideSheet(onDismissRequest = dismiss) { entryFocus ->
                actions.edit?.let { edit ->
                    TvEditRequestSheet(content.item, content.edit, edit.controls, onClosed = closer::close, entryFocus = entryFocus)
                }
            }
        DetailSheet.MarkAs ->
            TvSideSheet(onDismissRequest = dismiss) { entryFocus ->
                media?.let {
                    TvMarkAsSteps(
                        media = it,
                        onSelect = { instance, choice ->
                            actions.onSetMediaStatus(it.mediaId, choice, instance.is4k)
                            closer.close()
                        },
                        entryFocus = entryFocus,
                    )
                }
            }
        DetailSheet.Report ->
            TvSideSheet(onDismissRequest = dismiss) { entryFocus ->
                TvReportIssueStep(content.report, draft, actions, onDone = dismiss, entryFocus = entryFocus)
            }
    }
}

/**
 * Marking the title's state, as the phone's Mark as sheet does: which instance first where the server holds the
 * title twice, then the states, the current one ticked. A pick applies and closes.
 */
@Composable
internal fun ColumnScope.TvMarkAsSteps(
    media: MediaRecord,
    onSelect: (MediaInstance, MediaStatusChoice) -> Unit,
    entryFocus: FocusRequester,
) {
    var picked by rememberSaveable { mutableStateOf(media.instances.singleOrNull()?.is4k) }
    val instance = media.instances.firstOrNull { it.is4k == picked }
    TvSideSheetTitle(stringResource(R.string.media_mark_as))
    if (instance == null) {
        media.instances.forEachIndexed { index, candidate ->
            TvSideSheetRow(
                label = stringResource(candidate.labelRes()),
                icon = candidate.icon(media.isTv),
                onClick = { picked = candidate.is4k },
                modifier = if (index == 0) Modifier.focusRequester(entryFocus) else Modifier,
            )
        }
        TvSideSheetStepFocus(entryFocus)
    } else {
        TvSideSheetBody(stringResource(instance.labelRes()) + "\n" + stringResource(R.string.media_mark_as_scope_caption))
        MediaStatusChoice.entries.forEachIndexed { index, choice ->
            val current = choice.code == instance.status
            TvOptionRow(
                label = stringResource(choice.labelRes()),
                icon = choice.icon(),
                selected = current,
                onClick = { if (!current) onSelect(instance, choice) },
                modifier = if (index == 0) Modifier.focusRequester(entryFocus) else Modifier,
            )
        }
        TvSideSheetStepFocus(entryFocus)
    }
}

private fun MediaInstance.labelRes(): Int = if (is4k) R.string.settings_service_4k else R.string.media_instance_standard

/**
 * Reporting an issue with the title: the kind, then the message, then Send. It scrolls, since the kinds and the field
 * outgrow the panel on a short screen. Once the server has it, the sheet says so and Done closes it.
 */
@Composable
internal fun ColumnScope.TvReportIssueStep(
    report: IssueReport,
    draft: TvReportDraft,
    actions: TvRequestDetailActions,
    onDone: () -> Unit,
    entryFocus: FocusRequester,
) {
    TvSideSheetTitle(stringResource(R.string.issue_report_title))
    if (report == IssueReport.Sent) {
        // Sent: the next report starts from nothing.
        LaunchedEffect(Unit) { draft.onMessageChange("") }
        TvSideSheetBody(stringResource(R.string.issue_report_sent))
        TvSideSheetRow(label = stringResource(R.string.editor_done), onClick = onDone, modifier = Modifier.focusRequester(entryFocus))
        TvSideSheetStepFocus(entryFocus)
        return
    }
    val sending = report == IssueReport.Sending
    val canSend = draft.message.isNotBlank() && !sending
    val send = { if (canSend) actions.onReportIssue(draft.type, draft.message) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
    ) {
        IssueType.entries.forEachIndexed { index, type ->
            TvOptionRow(
                label = stringResource(type.labelRes()),
                icon = type.icon(),
                selected = type == draft.type,
                onClick = { draft.onTypeChange(type) },
                modifier = if (index == 0) Modifier.focusRequester(entryFocus) else Modifier,
            )
        }
        TvTextField(
            value = draft.message,
            onValueChange = draft.onMessageChange,
            label = stringResource(R.string.issue_report_message),
            enabled = !sending,
            placeholder = stringResource(R.string.issue_message_placeholder),
            fillWidth = true,
            onImeAction = send,
        )
        if (report is IssueReport.Failed) TvFormNote(stringResource(R.string.issue_report_failed), tone = TvFormNoteTone.Error)
        TvSideSheetRow(label = stringResource(R.string.issue_report_send), onClick = send, enabled = canSend)
    }
    TvSideSheetStepFocus(entryFocus)
}
