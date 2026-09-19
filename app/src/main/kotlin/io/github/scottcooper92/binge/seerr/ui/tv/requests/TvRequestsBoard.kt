package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.focus.restoreTvOverlayFocus
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.RequestCounts
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSort
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.requests.emptyMessageRes
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.tv.TvBoardBands
import io.github.scottcooper92.binge.seerr.ui.tv.TvBoardFrame
import io.github.scottcooper92.binge.seerr.ui.tv.TvBoardPlate
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedList
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows

/** Everything the requests board can ask of its host, in one place so the entry stays a wiring. */
internal class TvRequestsActions(
    val onFilterChange: (RequestFilter) -> Unit,
    val onSortChange: (RequestSort) -> Unit,
    val onOpenDetail: (RequestItem) -> Unit,
    val onRetryLoad: () -> Unit,
    val onReconnect: () -> Unit,
)

/**
 * The requests browser as a television board: the filters as a band the D-pad walks, the selected filter's
 * rows beneath. OK on a row opens its read-only detail page (an overlay above the rail, not owned by this
 * board); moderation lives there now rather than on an end-edge sheet opened straight from the row.
 *
 * [rows] is the paged list decomposed into a count and an accessor, so the board is reachable from a plain
 * JVM test; the entry supplies the real pager. [openRequestId] is the id of the request whose detail page is
 * currently showing, or null once it has closed — on that transition the board offers focus back to the row
 * that opened it, one frame after the page's own disposal, the same round trip the end-edge sheet used to run
 * locally. [initialFocusedRowId] and [initialFocusedFilterLabel] seed a preview; production passes neither.
 */
@Composable
internal fun TvRequestsBoard(
    state: RequestsUiState,
    rows: TvPagedRows<RequestItem>,
    actions: TvRequestsActions,
    modifier: Modifier = Modifier,
    openRequestId: Int? = null,
    initialFocusedRowId: Int? = null,
    initialFocusedFilterLabel: String? = null,
    now: Long = System.currentTimeMillis(),
) {
    val ready = state as? RequestsUiState.Ready
    val restoreFocus = remember { FocusRequester() }
    // Pinned to the row that opened the page, not the open request id: by the time the id clears the row
    // that carried it may have scrolled off, and the requester must still be attached somewhere.
    var restoreRowId by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(openRequestId) {
        if (openRequestId == null && restoreRowId != null) restoreTvOverlayFocus(restoreFocus)
    }
    Box(modifier = modifier.fillMaxSize()) {
        TvBoardFrame(title = stringResource(R.string.hub_section_requests)) {
            if (ready == null) {
                TvBoardPlate(body = stringResource(R.string.tv_loading), modifier = Modifier.weight(1f))
                return@TvBoardFrame
            }
            TvBoardBands(
                filters = RequestFilter.entries.map { filter -> filter to filterLabel(filter, ready.counts) },
                selectedFilter = ready.filter,
                onFilterChange = actions.onFilterChange,
                sorts = RequestSort.entries.map { sort -> sort to stringResource(sort.labelRes()) },
                selectedSort = ready.sort,
                onSortChange = actions.onSortChange,
                initialFocusedLabel = initialFocusedFilterLabel,
            )
            TvPagedList(
                rows = rows,
                emptyMessage = stringResource(ready.filter.emptyMessageRes()),
                onRetryLoad = actions.onRetryLoad,
                onReconnect = actions.onReconnect,
                modifier = Modifier.weight(1f),
            ) { item ->
                TvRequestRow(
                    item = item,
                    onSelect = {
                        restoreRowId = item.id
                        actions.onOpenDetail(item)
                    },
                    isActing = item.id in ready.actingIds,
                    initiallyFocused = item.id == initialFocusedRowId,
                    now = now,
                    modifier = if (item.id == restoreRowId) Modifier.focusRequester(restoreFocus) else Modifier,
                )
            }
        }
    }
}

/** The filter's name with the server's total behind it, where the server counts that bucket. */
@Composable
private fun filterLabel(
    filter: RequestFilter,
    counts: RequestCounts?,
): String {
    val label = stringResource(filter.labelRes())
    val count = counts?.countFor(filter) ?: return label
    return stringResource(R.string.tv_filter_with_count, label, count)
}
