package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.SortSheet
import io.github.scottcooper92.binge.seerr.ui.state.innerPadding
import io.github.scottcooper92.binge.seerr.ui.state.outerPadding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

class RequestsActions(
    val onBack: () -> Unit,
    val onFilterChange: (RequestFilter) -> Unit,
    val onSortChange: (RequestSort) -> Unit,
    val onOpen: (RequestItem) -> Unit,
    val onOpenActions: (RequestItem) -> Unit,
    val onDismissActions: () -> Unit,
    val onApprove: (Int) -> Unit,
    val onRetry: (Int) -> Unit,
    val onDecline: (RequestItem, Boolean) -> Unit,
    val onRemove: (RequestItem, Boolean) -> Unit,
)

/**
 * The requests browser: filter chips with the server's totals over the selected filter's paged rows.
 *
 * @param shouldRefresh true at most once per list version per filter, so a freshly composed, current
 * page does not blank-refresh after a moderation elsewhere.
 * @param showBack false when the hub is showing beside this pane, where a back arrow to it is redundant.
 */
@Composable
fun RequestsScreen(
    state: RequestsUiState,
    requestsFor: (RequestFilter) -> Flow<PagingData<RequestItem>>,
    events: Flow<ModerationEvent>,
    shouldRefresh: (RequestFilter, Int) -> Boolean,
    actions: RequestsActions,
    showBack: Boolean = true,
) {
    var showSort by rememberSaveable { mutableStateOf(false) }
    val ready = state as? RequestsUiState.Ready
    val snackbarHostState = remember { SnackbarHostState() }
    ModerationSnackbarEffect(events, snackbarHostState)
    val header: (@Composable () -> Unit)? =
        ready?.let { chips ->
            @Composable {
                BingeFilterChipRow(
                    items =
                        RequestFilter.entries.map {
                            FilterChipItem(label = stringResource(it.labelRes()), count = chips.counts?.countFor(it))
                        },
                    selectedIndex = chips.filter.ordinal,
                    onSelect = { actions.onFilterChange(RequestFilter.entries[it]) },
                )
            }
        }
    ScreenScaffold(
        title = stringResource(R.string.hub_section_requests),
        onBack = actions.onBack.takeIf { showBack },
        snackbarHostState = snackbarHostState,
        header = header,
        actions = {
            if (ready != null) {
                IconButton(onClick = { showSort = true }) {
                    Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.requests_sort_cd))
                }
            }
        },
    ) { padding ->
        if (ready == null) {
            LoadingScreen(Modifier.fillMaxSize().padding(padding))
        } else {
            Box(Modifier.fillMaxSize().padding(padding.outerPadding())) {
                val lazyItems = requestsFor(ready.filter).collectAsLazyPagingItems()
                // A moderation bumps the version; a stale filter refreshes once, anchored, keeping its scroll.
                LaunchedEffect(ready.listVersion, ready.filter) {
                    if (shouldRefresh(ready.filter, ready.listVersion)) lazyItems.refresh()
                }
                RequestsBody(
                    filter = ready.filter,
                    lazyItems = lazyItems,
                    scope = ready.scope,
                    actingIds = ready.actingIds,
                    onOpen = actions.onOpen,
                    onOpenActions = actions.onOpenActions,
                    // A rejected session cannot be retried past: the hub owns reconnecting.
                    onReconnect = actions.onBack,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding.innerPadding(),
                )
            }
        }
    }
    if (showSort && ready != null) {
        SortSheet(
            choices = RequestSort.entries,
            selected = ready.sort,
            label = { stringResource(it.labelRes()) },
            onSelect = actions.onSortChange,
            onDismiss = { showSort = false },
        )
    }
    ready?.actionItem?.let { item ->
        RequestActionsSheet(
            item = item,
            actions = item.actions(ready.scope),
            onApprove = { actions.onApprove(item.id) },
            onRetry = { actions.onRetry(item.id) },
            onDecline = { block -> actions.onDecline(item, block) },
            onRemove = { block -> actions.onRemove(item, block) },
            onDismiss = actions.onDismissActions,
        )
    }
}

/** Each moderation outcome as a snackbar; a newer one supersedes the one still showing. */
@Composable
internal fun ModerationSnackbarEffect(
    events: Flow<ModerationEvent>,
    snackbarHostState: SnackbarHostState,
) {
    val resources = LocalResources.current
    LaunchedEffect(events) {
        events.collectLatest { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = resources.getString(event.messageRes()),
                kind = if (event.isError()) SnackbarMessageKind.Error else SnackbarMessageKind.Confirmation,
            )
        }
    }
}
