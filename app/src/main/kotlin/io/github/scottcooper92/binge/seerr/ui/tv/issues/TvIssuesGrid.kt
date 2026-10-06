package io.github.scottcooper92.binge.seerr.ui.tv.issues

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.issues.IssueCounts
import io.github.scottcooper92.binge.seerr.ui.issues.IssueFilter
import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.issues.emptyMessageRes
import io.github.scottcooper92.binge.seerr.ui.issues.labelRes
import io.github.scottcooper92.binge.seerr.ui.tv.TvBackdropArtwork
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedGridScreen
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.TvPosterCard
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes as mediaTypeLabelRes

/**
 * Every issue behind one filter's row, as a paged grid. OK on a card does what it does on the row: the actions
 * sheet is the board's, so a card this viewer may act on opens its read-only page here too, where the
 * management lives.
 */
@Composable
internal fun TvIssuesGrid(
    filter: IssueFilter,
    counts: IssueCounts?,
    rows: TvPagedRows<IssueItem>,
    actingIds: Set<Int>,
    detailOpen: Boolean,
    onOpenDetail: (IssueItem) -> Unit,
    onRetryLoad: () -> Unit,
    onReconnect: () -> Unit,
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
        onReconnect = onReconnect,
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
            onClick = { onOpenDetail(item) },
            modifier = cellModifier,
        )
    }
}
