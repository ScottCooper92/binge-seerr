package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Where a pull rests while it refreshes. Its own state animates there from a coroutine, which a still frame never
 * runs, so without this a refreshing frame shows no spinner at all. The design system's own sample does the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal object RestingPull : PullToRefreshState {
    override val distanceFraction: Float = 1f
    override val isAnimating: Boolean = false

    override suspend fun animateToThreshold() = Unit

    override suspend fun animateToHidden() = Unit

    override suspend fun snapTo(targetValue: Float) = Unit
}

private val idle = LoadStates(LoadState.NotLoading(false), LoadState.NotLoading(false), LoadState.NotLoading(false))
private val refreshing = idle.copy(refresh = LoadState.Loading)

/**
 * A page of [rows] whose refresh is running, as a still frame can show it. `LazyPagingItems` takes a replayed
 * `PagingData` as its first value, so the rows are there on the first frame, where a plain flow leaves it loading.
 * [fromServer] is a remote mediator refreshing; false is the paging source alone, as the blocklist reads.
 *
 * A frame remembers the flow it gets, since `collectAsLazyPagingItems` starts over for a new one.
 */
internal fun <T : Any> refreshingRows(
    rows: List<T>,
    fromServer: Boolean = true,
): Flow<PagingData<T>> =
    MutableStateFlow(
        if (fromServer) {
            PagingData.from(rows, sourceLoadStates = idle, mediatorLoadStates = refreshing)
        } else {
            PagingData.from(rows, sourceLoadStates = refreshing)
        },
    )
