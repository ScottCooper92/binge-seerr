package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.component.TvConfirmDialog
import com.binge.designsystem.tv.component.TvOptionRow
import com.binge.designsystem.tv.component.TvSaveStatus
import com.binge.designsystem.tv.component.TvSideSheet
import com.binge.designsystem.tv.component.TvSideSheetBody
import com.binge.designsystem.tv.component.TvSideSheetRow
import com.binge.designsystem.tv.component.TvSideSheetRows
import com.binge.designsystem.tv.component.TvSideSheetStepFocus
import com.binge.designsystem.tv.component.TvSideSheetTitle
import com.binge.designsystem.tv.focus.TvOverlayCloser
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.requests.EditState
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.MediaStatusChoice
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.binge.companion.seerr.ui.requests.icon
import com.cooper.binge.companion.seerr.ui.requests.labelRes
import com.cooper.binge.companion.seerr.ui.tv.OnSaved
import com.cooper.binge.companion.seerr.ui.tv.ScopedTo
import com.cooper.binge.companion.seerr.ui.tv.TvActionRun
import com.cooper.binge.companion.seerr.ui.tv.TvPhoneCode

/**
 * What the page opens over itself: the two confirm dialogs, and the sheets for editing the request, marking the title's
 * state and reporting an issue on the phone.
 */
internal enum class DetailSheet { Remove, Block, Edit, MarkAs, Report }

/** What the sheets read from the page: the request, its title's record, the open editor, and the title's page on the server. */
internal class TvDetailSheetContent(
    val item: RequestItem,
    val media: MediaRecord?,
    val edit: EditState?,
    val webUrl: String,
    val serverName: String,
)

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
    run: TvActionRun,
    isActing: Boolean = false,
) {
    val closer = host.closer
    val media = content.media
    // An edit closed unsaved is dropped, as the phone's sheet drops it.
    val dismiss = {
        if (host.step == DetailSheet.Edit) actions.edit?.controls?.cancel()
        closer.close()
    }
    // The two confirms are a question and nothing else, so each is a dialog; the rest carry a list, a form or a code, so
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
                        onSelect = { instance, choice -> actions.onSetMediaStatus(it.mediaId, choice, instance.is4k) },
                        entryFocus = entryFocus,
                        status = run.status,
                        onRetry = run::again,
                        actionInFlight = isActing,
                    )
                }
                // The sheet stays open while it saves: "Saved" is read, and then it closes.
                run.OnSaved { closer.close() }
                // Another action's failure, left on the page, is not this sheet's to show or retry.
                run.ScopedTo(DetailSheet.MarkAs)
            }
        DetailSheet.Report ->
            TvSideSheet(onDismissRequest = dismiss) { entryFocus ->
                TvReportOnPhoneStep(content.webUrl, content.serverName, entryFocus)
            }
    }
}

/**
 * Marking the title's state, as the phone's Mark as sheet does: which instance first where the server holds the
 * title twice, then the states, the current one ticked. A pick applies in place: the tick moves to it at once, [status]
 * says "Saving…" and then "Saved" above the options, and the sheet closes after. On a failure the tick moves back and the
 * line offers Try again, which applies the same pick once more. While [actionInFlight] the page drops a pick, so the
 * rows ignore it and the tick stays where it was.
 */
@Composable
internal fun ColumnScope.TvMarkAsSteps(
    media: MediaRecord,
    onSelect: (MediaInstance, MediaStatusChoice) -> Unit,
    entryFocus: FocusRequester,
    status: SaveStatus = SaveStatus.Idle,
    onRetry: () -> Unit = {},
    actionInFlight: Boolean = false,
) {
    var picked by rememberSaveable { mutableStateOf(media.instances.singleOrNull()?.is4k) }
    val instance = media.instances.firstOrNull { it.is4k == picked }
    // The pick being saved, ticked ahead of the server's answer; a failure puts the tick back where it was.
    var pending by remember { mutableStateOf<MediaStatusChoice?>(null) }
    var failedPick by remember { mutableStateOf<MediaStatusChoice?>(null) }
    LaunchedEffect(status) {
        if (status == SaveStatus.Failed) {
            failedPick = pending
            pending = null
        }
    }
    TvSideSheetTitle(stringResource(R.string.media_mark_as))
    if (instance == null) {
        TvSideSheetRows {
            media.instances.forEachIndexed { index, candidate ->
                TvSideSheetRow(
                    label = stringResource(candidate.labelRes()),
                    icon = candidate.icon(media.isTv),
                    onClick = { picked = candidate.is4k },
                    modifier = if (index == 0) Modifier.focusRequester(entryFocus) else Modifier,
                )
            }
        }
        TvSideSheetStepFocus(entryFocus)
    } else {
        TvSideSheetBody(stringResource(instance.labelRes()) + "\n" + stringResource(R.string.media_mark_as_scope_caption))
        // Above the options, not under them: five rows fill a television's height, and the line must never sit off-screen.
        TvSaveStatus(
            status = status,
            onRetry = {
                pending = failedPick
                onRetry()
            },
        )
        TvSideSheetRows {
            MediaStatusChoice.entries.forEachIndexed { index, choice ->
                val current = choice.code == instance.status
                TvOptionRow(
                    label = stringResource(choice.labelRes()),
                    icon = choice.icon(),
                    selected = pending?.let { it == choice } ?: current,
                    onClick = {
                        if (!current && status != SaveStatus.Saving && !actionInFlight) {
                            pending = choice
                            onSelect(instance, choice)
                        }
                    },
                    modifier = if (index == 0) Modifier.focusRequester(entryFocus) else Modifier,
                )
            }
        }
        TvSideSheetStepFocus(entryFocus)
    }
}

/**
 * Reporting an issue needs a keyboard, so the sheet hands it to the phone: the title's page on the server, as a code, where
 * the report is written.
 */
@Composable
internal fun ColumnScope.TvReportOnPhoneStep(
    webUrl: String,
    serverName: String,
    entryFocus: FocusRequester,
) {
    TvSideSheetTitle(stringResource(R.string.issue_report_title))
    TvPhoneCode(
        url = webUrl,
        body = stringResource(R.string.tv_detail_report_on_phone, serverName),
        modifier = Modifier.focusRequester(entryFocus),
    )
    TvSideSheetStepFocus(entryFocus)
}
