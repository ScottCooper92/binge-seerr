package io.github.scottcooper92.binge.seerr.ui.tv.issues

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.issues.IssueCounts
import io.github.scottcooper92.binge.seerr.ui.issues.IssueFilter
import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.issues.IssueListEvent
import io.github.scottcooper92.binge.seerr.ui.issues.IssueStatus
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesUiState
import io.github.scottcooper92.binge.seerr.ui.issues.emptyMessageRes
import io.github.scottcooper92.binge.seerr.ui.issues.labelRes
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheet
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetBody
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetConfirm
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetRow
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetStepFocus
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetTitle
import io.github.scottcooper92.binge.seerr.ui.tv.TvBoardFrame
import io.github.scottcooper92.binge.seerr.ui.tv.TvBoardPlate
import io.github.scottcooper92.binge.seerr.ui.tv.TvFilterBand
import io.github.scottcooper92.binge.seerr.ui.tv.TvFormNote
import io.github.scottcooper92.binge.seerr.ui.tv.TvFormNoteTone
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedList
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.rememberTvTransientEvent
import kotlinx.coroutines.flow.Flow
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes as mediaTypeLabelRes

/** Everything the issues board can ask of its ViewModel, in one place so the entry stays a wiring. */
internal class TvIssuesActions(
    val onFilterChange: (IssueFilter) -> Unit,
    val onOpenActions: (IssueItem) -> Unit,
    val onDismissActions: () -> Unit,
    val onResolve: (IssueItem) -> Unit,
    val onReopen: (IssueItem) -> Unit,
    val onDelete: (IssueItem) -> Unit,
    val onRetryLoad: () -> Unit,
    val onReconnect: () -> Unit,
)

/**
 * The issues browser as a television board: the filters as a band, the selected filter's rows beneath,
 * and a row's actions — resolve or reopen, and delete — on the end-edge sheet. Same shape as the requests
 * board; the comment thread stays on the phone for now.
 */
@Composable
internal fun TvIssuesBoard(
    state: IssuesUiState,
    rows: TvPagedRows<IssueItem>,
    events: Flow<IssueListEvent>,
    actions: TvIssuesActions,
    modifier: Modifier = Modifier,
    initialFocusedRowId: Int? = null,
) {
    val ready = state as? IssuesUiState.Ready
    val event = rememberTvTransientEvent(events)
    val restoreFocus = remember { FocusRequester() }
    // Pinned to the row that opened the sheet, not the open action item: by the time the closer requests
    // the return the item is already null, and the requester must still be attached somewhere.
    var restoreRowId by rememberSaveable { mutableStateOf<Int?>(null) }
    val closer = rememberTvOverlayCloser(restoreTo = restoreFocus, onClose = actions.onDismissActions)
    Box(modifier = modifier.fillMaxSize()) {
        TvBoardFrame(title = stringResource(R.string.hub_section_issues)) {
            if (ready == null) {
                TvBoardPlate(body = stringResource(R.string.tv_loading), modifier = Modifier.weight(1f))
                return@TvBoardFrame
            }
            TvFilterBand(
                filters = IssueFilter.entries.map { filter -> filter to filterLabel(filter, ready.counts) },
                selected = ready.filter,
                onSelect = actions.onFilterChange,
            )
            TvPagedList(
                rows = rows,
                emptyMessage = stringResource(ready.filter.emptyMessageRes()),
                onRetryLoad = actions.onRetryLoad,
                onReconnect = actions.onReconnect,
                modifier = Modifier.weight(1f),
            ) { item ->
                TvIssueRow(
                    item = item,
                    onSelect =
                        {
                            restoreRowId = item.id
                            actions.onOpenActions(item)
                        }.takeIf { item.canBeActedOn(ready.scope) },
                    isActing = item.id in ready.actingIds,
                    initiallyFocused = item.id == initialFocusedRowId,
                    modifier = if (item.id == restoreRowId) Modifier.focusRequester(restoreFocus) else Modifier,
                )
            }
            event?.let {
                TvFormNote(
                    text = stringResource(it.messageRes()),
                    tone = if (it is IssueListEvent.Failed) TvFormNoteTone.Error else TvFormNoteTone.Success,
                )
            }
        }
        ready?.actionItem?.let { item ->
            TvIssueActionsSheet(
                item = item,
                onResolve = {
                    actions.onResolve(item)
                    closer.close()
                },
                onReopen = {
                    actions.onReopen(item)
                    closer.close()
                },
                onDelete = {
                    actions.onDelete(item)
                    closer.close()
                },
                onDismiss = closer::close,
            )
        }
    }
}

