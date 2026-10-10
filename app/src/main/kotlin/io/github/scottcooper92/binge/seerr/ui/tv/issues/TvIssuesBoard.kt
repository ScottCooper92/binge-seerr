package io.github.scottcooper92.binge.seerr.ui.tv.issues

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
import com.binge.designsystem.tv.nav.tvContentGutterStart
import com.binge.designsystem.tv.template.TvHubRow
import com.binge.designsystem.tv.template.TvImmersiveHub
import com.binge.designsystem.tv.template.TvMessagePage
import com.binge.designsystem.tv.template.TvPageAction
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.issues.IssueCounts
import io.github.scottcooper92.binge.seerr.ui.issues.IssueFilter
import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.issues.IssueListEvent
import io.github.scottcooper92.binge.seerr.ui.issues.IssueStatus
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesUiState
import io.github.scottcooper92.binge.seerr.ui.issues.emptyMessageRes
import io.github.scottcooper92.binge.seerr.ui.issues.issueAffectedLabel
import io.github.scottcooper92.binge.seerr.ui.issues.labelRes
import io.github.scottcooper92.binge.seerr.ui.issues.tone
import io.github.scottcooper92.binge.seerr.ui.tv.OverlayFocusRestore
import io.github.scottcooper92.binge.seerr.ui.tv.TV_ROW_ITEM_CAP
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheet
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetBody
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetConfirm
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetRow
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetStepFocus
import io.github.scottcooper92.binge.seerr.ui.tv.TvActionSheetTitle
import io.github.scottcooper92.binge.seerr.ui.tv.TvBackdropArtwork
import io.github.scottcooper92.binge.seerr.ui.tv.TvBackdropCopy
import io.github.scottcooper92.binge.seerr.ui.tv.TvFormNote
import io.github.scottcooper92.binge.seerr.ui.tv.TvFormNoteTone
import io.github.scottcooper92.binge.seerr.ui.tv.TvHubLoading
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.TvPosterCard
import io.github.scottcooper92.binge.seerr.ui.tv.TvRowsFallback
import io.github.scottcooper92.binge.seerr.ui.tv.rememberOverlayFocusRestore
import io.github.scottcooper92.binge.seerr.ui.tv.rememberTvTransientEvent
import io.github.scottcooper92.binge.seerr.ui.tv.tvColor
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.tv.R as TvR
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes as mediaTypeLabelRes

/** Everything the issues board can ask of its ViewModel, in one place so the entry stays a wiring. */
internal class TvIssuesActions(
    val onOpenActions: (IssueItem) -> Unit,
    val onDismissActions: () -> Unit,
    val onOpenDetail: (IssueItem) -> Unit,
    val onResolve: (IssueItem) -> Unit,
    val onReopen: (IssueItem) -> Unit,
    val onDelete: (IssueItem) -> Unit,
    val onSeeAll: (IssueFilter) -> Unit,
    val onRetryLoad: () -> Unit,
    val onRetryScope: () -> Unit,
)

/**
 * The issues browser as a television board: the filters as a band, the selected filter's rows beneath,
 * and a row's actions — resolve or reopen, and delete — on the end-edge sheet. Every row also opens the
 * read-only issue page (an overlay above the rail, not owned by this board) — from the sheet for a row
 * this viewer may act on, where resolving, reopening and deleting stay; directly for one they may not,
 * where the sheet has nothing else to offer.
 *
 * [openIssueId] is the id of the issue whose detail page is currently showing, or null once it has
 * closed — on that transition the board offers focus back to the row that opened it, one frame after
 * the page's own disposal, the same round trip the requests board runs for its detail page.
 */
@Composable
internal fun TvIssuesBoard(
    state: IssuesUiState,
    rowsFor: (IssueFilter) -> TvPagedRows<IssueItem>,
    events: Flow<IssueListEvent>,
    actions: TvIssuesActions,
    modifier: Modifier = Modifier,
    openIssueId: Int? = null,
    seeAllOpen: Boolean = false,
    now: Long = System.currentTimeMillis(),
) {
    val ready = state as? IssuesUiState.Ready
    val event = rememberTvTransientEvent(events)
    val restore = rememberOverlayFocusRestore(overlayOpen = openIssueId != null || seeAllOpen)
    val closer = rememberTvOverlayCloser(restoreTo = restore.requester, onClose = actions.onDismissActions)
    Box(modifier = modifier.fillMaxSize()) {
        if (ready == null) {
            TvIssuesUnresolved(state, actions)
        } else {
            TvIssuesRows(
                ready = ready,
                rowsFor = rowsFor,
                actions = actions,
                restore = restore,
                onSeeAll = { filter ->
                    restore.leavingFromSeeAll(filter.name)
                    actions.onSeeAll(filter)
                },
                onSelect = { item ->
                    restore.leavingFromRow(item.id)
                    if (item.canBeActedOn(ready.scope)) actions.onOpenActions(item) else actions.onOpenDetail(item)
                },
                now = now,
            )
        }
        event?.let {
            TvFormNote(
                text = stringResource(it.messageRes()),
                tone = if (it is IssueListEvent.Failed) TvFormNoteTone.Error else TvFormNoteTone.Success,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = tvContentGutterStart(), bottom = dimensionResource(TvR.dimen.tv_overscan_vertical)),
            )
        }
        // While the grid is open it renders the sheet itself, on this same ViewModel; one sheet, not two.
        ready?.actionItem?.takeUnless { seeAllOpen }?.let { item ->
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
                // Not `closer.close()`: that would request focus back onto this (now hidden) row one frame
                // after the sheet disposes, racing the detail page's own arrival focus for the same beat.
                // The board's `openIssueId` effect above is the one restore this path needs, once the page
                // the viewer is going to actually closes.
                onOpenDetail = {
                    actions.onDismissActions()
                    actions.onOpenDetail(item)
                },
                onDismiss = closer::close,
            )
        }
    }
}

