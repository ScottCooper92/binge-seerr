package io.github.scottcooper92.binge.seerr.ui.tv.issues

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.issues.IssueCounts
import io.github.scottcooper92.binge.seerr.ui.issues.IssueFilter
import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.issues.IssueListScope
import io.github.scottcooper92.binge.seerr.ui.issues.canBeDeleted
import io.github.scottcooper92.binge.seerr.ui.issues.emptyMessageRes
import io.github.scottcooper92.binge.seerr.ui.issues.labelRes
import io.github.scottcooper92.binge.seerr.ui.tv.TvBackdropArtwork
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedGridScreen
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.TvPosterCard
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes as mediaTypeLabelRes

/** What the grid needs for a card's actions sheet: who is asking, which card it is open for, and the ViewModel's verbs. */
internal class TvIssuesGridManagement(
    val scope: IssueListScope?,
    val actionItem: IssueItem?,
    val onOpenActions: (IssueItem) -> Unit,
    val onDismissActions: () -> Unit,
    val onResolve: (IssueItem) -> Unit,
    val onReopen: (IssueItem) -> Unit,
    val onDelete: (IssueItem) -> Unit,
)

/**
 * Every issue behind one filter's row, as a paged grid. OK on a card does what it does on the row: a card
 * this viewer may act on opens the actions sheet over the grid, [management] holding the one it is for, and
 * any other opens the read-only page. So an issue past a row's cap is as manageable as one on the row.
 */
@Composable
internal fun TvIssuesGrid(
    filter: IssueFilter,
    counts: IssueCounts?,
    rows: TvPagedRows<IssueItem>,
    actingIds: Set<Int>,
    management: TvIssuesGridManagement,
    detailOpen: Boolean,
    onOpenDetail: (IssueItem) -> Unit,
    onRetryLoad: () -> Unit,
    onBack: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    val label = stringResource(filter.labelRes())
    val count = counts?.countFor(filter)
    TvPagedGridScreen(
        heading = if (count != null) stringResource(R.string.tv_filter_with_count, label, count) else label,
        rows = rows,
        emptyBody = stringResource(filter.emptyMessageRes()),
        onRetryLoad = onRetryLoad,
        onBack = onBack,
        detailOpen = detailOpen,
        artwork = { item -> TvBackdropArtwork(item.backdropUrl, item.posterUrl) },
        copy = { item -> TvIssueCopy(item, now) },
    ) { item, isFocused, onFocusChanged, cellModifier ->
        TvPosterCard(
            title = item.title ?: stringResource(item.mediaType.mediaTypeLabelRes()),
            posterUrl = item.posterUrl,
            isFocused = isFocused,
            onFocusChanged = onFocusChanged,
            enabled = item.id !in actingIds,
            onClick = {
                if (management.scope?.let(item::canBeActedOn) == true) management.onOpenActions(item) else onOpenDetail(item)
            },
            modifier = cellModifier,
        )
    }
    management.actionItem?.let { item ->
        TvIssueActionsSheet(
            item = item,
            canDelete = management.scope?.let(item::canBeDeleted) == true,
            onResolve = {
                management.onResolve(item)
                management.onDismissActions()
            },
            onReopen = {
                management.onReopen(item)
                management.onDismissActions()
            },
            onDelete = {
                management.onDelete(item)
                management.onDismissActions()
            },
            onOpenDetail = {
                management.onDismissActions()
                onOpenDetail(item)
            },
            onDismiss = management.onDismissActions,
        )
    }
}
