package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.cooper.binge.companion.seerr.flags.LocalFlags
import com.cooper.binge.companion.seerr.ui.bingeTitleHandOff
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailEvent
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailViewModel
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssuesUiState
import com.cooper.binge.companion.seerr.ui.issues.IssuesViewModel
import com.cooper.binge.companion.seerr.ui.tv.issues.IssueRowFilters
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssueSheet
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssueSheetActions
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesActions
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesBoard
import com.cooper.binge.companion.seerr.ui.tv.issues.TvIssuesGrid

@Composable
internal fun TvIssuesEntry(
    onOpenIssue: (Int) -> Unit,
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
        now = ready?.now ?: System.currentTimeMillis(),
        actions =
            TvIssuesActions(
                onOpenDetail = { item -> onOpenIssue(item.id) },
                onSeeAll = onSeeAll,
                onRetryLoad = { pagers.values.forEach { it.retry() } },
                onRetryScope = viewModel::retry,
            ),
    )
}

/** Every issue behind one filter's row, as a paged grid above Home, on the same ViewModel as the board. */
@Composable
internal fun TvIssuesGridOverlay(
    filter: IssueFilter,
    detailOpen: Boolean,
    onOpenIssue: (Int) -> Unit,
    onDone: () -> Unit,
    viewModel: IssuesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? IssuesUiState.Ready
    val lazyItems = viewModel.issues(filter).collectAsLazyPagingItems()
    TvIssuesGrid(
        filter = filter,
        counts = ready?.counts,
        rows = lazyItems.toRows(ready?.refreshes?.get(filter)) { it.id },
        detailOpen = detailOpen,
        onOpenDetail = { onOpenIssue(it.id) },
        onRetryLoad = { lazyItems.retry() },
        onBack = onDone,
        now = ready?.now ?: System.currentTimeMillis(),
    )
}

/**
 * An issue's sheet, over the bar: the same [IssueDetailViewModel] the phone's `IssueDetailEntry` binds, in a view-model
 * scope of its own as the request page's is. A deleted issue closes it. [onIssueChanged] hears a resolve, a reopen or a
 * delete, so the board's counts move with it.
 */
@Composable
internal fun TvIssueSheetEntry(
    issueId: Int,
    onDone: () -> Unit,
    onIssueChanged: () -> Unit,
    viewModel: IssueDetailViewModel =
        hiltViewModel<IssueDetailViewModel, IssueDetailViewModel.Factory>(creationCallback = { factory -> factory.create(issueId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event.changesTheIssue()) onIssueChanged()
            if (event == IssueDetailEvent.IssueDeleted) onDone()
        }
    }
    val context = LocalContext.current
    val promoted = LocalFlags.current.bingePromotion
    val detail = (state as? IssueDetailUiState.Ready)?.detail
    val onOpenInMediaServer = remember(context, detail?.mediaServerItem) { context.mediaServerHandOff(detail?.mediaServerItem) }
    // As on the request page: offered only where Binge would answer the link, since there is no browser to fall back to.
    val title = detail?.item
    val onOpenInBinge =
        remember(context, promoted, title?.mediaType, title?.tmdbId) { title?.let { context.bingeTitleHandOff(promoted, it.mediaType, it.tmdbId) } }
    TvIssueSheet(
        state = state,
        events = viewModel.events,
        actions =
            TvIssueSheetActions(
                onDismiss = onDone,
                onRetry = viewModel::reload,
                onToggleStatus = viewModel::toggleStatus,
                onDeleteIssue = viewModel::deleteIssue,
                onDeleteComment = viewModel::deleteComment,
                onOpenInMediaServer = onOpenInMediaServer,
                onOpenInBinge = onOpenInBinge,
            ),
    )
}

/** An answer that moves the issue between the board's filters, or off the board. */
internal fun IssueDetailEvent.changesTheIssue(): Boolean =
    this == IssueDetailEvent.IssueResolved || this == IssueDetailEvent.IssueReopened || this == IssueDetailEvent.IssueDeleted
