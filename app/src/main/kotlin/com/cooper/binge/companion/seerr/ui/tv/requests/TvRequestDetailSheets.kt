package com.cooper.binge.companion.seerr.ui.tv.requests

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.cooper.binge.companion.seerr.handoff.TitleReportLinks
import com.cooper.binge.companion.seerr.ui.requests.EditState
import com.cooper.binge.companion.seerr.ui.requests.MediaInstance
import com.cooper.binge.companion.seerr.ui.requests.MediaRecord
import com.cooper.binge.companion.seerr.ui.requests.MediaStatusChoice
import com.cooper.binge.companion.seerr.ui.requests.RequestItem
import com.cooper.binge.companion.seerr.ui.requests.icon
import com.cooper.binge.companion.seerr.ui.requests.labelRes
import com.cooper.binge.companion.seerr.ui.tv.ScopedTo
import com.cooper.binge.companion.seerr.ui.tv.TvActionRun
import com.cooper.binge.companion.seerr.ui.tv.TvPhoneCode

/**
 * What the page opens over itself: the two confirm dialogs, and the pages for editing the request, marking the title's
 * state and reporting an issue on the phone. A page opens as a sheet of its own from the action row, and inside the Manage
 * stack from Manage, where Back returns to the row that opened it.
 */
internal enum class DetailSheet(
    val isPage: Boolean,
) {
    Remove(isPage = false),
    Block(isPage = false),
    Edit(isPage = true),
    MarkAs(isPage = true),
    Report(isPage = true),
}

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

/** What a page needs from the screen, wherever it opens: the actions, what it shows, the page's run and whether it acts. */
internal class TvDetailPageHost(
    val actions: TvRequestDetailActions,
    val content: TvDetailSheetContent,
    val run: TvActionRun,
    val isActing: Boolean,
    /** Tells the screen whether Mark as is showing, so the action row leaves its status to the page. */
    val onMarkAsShown: (Boolean) -> Unit,
)

/** The sheet [host] names, opened from the action row. Each closes through its own closer, so focus returns to its button. */
@Composable
internal fun TvRequestDetailSheet(
    host: TvDetailSheetHost,
    page: TvDetailPageHost,
) {
    val closer = host.closer
    // The two confirms are a question and nothing else, so each is a dialog; the rest carry a list, a form or a code, so
    // they are pages of a sheet.
    if (host.step.isPage) {
        TvSideSheet(onDismissRequest = closer::close) { entryFocus ->
            TvRequestDetailPage(host.step, page, entryFocus, onEditClosed = closer::close)
        }
    } else {
        TvRequestConfirm(host.step, page.actions, onDone = { closer.close() })
    }
}

/** Remove's or Block's confirm. [onDone] closes it, and says whether it was answered: the action has run if so. */
@Composable
internal fun TvRequestConfirm(
    step: DetailSheet,
    actions: TvRequestDetailActions,
    onDone: (confirmed: Boolean) -> Unit,
) {
    val remove = step == DetailSheet.Remove
    TvConfirmDialog(
        title = stringResource(if (remove) R.string.request_remove_confirm_title else R.string.request_block_confirm_title),
        message = stringResource(if (remove) R.string.request_remove_confirm_message else R.string.request_block_confirm_message),
        confirmLabel = stringResource(if (remove) R.string.request_remove else R.string.tv_detail_block),
        onConfirm = {
            if (remove) actions.onRemove(false) else actions.onBlock()
            onDone(true)
        },
        onDismissRequest = { onDone(false) },
        destructive = true,
    )
}

/**
 * One of the pages, [step]: Edit, Mark as or Report. Each brings its own arrival focus through [entryFocus]. [onEditClosed]
 * runs once a saved edit has closed the editor. An edit the page leaves unsaved is dropped, as the phone's sheet drops it;
 * a configuration change keeps it, since the page comes back.
 */
@Composable
internal fun ColumnScope.TvRequestDetailPage(
    step: DetailSheet,
    host: TvDetailPageHost,
    entryFocus: FocusRequester,
    onEditClosed: () -> Unit,
) {
    val content = host.content
    when (step) {
        DetailSheet.Edit ->
            host.actions.edit?.let { edit ->
                val activity = LocalActivity.current
                DisposableEffect(edit.controls) {
                    onDispose { if (activity?.isChangingConfigurations != true) edit.controls.cancel() }
                }
                TvEditRequestSheet(content.item, content.edit, edit.controls, onClosed = onEditClosed, entryFocus = entryFocus)
            }
        DetailSheet.MarkAs -> {
            content.media?.let {
                TvMarkAsSteps(
                    media = it,
                    onSelect = { instance, choice -> host.actions.onSetMediaStatus(it.mediaId, choice, instance.is4k) },
                    entryFocus = entryFocus,
                    status = host.run.status,
                    onRetry = host.run::again,
                    actionInFlight = host.isActing,
                )
            }
            // The page stays open once it has saved: the tick and "Saved" are the answer, read in place. Another action's
            // failure, left on the page, is not this page's to show or retry.
            host.run.ScopedTo(DetailSheet.MarkAs)
            val shown by rememberUpdatedState(host.onMarkAsShown)
            DisposableEffect(Unit) {
                shown(true)
                onDispose { shown(false) }
            }
        }
        DetailSheet.Report -> TvReportOnPhoneStep(TitleReportLinks.write(content.webUrl, content.item.is4k), content.serverName, entryFocus)
        DetailSheet.Remove, DetailSheet.Block -> Unit
    }
}

/**
 * Marking the title's state, as the phone's Mark as sheet does: which instance first where the server holds the
 * title twice, then the states, the current one ticked. A pick applies in place: the row picked shows a spinner in the
 * tick's place while it saves, and the tick moves to it once saved. The page stays open. On a failure the tick stays where
 * it was, and a line above the options offers Try again, which applies the same pick once more. While [actionInFlight]
 * the page drops a pick, so the rows ignore it and the tick stays where it was.
 */
@Composable
internal fun ColumnScope.TvMarkAsSteps(
    media: MediaRecord,
    onSelect: (MediaInstance, MediaStatusChoice) -> Unit,
    entryFocus: FocusRequester,
    status: SaveStatus = SaveStatus.Idle,
    onRetry: () -> Unit = {},
    actionInFlight: Boolean = false,
    /** The pick already being saved on arrival, for a frame, which cannot press a row. Production passes null. */
    initialPick: MediaStatusChoice? = null,
) {
    var picked by rememberSaveable { mutableStateOf(media.instances.singleOrNull()?.is4k) }
    val instance = media.instances.firstOrNull { it.is4k == picked }
    // The pick being saved: its row spins until the server answers, then takes the tick. A failure leaves the tick where it was.
    var pending by remember { mutableStateOf(initialPick) }
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
                    opensPage = true,
                    modifier = if (index == 0) Modifier.focusRequester(entryFocus) else Modifier,
                )
            }
        }
        TvSideSheetStepFocus(entryFocus)
    } else {
        TvSideSheetBody(stringResource(instance.labelRes()) + "\n" + stringResource(R.string.media_mark_as_scope_caption))
        // Saving and saved show in the row picked. A failure says so here, with its Retry: above the options, not under them,
        // since five rows fill a television's height and the line must never sit off-screen.
        TvSaveStatus(
            status = if (status == SaveStatus.Failed) status else SaveStatus.Idle,
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
                    selected = pending?.takeIf { status == SaveStatus.Saved }?.let { it == choice } ?: current,
                    saving = status == SaveStatus.Saving && pending == choice,
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
