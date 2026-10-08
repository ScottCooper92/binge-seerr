package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.binge.designsystem.template.PagedPhase
import io.github.scottcooper92.binge.seerr.data.ListRefresh
import io.github.scottcooper92.binge.seerr.ui.state.rememberPagedPhase

/**
 * The pager's count, accessor and load states, in the form the boards take; still loading while there is no
 * pager. The refresh is the phone's [rememberPagedPhase], so the two never disagree on what an empty list is.
 */
@Composable
internal fun <T : Any> LazyPagingItems<T>?.toRows(
    lastRefresh: ListRefresh?,
    keyOf: (T) -> Any,
): TvPagedRows<T> {
    if (this == null) return TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Loading)
    return TvPagedRows(
        count = itemCount,
        at = { index -> this[index] },
        itemKey = itemKey(keyOf),
        refresh = rememberPagedPhase(lastRefresh).tvRefresh(),
        append = loadState.appendPhase(),
    )
}

/**
 * A [PagedPhase] as the boards' refresh. A board shows rows whenever there are any, so an idle refresh
 * with none is the empty plate and a loading one is the loading plate.
 */
internal fun PagedPhase.tvRefresh(): TvLoadPhase =
    when (this) {
        PagedPhase.Skeleton -> TvLoadPhase.Loading
        PagedPhase.Empty -> TvLoadPhase.Idle
        is PagedPhase.Rows ->
            when {
                refreshError != null -> TvLoadPhase.Failed
                refreshing -> TvLoadPhase.Loading
                else -> TvLoadPhase.Idle
            }
        is PagedPhase.Failed -> TvLoadPhase.Failed
    }

internal fun CombinedLoadStates.appendPhase(): TvLoadPhase = settle(mediator?.append, append).toPhase()

/** An error from either side wins, then a load in progress. */
private fun settle(
    remote: LoadState?,
    source: LoadState,
): LoadState {
    val states = listOfNotNull(remote, source)
    return states.firstOrNull { it is LoadState.Error } ?: states.firstOrNull { it is LoadState.Loading } ?: source
}

private fun LoadState.toPhase(): TvLoadPhase =
    when (this) {
        is LoadState.Loading -> TvLoadPhase.Loading
        is LoadState.Error -> TvLoadPhase.Failed
        is LoadState.NotLoading -> TvLoadPhase.Idle
    }
