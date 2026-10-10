package io.github.scottcooper92.binge.seerr.ui.tv.requests

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
import com.binge.designsystem.tv.focus.TvOverlayCloser
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.IssueReport
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.MediaInstance
import io.github.scottcooper92.binge.seerr.ui.requests.MediaRecord
import io.github.scottcooper92.binge.seerr.ui.requests.MediaStatusChoice
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheet
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetBody
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetConfirm
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetRow
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetStepFocus
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetTitle
import io.github.scottcooper92.binge.seerr.ui.tv.TvFormNote
import io.github.scottcooper92.binge.seerr.ui.tv.TvFormNoteTone
import io.github.scottcooper92.binge.seerr.ui.tv.TvOptionRow
import io.github.scottcooper92.binge.seerr.ui.tv.TvTextField
import com.binge.designsystem.R as DesR

/** The sheets the page opens over itself: the two confirms, marking the title's state, and reporting an issue. */
internal enum class DetailSheet { Remove, Block, MarkAs, Report }

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
    media: MediaRecord?,
    report: IssueReport,
    draft: TvReportDraft,
) {
    val closer = host.closer
    // A report closed while it is still sending keeps its guard: the view model refuses a second send until it answers.
    val dismiss = {
        if (host.step == DetailSheet.Report) actions.onDismissReport()
        closer.close()
    }
    TvActionSheet(onDismiss = dismiss) { entryFocus ->
        when (host.step) {
            DetailSheet.Remove ->
                TvActionSheetConfirm(
                    title = stringResource(R.string.request_remove_confirm_title),
                    message = stringResource(R.string.request_remove_confirm_message),
                    confirmLabel = stringResource(R.string.request_remove),
                    onConfirm = {
                        actions.onRemove(false)
                        closer.close()
                    },
                    onCancel = closer::close,
                    entryFocus = entryFocus,
                )
            DetailSheet.Block ->
                TvActionSheetConfirm(
                    title = stringResource(R.string.request_block_confirm_title),
                    message = stringResource(R.string.request_block_confirm_message),
                    confirmLabel = stringResource(R.string.tv_detail_block),
                    onConfirm = {
                        actions.onBlock()
                        closer.close()
                    },
                    onCancel = closer::close,
                    entryFocus = entryFocus,
                )
            DetailSheet.MarkAs ->
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
            DetailSheet.Report -> TvReportIssueStep(report, draft, actions, onDone = dismiss, entryFocus = entryFocus)
        }
    }
}

/**
 * Marking the title's state, as the phone's Mark as sheet does: which instance first where the server holds the
 * title twice, then the states, the current one ticked. A pick applies and closes.
 */
@Composable
private fun ColumnScope.TvMarkAsSteps(
    media: MediaRecord,
    onSelect: (MediaInstance, MediaStatusChoice) -> Unit,
    entryFocus: FocusRequester,
) {
    var picked by rememberSaveable { mutableStateOf(media.instances.singleOrNull()?.is4k) }
    val instance = media.instances.firstOrNull { it.is4k == picked }
    TvActionSheetTitle(stringResource(R.string.media_mark_as))
    if (instance == null) {
        media.instances.forEachIndexed { index, candidate ->
            TvActionSheetRow(
                label = stringResource(candidate.labelRes()),
                onClick = { picked = candidate.is4k },
                modifier = if (index == 0) Modifier.focusRequester(entryFocus) else Modifier,
            )
        }
        TvActionSheetStepFocus(entryFocus)
    } else {
        TvActionSheetBody(stringResource(instance.labelRes()) + "\n" + stringResource(R.string.media_mark_as_scope_caption))
        MediaStatusChoice.entries.forEachIndexed { index, choice ->
            val current = choice.code == instance.status
            TvOptionRow(
                label = stringResource(choice.labelRes()),
                selected = current,
                onSelect = { if (!current) onSelect(instance, choice) },
                modifier = if (index == 0) Modifier.focusRequester(entryFocus) else Modifier,
            )
        }
        TvActionSheetStepFocus(entryFocus)
    }
}

private fun MediaInstance.labelRes(): Int = if (is4k) R.string.settings_service_4k else R.string.media_instance_standard

/**
 * Reporting an issue with the title: the kind, then the message, then Send. It scrolls, since the kinds and the field
 * outgrow the panel on a short screen. Once the server has it, the sheet says so and Done closes it.
 */
@Composable
private fun ColumnScope.TvReportIssueStep(
    report: IssueReport,
    draft: TvReportDraft,
    actions: TvRequestDetailActions,
    onDone: () -> Unit,
    entryFocus: FocusRequester,
) {
    TvActionSheetTitle(stringResource(R.string.issue_report_title))
    if (report == IssueReport.Sent) {
        // Sent: the next report starts from nothing.
        LaunchedEffect(Unit) { draft.onMessageChange("") }
        TvActionSheetBody(stringResource(R.string.issue_report_sent))
        TvActionSheetRow(label = stringResource(R.string.editor_done), onClick = onDone, modifier = Modifier.focusRequester(entryFocus))
        TvActionSheetStepFocus(entryFocus)
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
                selected = type == draft.type,
                onSelect = { draft.onTypeChange(type) },
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
            onDone = send,
        )
        if (report is IssueReport.Failed) TvFormNote(stringResource(R.string.issue_report_failed), tone = TvFormNoteTone.Error)
        TvActionSheetRow(label = stringResource(R.string.issue_report_send), onClick = send, enabled = canSend)
    }
    TvActionSheetStepFocus(entryFocus)
}
