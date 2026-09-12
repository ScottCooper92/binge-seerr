package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.binge.designsystem.component.BingeFilterChipRow
import com.binge.designsystem.component.BingeSnackbarHost
import com.binge.designsystem.component.BingeTopBar
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
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

/** The in-place refresh after a moderation: the version stream plus the once-per-version gate. */
class ListRefresh(
    val version: StateFlow<Int>,
    val shouldRefresh: (RequestFilter, Int) -> Boolean,
)

/** The requests browser: filter chips with the server's totals over the selected filter's paged rows. */
@Composable
fun RequestsScreen(
    state: RequestsUiState,
    requestsFor: (RequestFilter) -> Flow<PagingData<RequestItem>>,
    events: Flow<ModerationEvent>,
    refresh: ListRefresh,
    actions: RequestsActions,
) {
    var showSort by rememberSaveable { mutableStateOf(false) }
    val ready = state as? RequestsUiState.Ready
    val snackbarHostState = remember { SnackbarHostState() }
    ModerationSnackbarEffect(events, snackbarHostState)
    Scaffold(
        snackbarHost = { BingeSnackbarHost(snackbarHostState) },
        topBar = {
            BingeTopBar(
                title = stringResource(R.string.hub_section_requests),
                onBack = actions.onBack,
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { showSort = true }) {
                            Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.requests_sort_cd))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (ready == null) {
            LoadingScreen(Modifier.fillMaxSize().padding(padding))
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                BingeFilterChipRow(
                    items =
                        RequestFilter.entries.map {
                            FilterChipItem(
                                label = stringResource(it.labelRes()),
                                count = ready.counts?.countFor(it),
                            )
                        },
                    selectedIndex = ready.filter.ordinal,
                    onSelect = { actions.onFilterChange(RequestFilter.entries[it]) },
                )
                val lazyItems = requestsFor(ready.filter).collectAsLazyPagingItems()
                val version by refresh.version.collectAsStateWithLifecycle()
                // A moderation bumps the version; a stale filter refreshes once, anchored, keeping its scroll.
                LaunchedEffect(version, ready.filter) {
                    if (refresh.shouldRefresh(ready.filter, version)) lazyItems.refresh()
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
                )
            }
        }
    }
    if (showSort && ready != null) {
        RequestSortSheet(selected = ready.sort, onSelect = actions.onSortChange, onDismiss = { showSort = false })
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
