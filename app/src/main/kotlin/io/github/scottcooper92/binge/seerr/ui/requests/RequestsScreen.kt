package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.binge.designsystem.component.BingeFilterChipPager
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.ScreenScaffold
import io.github.scottcooper92.binge.seerr.ui.state.SortSheet
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
 * The requests browser: a page of paged rows per filter, swiped between or picked from chips carrying the
 * server's totals. The ViewModel owns the selected filter; a chip's index is its filter's ordinal.
 *
 * @param shouldRefresh true at most once per list version per filter, so a freshly composed, current
 * page does not blank-refresh after a moderation elsewhere.
 * @param showBack false when the hub is showing beside this pane, where a back arrow to it is redundant.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    ScreenScaffold(
        title = stringResource(R.string.hub_section_requests),
        onBack = actions.onBack.takeIf { showBack },
        snackbarHostState = snackbarHostState,
        scrollBehavior = scrollBehavior,
        // The pager's header draws the one scrim over the bar and the chips together.
        barScrim = ready == null,
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
            BingeFilterChipPager(
                items =
                    RequestFilter.entries.map {
                        FilterChipItem(label = stringResource(it.labelRes()), count = ready.counts?.countFor(it))
                    },
                selectedIndex = ready.filter.ordinal,
                onSelectedIndexChange = { actions.onFilterChange(RequestFilter.entries[it]) },
                modifier = Modifier.fillMaxSize().padding(padding.outerPadding()),
                // The bar's height joins the pager's header, so the rows reach the top of the window and pass under both.
                header = { Spacer(Modifier.height(padding.calculateTopPadding())) },
                headerBackground = Color.Transparent,
                scrimFraction = scrollBehavior.state.collapsedFraction,
            ) { pagePadding, page ->
                RequestsPage(
                    filter = RequestFilter.entries[page],
                    state = ready,
                    requestsFor = requestsFor,
                    shouldRefresh = shouldRefresh,
                    actions = actions,
                    contentPadding = PaddingValues(top = pagePadding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
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

/**
 * One filter's page. The pager composes a page before the selection lands on it, while it is swiped into
 * view, so a page collects its own [filter]'s rows and never the selected one's. Refreshing is the
 * selected page's alone: a page only swiped past must not spend the list's one refresh for this version.
 */
@Composable
private fun RequestsPage(
    filter: RequestFilter,
    state: RequestsUiState.Ready,
    requestsFor: (RequestFilter) -> Flow<PagingData<RequestItem>>,
    shouldRefresh: (RequestFilter, Int) -> Boolean,
    actions: RequestsActions,
    contentPadding: PaddingValues,
) {
    val lazyItems = requestsFor(filter).collectAsLazyPagingItems()
    val selected = filter == state.filter
    // A moderation bumps the version; a stale page refreshes once it is selected, anchored, keeping its scroll.
    LaunchedEffect(state.listVersion, selected) {
        if (selected && shouldRefresh(filter, state.listVersion)) lazyItems.refresh()
    }
    RequestsBody(
        filter = filter,
        lazyItems = lazyItems,
        scope = state.scope,
        actingIds = state.actingIds,
        onOpen = actions.onOpen,
        onRemove = { item -> actions.onRemove(item, false) },
        // A rejected session cannot be retried past: the hub owns reconnecting.
        onReconnect = actions.onBack,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    )
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
