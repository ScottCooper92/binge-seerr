package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.tv.focus.rememberTvOverlayCloser
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.requests.ModerationEvent
import io.github.scottcooper92.binge.seerr.ui.requests.RequestCounts
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.requests.actions
import io.github.scottcooper92.binge.seerr.ui.requests.emptyMessageRes
import io.github.scottcooper92.binge.seerr.ui.requests.isError
import io.github.scottcooper92.binge.seerr.ui.requests.labelRes
import io.github.scottcooper92.binge.seerr.ui.requests.messageRes
import io.github.scottcooper92.binge.seerr.ui.tv.TvBoardFrame
import io.github.scottcooper92.binge.seerr.ui.tv.TvBoardPlate
import io.github.scottcooper92.binge.seerr.ui.tv.TvFilterBand
import io.github.scottcooper92.binge.seerr.ui.tv.TvFormNote
import io.github.scottcooper92.binge.seerr.ui.tv.TvFormNoteTone
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedList
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.rememberTvTransientEvent
import kotlinx.coroutines.flow.Flow

/** Everything the requests board can ask of its ViewModel, in one place so the entry stays a wiring. */
internal class TvRequestsActions(
    val onFilterChange: (RequestFilter) -> Unit,
    val onOpenActions: (RequestItem) -> Unit,
    val onDismissActions: () -> Unit,
    val onApprove: (Int) -> Unit,
    val onRetry: (Int) -> Unit,
    val onDecline: (RequestItem, Boolean) -> Unit,
    val onRemove: (RequestItem, Boolean) -> Unit,
    val onRetryLoad: () -> Unit,
    val onReconnect: () -> Unit,
)

/**
 * The requests browser as a television board: the filters as a band the D-pad walks, the selected filter's
 * rows beneath, and a row's moderation on the end-edge sheet. The outcome of each moderation shows as a
 * line under the list for a few seconds, the board's answer to the phone's snackbar.
 *
 * [rows] is the paged list decomposed into a count and an accessor, so the board is reachable from a plain
 * JVM test; the entry supplies the real pager. [initialFocusedRowId] and [initialFocusedFilterLabel] seed a
 * preview; production passes neither.
 */
@Composable
internal fun TvRequestsBoard(
    state: RequestsUiState,
    rows: TvPagedRows<RequestItem>,
    events: Flow<ModerationEvent>,
    actions: TvRequestsActions,
    modifier: Modifier = Modifier,
    initialFocusedRowId: Int? = null,
    initialFocusedFilterLabel: String? = null,
) {
    val ready = state as? RequestsUiState.Ready
    val event = rememberTvTransientEvent(events)
    // The row that opened the sheet gets focus back when the sheet closes, a frame after it is gone.
    val restoreFocus = remember { FocusRequester() }
    // Pinned to the row that opened the sheet, not the open action item: by the time the closer requests
    // the return the item is already null, and the requester must still be attached somewhere.
    var restoreRowId by rememberSaveable { mutableStateOf<Int?>(null) }
    val closer = rememberTvOverlayCloser(restoreTo = restoreFocus, onClose = actions.onDismissActions)
    Box(modifier = modifier.fillMaxSize()) {
        TvBoardFrame(title = stringResource(R.string.hub_section_requests)) {
            if (ready == null) {
                TvBoardPlate(body = stringResource(R.string.tv_loading), modifier = Modifier.weight(1f))
                return@TvBoardFrame
            }
            TvFilterBand(
                filters = RequestFilter.entries.map { filter -> filter to filterLabel(filter, ready.counts) },
                selected = ready.filter,
                onSelect = actions.onFilterChange,
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
                    onSelect =
                        {
                            restoreRowId = item.id
                            actions.onOpenActions(item)
                        }.takeIf { item.actions(ready.scope).any },
                    isActing = item.id in ready.actingIds,
                    initiallyFocused = item.id == initialFocusedRowId,
                    modifier = if (item.id == restoreRowId) Modifier.focusRequester(restoreFocus) else Modifier,
                )
            }
            event?.let {
                TvFormNote(
                    text = stringResource(it.messageRes()),
                    tone = if (it.isError()) TvFormNoteTone.Error else TvFormNoteTone.Success,
                )
            }
        }
        ready?.actionItem?.let { item ->
            TvRequestActionsSheet(
                item = item,
                actions = item.actions(ready.scope),
                sheetActions =
                    TvRequestSheetActions(
                        onApprove = {
                            actions.onApprove(item.id)
                            closer.close()
                        },
                        onRetry = {
                            actions.onRetry(item.id)
                            closer.close()
                        },
                        onDecline = { block ->
                            actions.onDecline(item, block)
                            closer.close()
                        },
                        onRemove = { block ->
                            actions.onRemove(item, block)
                            closer.close()
                        },
                        onDismiss = closer::close,
                    ),
            )
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