/** What the board shows before the issues are known: the loading page, or that they could not be read, with a retry. */
@Composable
private fun TvIssuesUnresolved(
    state: IssuesUiState,
    actions: TvIssuesActions,
) {
    if (state !is IssuesUiState.Error) {
        TvHubLoading()
        return
    }
    TvMessagePage(
        body = stringResource(R.string.tv_list_load_failed),
        icon = Icons.Filled.Warning,
        primary = TvPageAction(stringResource(R.string.hub_retry), actions.onRetryScope),
    )
}

/** The filters that get a row; "All" would only repeat the others. */
internal val IssueRowFilters = IssueFilter.entries.filter { it != IssueFilter.All }

/** The issues as the design system's immersive hub: a row of posters per filter over a backdrop describing the focused issue. */
@Composable
private fun TvIssuesRows(
    ready: IssuesUiState.Ready,
    rowsFor: (IssueFilter) -> TvPagedRows<IssueItem>,
    actions: TvIssuesActions,
    restore: OverlayFocusRestore,
    onSeeAll: (IssueFilter) -> Unit,
    onSelect: (IssueItem) -> Unit,
    now: Long,
) {
    val perFilter = IssueRowFilters.map { it to rowsFor(it) }
    if (perFilter.none { (_, rows) -> rows.count > 0 }) {
        TvRowsFallback(
            rows = perFilter.map { it.second },
            emptyBody = stringResource(IssueFilter.All.emptyMessageRes()),
            onRetryLoad = actions.onRetryLoad,
        )
        return
    }
    val hubRows =
        perFilter.map { (filter, rows) ->
            TvHubRow(
                key = filter.name,
                title = filterLabel(filter, ready.counts),
                items = (0 until minOf(rows.count, TV_ROW_ITEM_CAP)).mapNotNull { rows.at(it) },
                onSeeAll = { onSeeAll(filter) }.takeIf { (ready.counts?.countFor(filter) ?: rows.count) > TV_ROW_ITEM_CAP },
            )
        }
    TvImmersiveHub(
        rows = hubRows,
        itemId = { it.id },
        cardWidth = dimensionResource(TvR.dimen.tv_immersive_card_width),
        onItemClick = onSelect,
        seeAllLabel = stringResource(R.string.tv_see_all),
        seeAllModifier = restore::seeAllModifier,
        artwork = { item -> TvBackdropArtwork(item.backdropUrl, item.posterUrl) },
        copy = { item -> TvIssueCopy(item, now) },
    ) { item, isFocused, onFocusChanged, onClick, cellModifier ->
        TvPosterCard(
            title = item.title ?: stringResource(item.mediaType.mediaTypeLabelRes()),
            posterUrl = item.posterUrl,
            isFocused = isFocused,
            onFocusChanged = onFocusChanged,
            enabled = item.id !in ready.actingIds,
            onClick = onClick,
            modifier = restore.rowModifier(item.id, cellModifier),
        )
    }
}

/** What the backdrop says about the focused issue: the title, what is wrong and where, who filed it, and the opening line. */
@Composable
internal fun ColumnScope.TvIssueCopy(
    item: IssueItem,
    now: Long,
) {
    val separator = stringResource(R.string.hub_meta_separator)
    TvBackdropCopy(
        meta =
            listOfNotNull(
                stringResource(item.mediaType.mediaTypeLabelRes()),
                item.year,
                item.certification,
            ).joinToString(separator),
        title = item.title ?: stringResource(item.mediaType.mediaTypeLabelRes()),
        status =
            listOfNotNull(
                stringResource(item.status.labelRes()),
                stringResource(item.type.labelRes()),
                issueAffectedLabel(item),
                item.reportedBy ?: stringResource(R.string.requests_requester_unknown),
                item.commentCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.issue_comments, it, it) },
                formatRelativeOrAbsolute(item.createdAtMillis, now),
            ).joinToString(separator),
        statusColor = item.status.tone().tvColor(),
        // What the reporter wrote is the point of an issue; the title's synopsis only fills in when there is none.
        synopsis = item.problem ?: item.overview,
    )
}

/** The actions that take a second step before they land. */
private enum class Pending { Resolve, Reopen, Delete }

/**
 * An issue's actions on the end-edge sheet, for a row this viewer may act on: close or reopen it, and
 * delete it, each confirmed, then read its comment thread. Entry focus stays on the first management
 * row exactly as before — Read comments is appended last, an addition rather than a reordering.
 */
@Composable
internal fun TvIssueActionsSheet(
    item: IssueItem,
    onResolve: () -> Unit,
    onReopen: () -> Unit,
    onDelete: () -> Unit,
    onOpenDetail: () -> Unit,
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
                TvActionSheetRow(label = stringResource(R.string.tv_issue_read_comments), onClick = onOpenDetail)
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
