package io.github.scottcooper92.binge.seerr.ui.state

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import io.github.scottcooper92.binge.seerr.data.ListRefresh
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

private val loading = LoadState.Loading
private val idle = LoadState.NotLoading(endOfPaginationReached = false)
private val complete = LoadState.NotLoading(endOfPaginationReached = true)
private val failure = IOException("unreachable")

private val rows = PagedPhase.Rows(refreshing = false, refreshError = null)
private val refreshingRows = PagedPhase.Rows(refreshing = true, refreshError = null)

/** One frame of a paged list: what the pager reports, how many rows it holds, and the latest refresh the view model knows of. */
private data class Frame(
    val source: LoadState,
    val mediator: LoadState?,
    val count: Int = 0,
    val refresh: ListRefresh? = null,
    val mediatorAppend: LoadState = idle,
)

/** The pager's combined refresh is left as Paging computes it from the two sides, so no test leans on it. */
private fun Frame.states(): CombinedLoadStates {
    val combined =
        if (mediator == null ||
            mediator is LoadState.NotLoading &&
            source is LoadState.NotLoading
        ) {
            mediator ?: source
        } else {
            loading
        }
    return CombinedLoadStates(
        refresh = combined,
        prepend = idle,
        append = idle,
        source = LoadStates(refresh = source, prepend = idle, append = idle),
        mediator = mediator?.let { LoadStates(refresh = it, prepend = idle, append = mediatorAppend) },
    )
}

private fun phases(vararg frames: Frame): List<PagedPhase> {
    val tracker = PagedPhaseTracker()
    return frames.map { tracker.phase(it.states(), it.count, it.refresh) }
}

private fun wrote(rows: Int) = ListRefresh(rowsWritten = rows, sequence = rows.toLong() + 1)

/**
 * What each sequence a paged list goes through looks like on screen. The frames are the ones the real
 * pipeline produces ([PagedPhaseSequenceTest] records them), including the gap where the mediator has
 * finished and the cache has not yet handed over what it wrote.
 */
class PagedPhaseTest {
    @Test
    fun `a cold open of a server with nothing in it is the skeleton until the refresh says so, then empty`() {
        val phases =
            phases(
                Frame(source = loading, mediator = idle),
                Frame(source = loading, mediator = loading),
                Frame(source = idle, mediator = loading),
                // Finished, and the view model has not heard yet.
                Frame(source = idle, mediator = complete),
                Frame(source = idle, mediator = complete, refresh = wrote(0)),
            )

        assertEquals(listOf(PagedPhase.Skeleton, PagedPhase.Skeleton, PagedPhase.Skeleton, PagedPhase.Skeleton, PagedPhase.Empty), phases)
    }

    @Test
    fun `a cold open of one page stays on the skeleton while the cache catches up, then shows the rows`() {
        val phases =
            phases(
                Frame(source = loading, mediator = idle),
                Frame(source = idle, mediator = loading),
                // The gap: the mediator is done, the cache still holds the empty read from before it wrote.
                Frame(source = idle, mediator = idle, refresh = wrote(3), mediatorAppend = complete),
                Frame(source = loading, mediator = idle, refresh = wrote(3), mediatorAppend = complete),
                Frame(source = idle, mediator = idle, count = 3, refresh = wrote(3), mediatorAppend = complete),
            )

        assertEquals(listOf(PagedPhase.Skeleton, PagedPhase.Skeleton, PagedPhase.Skeleton, PagedPhase.Skeleton, rows), phases)
    }

    @Test
    fun `a cold open of several pages is the same, with more to append`() {
        val phases =
            phases(
                Frame(source = idle, mediator = loading),
                Frame(source = idle, mediator = idle, refresh = wrote(20)),
                Frame(source = idle, mediator = idle, count = 20, refresh = wrote(20)),
            )

        assertEquals(listOf(PagedPhase.Skeleton, PagedPhase.Skeleton, rows), phases)
    }

    @Test
    fun `an open over cached rows keeps them on screen through the refresh`() {
        val phases =
            phases(
                Frame(source = loading, mediator = idle),
                Frame(source = idle, mediator = loading, count = 4),
                Frame(source = idle, mediator = idle, count = 4, refresh = wrote(6)),
                Frame(source = idle, mediator = idle, count = 6, refresh = wrote(6)),
            )

        assertEquals(listOf(PagedPhase.Skeleton, refreshingRows, rows, rows), phases)
    }

    @Test
    fun `cached rows the server no longer has give way to empty once the cache drops them`() {
        val phases =
            phases(
                Frame(source = idle, mediator = loading, count = 2),
                Frame(source = idle, mediator = idle, count = 2, refresh = wrote(0)),
                Frame(source = idle, mediator = idle, count = 0, refresh = wrote(0)),
            )

        assertEquals(listOf(refreshingRows, rows, PagedPhase.Empty), phases)
    }

    @Test
    fun `the last row resolved or deleted here leaves an empty list, not a skeleton`() {
        val phases =
            phases(
                Frame(source = idle, mediator = idle, count = 1, refresh = wrote(1)),
                Frame(source = loading, mediator = idle, count = 1, refresh = wrote(1)),
                Frame(source = idle, mediator = idle, count = 0, refresh = wrote(1)),
            )

        assertEquals(listOf(rows, rows, PagedPhase.Empty), phases)
    }

    @Test
    fun `a refresh that fails behind rows keeps them and says so`() {
        val error = LoadState.Error(failure)

        val phases = phases(Frame(source = idle, mediator = loading, count = 3), Frame(source = idle, mediator = error, count = 3))

        assertEquals(listOf(refreshingRows, PagedPhase.Rows(refreshing = false, refreshError = failure)), phases)
    }

    @Test
    fun `a refresh that fails with nothing cached is the error, even while the cache reads`() {
        val error = LoadState.Error(failure)

        val phases =
            phases(
                Frame(source = idle, mediator = loading),
                Frame(source = loading, mediator = error),
                Frame(source = idle, mediator = error),
            )

        assertEquals(listOf(PagedPhase.Skeleton, PagedPhase.Failed(failure), PagedPhase.Failed(failure)), phases)
    }

    @Test
    fun `a list read straight from a paging source starts on the skeleton, as paging-compose reports it before the first load`() {
        // LazyPagingItems' state before its first PagingData: refresh Loading, no mediator.
        val phases =
            phases(
                Frame(source = loading, mediator = null),
                Frame(source = idle, mediator = null),
            )

        assertEquals(listOf(PagedPhase.Skeleton, PagedPhase.Empty), phases)
    }

    @Test
    fun `a paging source's rows, its reload line and its failure`() {
        assertEquals(
            listOf(rows, refreshingRows, PagedPhase.Failed(failure)),
            phases(
                Frame(source = idle, mediator = null, count = 2),
                Frame(source = loading, mediator = null, count = 2),
                Frame(source = LoadState.Error(failure), mediator = null),
            ),
        )
    }

    @Test
    fun `a cache read in progress is loading even when the combined refresh says it has finished`() {
        val states =
            CombinedLoadStates(
                refresh = idle,
                prepend = idle,
                append = idle,
                source = LoadStates(refresh = loading, prepend = idle, append = idle),
                mediator = LoadStates(refresh = idle, prepend = idle, append = complete),
            )

        assertEquals(PagedPhase.Skeleton, pagedPhase(states, itemCount = 0, cacheBehind = false))
    }
}
