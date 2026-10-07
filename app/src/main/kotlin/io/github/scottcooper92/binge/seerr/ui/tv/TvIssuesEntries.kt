package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailUiState
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.issues.IssueFilter
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesUiState
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesViewModel
import io.github.scottcooper92.binge.seerr.ui.tv.issues.IssueRowFilters
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssueDetailActions
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssueDetailScreen
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesActions
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesBoard
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesGrid
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesGridManagement

@Composable
internal fun TvIssuesEntry(
    onReconnect: () -> Unit,
    openIssueId: Int?,
    onOpenIssue: (Int) -> Unit,
    seeAllOpen: Boolean,
    onSeeAll: (IssueFilter) -> Unit,
    viewModel: IssuesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    val ready = state as? IssuesUiState.Ready
    // One pager per filter, each handed down as a count and an accessor, as Home does for the requests.
    val pagers = IssueRowFilters.associateWith { viewModel.issues(it).collectAsLazyPagingItems() }
    val rowsByFilter = IssueRowFilters.associateWith { pagers.getValue(it).toRows(ready?.refreshes?.get(it)) { item -> item.id } }
    TvIssuesBoard(
        state = state,
        rowsFor = { rowsByFilter.getValue(it) },
        events = viewModel.events,
        openIssueId = openIssueId,
        seeAllOpen = seeAllOpen,
        actions =
            TvIssuesActions(
                onOpenActions = viewModel::openActions,
                onDismissActions = viewModel::dismissActions,
                onOpenDetail = { item -> onOpenIssue(item.id) },
                onResolve = viewModel::resolve,
                onReopen = viewModel::reopen,
                onDelete = viewModel::delete,
                onSeeAll = onSeeAll,
                onRetryLoad = { pagers.values.forEach { it.retry() } },
                onReconnect = onReconnect,
            ),
    )
}

/** Every issue behind one filter's row, as a paged grid above the rail, on the same ViewModel as the board. */
@Composable
internal fun TvIssuesGridOverlay(
    filter: IssueFilter,
    detailOpen: Boolean,
    onOpenIssue: (Int) -> Unit,
    onDone: () -> Unit,
    onReconnect: () -> Unit,
    viewModel: IssuesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? IssuesUiState.Ready
    val lazyItems = viewModel.issues(filter).collectAsLazyPagingItems()
    TvIssuesGrid(
        filter = filter,
        counts = ready?.counts,
        rows = lazyItems.toRows(ready?.refreshes?.get(filter)) { it.id },
        actingIds = ready?.actingIds.orEmpty(),
        management =
            TvIssuesGridManagement(
                scope = ready?.scope,
                actionItem = ready?.actionItem,
                onOpenActions = viewModel::openActions,
                onDismissActions = viewModel::dismissActions,
                onResolve = viewModel::resolve,
                onReopen = viewModel::reopen,
                onDelete = viewModel::delete,
            ),
        detailOpen = detailOpen,
        onOpenDetail = { onOpenIssue(it.id) },
        onRetryLoad = { lazyItems.retry() },
        onReconnect = onReconnect,
        onBack = onDone,
    )
}

/**
 * An issue's read-only page, above the rail exactly as the connection form is: the same
 * [IssueDetailViewModel] the phone's `IssueDetailEntry` binds, and one instance per issue id, keyed by
 * [issueId] for the same reason as the request page.
 */
@Composable
internal fun TvIssueDetailOverlay(
    issueId: Int,
    onDone: () -> Unit,
    viewModel: IssueDetailViewModel =
        hiltViewModel<IssueDetailViewModel, IssueDetailViewModel.Factory>(
            key = "issue-detail-$issueId",
            creationCallback = { factory -> factory.create(issueId) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { if (viewModel.uiState.value is IssueDetailUiState.Ready) viewModel.reload() }
    TvIssueDetailScreen(
        state = state,
        actions = TvIssueDetailActions(onBack = onDone, onRetry = viewModel::reload),
    )
}
