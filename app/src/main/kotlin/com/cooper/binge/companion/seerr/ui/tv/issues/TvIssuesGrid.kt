package com.cooper.binge.companion.seerr.ui.tv.issues

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.issues.IssueCounts
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.emptyMessageRes
import com.cooper.binge.companion.seerr.ui.issues.labelRes
import com.cooper.binge.companion.seerr.ui.tv.TvBackdropArtwork
import com.cooper.binge.companion.seerr.ui.tv.TvPagedGridScreen
import com.cooper.binge.companion.seerr.ui.tv.TvPagedRows
import com.cooper.binge.companion.seerr.ui.tv.TvPosterCard
import com.cooper.binge.companion.seerr.ui.requests.labelRes as mediaTypeLabelRes

/**
 * Every issue behind one filter's row, as a paged grid. OK on a card does what it does on the row: it opens the issue's
 * sheet, so an issue past a row's cap is as manageable as one on the row.
 */
@Composable
internal fun TvIssuesGrid(
    filter: IssueFilter,
    counts: IssueCounts?,
    rows: TvPagedRows<IssueItem>,
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
            enabled = true,
            onClick = { onOpenDetail(item) },
            modifier = cellModifier,
        )
    }
}
