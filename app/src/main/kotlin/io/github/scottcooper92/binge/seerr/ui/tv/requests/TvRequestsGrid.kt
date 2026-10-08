package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.RequestCounts
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.emptyMessageRes
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.tv.TvBackdropArtwork
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedGridScreen
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.TvPosterCard

/** Every request behind one filter's row, as a paged grid; OK on a card opens its detail page. */
@Composable
internal fun TvRequestsGrid(
    filter: RequestFilter,
    counts: RequestCounts?,
    rows: TvPagedRows<RequestItem>,
    actingIds: Set<Int>,
    detailOpen: Boolean,
    onOpenDetail: (RequestItem) -> Unit,
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
        copy = { item -> TvRequestCopy(item, now) },
    ) { item, isFocused, onFocusChanged, cellModifier ->
        TvPosterCard(
            title = item.title ?: stringResource(item.mediaType.labelRes()),
            posterUrl = item.posterUrl,
            isFocused = isFocused,
            onFocusChanged = onFocusChanged,
            enabled = item.id !in actingIds,
            onClick = { onOpenDetail(item) },
            modifier = cellModifier,
        )
    }
}