/** The actions that take a second step before they land. */
private enum class Pending { Resolve, Reopen, Delete }

/** An issue's actions on the end-edge sheet: close or reopen it, and delete it, each confirmed. */
@Composable
internal fun TvIssueActionsSheet(
    item: IssueItem,
    onResolve: () -> Unit,
    onReopen: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pending by rememberSaveable { mutableStateOf<Pending?>(null) }
    val open = item.status == IssueStatus.Open
    TvActionSheet(onDismiss = onDismiss, modifier = modifier) { entryFocus ->
        when (pending) {
            Pending.Resolve ->
                TvActionSheetConfirm(
                    title = stringResource(R.string.issue_resolve_confirm_title),
                    message = stringResource(R.string.issue_resolve_confirm_message),
                    confirmLabel = stringResource(R.string.tv_issue_resolve),
                    onConfirm = onResolve,
                    onCancel = { pending = null },
                    entryFocus = entryFocus,
                )
            Pending.Reopen ->
                TvActionSheetConfirm(
                    title = stringResource(R.string.issue_reopen_confirm_title),
                    message = stringResource(R.string.issue_reopen_confirm_message),
                    confirmLabel = stringResource(R.string.tv_issue_reopen),
                    onConfirm = onReopen,
                    onCancel = { pending = null },
                    entryFocus = entryFocus,
                )
            Pending.Delete ->
                TvActionSheetConfirm(
                    title = stringResource(R.string.issue_delete_confirm_title),
                    message = stringResource(R.string.issue_delete_confirm_message),
                    confirmLabel = stringResource(R.string.issue_delete),
                    onConfirm = onDelete,
                    onCancel = { pending = null },
                    entryFocus = entryFocus,
                )
            null -> {
                TvActionSheetTitle(item.title ?: stringResource(item.mediaType.mediaTypeLabelRes()))
                TvActionSheetBody(
                    listOfNotNull(item.reportedBy, stringResource(item.status.labelRes()))
                        .joinToString(stringResource(R.string.hub_meta_separator)),
                )
                TvActionSheetRow(
                    label = stringResource(if (open) R.string.tv_issue_resolve else R.string.tv_issue_reopen),
                    onClick = { pending = if (open) Pending.Resolve else Pending.Reopen },
                    modifier = Modifier.focusRequester(entryFocus),
                )
                TvActionSheetRow(
                    label = stringResource(R.string.issue_delete),
                    onClick = { pending = Pending.Delete },
                    destructive = true,
                )
                TvActionSheetStepFocus(entryFocus)
            }
        }
    }
}

/** The line the board shows for an action's outcome. */
internal fun IssueListEvent.messageRes(): Int =
    when (this) {
        IssueListEvent.Resolved -> R.string.issue_resolved
        IssueListEvent.Reopened -> R.string.issue_reopened
        IssueListEvent.Deleted -> R.string.tv_issue_deleted
        is IssueListEvent.Failed ->
            if (error ==
                SeerrError.Unauthorized
            ) {
                R.string.hub_unauthorized_body
            } else {
                R.string.tv_issue_action_failed
            }
    }

@Composable
private fun filterLabel(
    filter: IssueFilter,
    counts: IssueCounts?,
): String {
    val label = stringResource(filter.labelRes())
    val count = counts?.countFor(filter) ?: return label
    return stringResource(R.string.tv_filter_with_count, label, count)
}
