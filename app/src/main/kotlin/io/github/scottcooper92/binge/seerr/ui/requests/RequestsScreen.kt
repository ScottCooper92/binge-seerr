package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshState
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
import com.binge.designsystem.component.FilterChipItem
import com.binge.designsystem.component.SnackbarMessageKind
import com.binge.designsystem.component.showSnackbar
import com.binge.designsystem.template.FilteredListScreen
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.state.SortSheet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

class RequestsActions(
    val onBack: () -> Unit,
    val onFilterChange: (RequestFilter) -> Unit,
    val onSortChange: (RequestSort) -> Unit,
    val onOpen: (RequestItem) -> Unit,
    val onRetryLoad: () -> Unit,
    /** A pull refreshed a list, so the chips re-read their counts. */
    val onRefreshCounts: () -> Unit,
    /** A moderation from a row's sheet finished, so the lists and counts are stale. */
    val onChanged: () -> Unit,
    /**
     * A request's actions sheet. A slot because it is the request's own detail view model that backs it,
     * so a row's sheet carries the same edit and media rows as the detail page's.
     */
    val detailSheet: @Composable (SiblingSheet) -> Unit,
)

/**
 * The requests browser: a page of paged rows per filter, swiped between or picked from chips carrying the
 * server's totals. The ViewModel owns the selected filter; a chip's index is its filter's ordinal.
 *
 * @param shouldRefresh true at most once per list version per filter, so a freshly composed, current
 * page does not blank-refresh after a moderation elsewhere.
 * @param showBack false when the hub is showing beside this pane, where a back arrow to it is redundant.
 * @param pullState a still frame's resting pull for every page; null gives each page M3's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(
    state: RequestsUiState,
    requestsFor: (RequestFilter) -> Flow<PagingData<RequestItem>>,
    shouldRefresh: (RequestFilter, Int) -> Boolean,
    actions: RequestsActions,
    showBack: Boolean = true,
    pullState: PullToRefreshState? = null,
) {
    var showSort by rememberSaveable { mutableStateOf(false) }
    // The request whose sheet was last opened, and whether it is up: dismissing keeps the id so a
    // moderation's result, which lands after the sheet closes, still reaches its snackbar and the list.
    var sheetId by rememberSaveable { mutableStateOf<Int?>(null) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var sheetItem by remember { mutableStateOf<RequestItem?>(null) }
    val ready = state as? RequestsUiState.Ready
    val snackbarHostState = remember { SnackbarHostState() }
    FilteredListScreen(
        title = stringResource(R.string.hub_section_requests),
        onBack = actions.onBack.takeIf { showBack },
        filters =
            ready
                ?.let { loaded ->
                    RequestFilter.entries.map { FilterChipItem(label = stringResource(it.labelRes()), count = loaded.counts?.countFor(it)) }
                }.orEmpty(),
        selectedFilter = ready?.filter?.ordinal ?: 0,
        onFilterChange = { actions.onFilterChange(RequestFilter.entries[it]) },
        ready = ready != null,
        snackbarHostState = snackbarHostState,
        actions = {
            if (ready != null) {
                IconButton(onClick = { showSort = true }) {
                    Icon(Icons.Filled.SwapVert, contentDescription = stringResource(R.string.requests_sort_cd))
                }
            }
        },
        notReady = { padding ->
            when (state) {
                is RequestsUiState.Error ->
                    ErrorScreen(error = state.error, modifier = Modifier.padding(padding), onRetry = actions.onRetryLoad)
                else -> LoadingScreen(Modifier.fillMaxSize().padding(padding))
            }
        },
    ) { page, contentPadding ->
        // `ready` is non-null whenever a page composes: the template shows notReady until it is.
        ready?.let { loaded ->
            RequestsPage(
                filter = RequestFilter.entries[page],
                state = loaded,
                requestsFor = requestsFor,
                shouldRefresh = shouldRefresh,
                actions = actions,
                onManage = { item ->
                    sheetId = item.id
                    sheetItem = item
                    sheetOpen = true
                },
                contentPadding = contentPadding,
                pullState = pullState,
            )
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
    sheetId?.let { id ->
        actions.detailSheet(
            SiblingSheet(
                requestId = id,
                open = sheetOpen,
                snackbarHostState = snackbarHostState,
                onDismiss = { sheetOpen = false },
                onChanged = actions.onChanged,
                preview = sheetItem?.takeIf { it.id == id }?.let { RequestPreview(it, it.actions(ready?.scope ?: ModerationScope())) },
            ),
        )
    }
}

/**
 * One filter's page. The pager composes a page before the selection lands on it — the pages either side of
 * the selected one are kept composed — so a page collects its own [filter]'s rows and never the selected
 * one's. Refreshing is the selected page's alone: a neighbour or a page only swiped past must not spend the
 * list's one refresh for this version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestsPage(
    filter: RequestFilter,
    state: RequestsUiState.Ready,
    requestsFor: (RequestFilter) -> Flow<PagingData<RequestItem>>,
    shouldRefresh: (RequestFilter, Int) -> Boolean,
    actions: RequestsActions,
    onManage: (RequestItem) -> Unit,
    contentPadding: PaddingValues,
    pullState: PullToRefreshState?,
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
        lastRefresh = state.refreshes[filter],
        scope = state.scope,
        now = state.now,
        onOpen = actions.onOpen,
        onManage = onManage,
        onPull = actions.onRefreshCounts,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        pullState = pullState,
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
