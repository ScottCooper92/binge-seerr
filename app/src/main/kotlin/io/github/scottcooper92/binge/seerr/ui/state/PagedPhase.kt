package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import io.github.scottcooper92.binge.seerr.data.ListRefresh

/** What a paged list shows. The phone and the TV both read it from [pagedPhase], so they agree. */
internal sealed interface PagedPhase {
    /** Nothing to show yet, and something is on its way. */
    data object Skeleton : PagedPhase

    /** The list has no rows. */
    data object Empty : PagedPhase

    /** Rows are on screen; [refreshing] and [refreshError] are what the refresh behind them is doing. */
    data class Rows(
        val refreshing: Boolean,
        val refreshError: Throwable?,
    ) : PagedPhase

    /** The first load failed and there is nothing cached to show instead. */
    data class Failed(
        val error: Throwable,
    ) : PagedPhase
}

/**
 * The phase for one frame of a paged list.
 *
 * A list with no rows is empty only when nothing is loading on either side and [cacheBehind] is false.
 * [cacheBehind] is the one thing the pager cannot report: a network refresh wrote rows that the cache
 * has not shown yet. Without it, the gap between the two reads as an empty list.
 *
 * The source's own refresh counts as loading. The pager's combined `refresh` does not: it follows the
 * network side, so it reads as finished while the cache is still reading.
 */
internal fun pagedPhase(
    loadState: CombinedLoadStates,
    itemCount: Int,
    cacheBehind: Boolean,
): PagedPhase {
    val source = loadState.source.refresh
    val remote = loadState.mediator?.refresh
    val error = (remote as? LoadState.Error ?: source as? LoadState.Error)?.error
    return when {
        itemCount > 0 -> PagedPhase.Rows(refreshing = (remote ?: source) is LoadState.Loading, refreshError = error)
        error != null -> PagedPhase.Failed(error)
        remote is LoadState.Loading || source is LoadState.Loading -> PagedPhase.Skeleton
        cacheBehind -> PagedPhase.Skeleton
        else -> PagedPhase.Empty
    }
}

/**
 * Follows one list from frame to frame to work out [pagedPhase]'s `cacheBehind`.
 *
 * A list backed by a network refresh (one with a mediator) is behind its cache until a refresh has
 * finished. After that it is behind only if the refresh wrote rows and none have been on screen since.
 * The second half is history, and it matters: once the rows have shown, an empty list is real. The
 * user may have resolved or deleted the last row.
 *
 * A list read straight from a paging source has no mediator and is never behind.
 */
internal class PagedPhaseTracker {
    private var refresh: ListRefresh? = null
    private var shownSinceRefresh = false

    fun phase(
        loadState: CombinedLoadStates,
        itemCount: Int,
        lastRefresh: ListRefresh?,
    ): PagedPhase {
        if (lastRefresh != refresh) {
            refresh = lastRefresh
            shownSinceRefresh = false
        }
        if (itemCount > 0) shownSinceRefresh = true
        val cacheBehind =
            loadState.mediator != null &&
                (lastRefresh == null || lastRefresh.rowsWritten > 0 && !shownSinceRefresh)
        return pagedPhase(loadState, itemCount, cacheBehind)
    }
}

/**
 * The phase of the list on screen. [lastRefresh] is the latest finished refresh of this list, from its
 * view model; a list without a mediator passes null.
 */
@Composable
internal fun LazyPagingItems<*>.rememberPagedPhase(lastRefresh: ListRefresh?): PagedPhase {
    val tracker = remember(this) { PagedPhaseTracker() }
    return tracker.phase(loadState, itemCount, lastRefresh)
}
