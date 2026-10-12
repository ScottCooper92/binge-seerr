package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistActions
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistDetailActions
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistDetailScreen
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistDetailViewModel
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistItem
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistScreen
import com.cooper.binge.companion.seerr.ui.blocklist.BlocklistViewModel
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailActions
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailScreen
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailViewModel
import com.cooper.binge.companion.seerr.ui.issues.IssuesActions
import com.cooper.binge.companion.seerr.ui.issues.IssuesScreen
import com.cooper.binge.companion.seerr.ui.issues.IssuesViewModel

@Composable
internal fun IssuesEntry(
    onBack: () -> Unit,
    showBack: Boolean,
    onOpen: (Int) -> Unit,
    viewModel: IssuesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The chip counts refetch on arrival, so a resolve elsewhere shows without a poll.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    IssuesScreen(
        state = state,
        showBack = showBack,
        issuesFor = viewModel::issues,
        actions =
            IssuesActions(
                onBack = onBack,
                onFilterChange = viewModel::setFilter,
                onSortChange = viewModel::setSort,
                onOpen = { item -> onOpen(item.id) },
                onRetryLoad = viewModel::retry,
                onRefreshCounts = viewModel::refreshCounts,
            ),
    )
}

@Composable
internal fun IssueDetailEntry(
    issueId: Int,
    onBack: () -> Unit,
    showBack: Boolean,
    viewModel: IssueDetailViewModel =
        hiltViewModel<IssueDetailViewModel, IssueDetailViewModel.Factory>(creationCallback = { factory -> factory.create(issueId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    IssueDetailScreen(
        state = state,
        events = viewModel.events,
        showBack = showBack,
        actions =
            IssueDetailActions(
                onBack = onBack,
                onRetry = viewModel::reload,
                onDraftChange = viewModel::setDraft,
                onPostComment = viewModel::postComment,
                onRetryOutbox = viewModel::retryOutbox,
                onEditOutbox = viewModel::editOutbox,
                onDropOutbox = viewModel::dropOutbox,
                onEditComment = viewModel::editComment,
                onRetryEdit = viewModel::retryEdit,
                onDeleteComment = viewModel::deleteComment,
                onToggleStatus = viewModel::toggleStatus,
                onDeleteIssue = viewModel::deleteIssue,
            ),
    )
}

@Composable
internal fun BlocklistEntry(
    onBack: () -> Unit,
    showBack: Boolean,
    onOpen: (item: BlocklistItem, canManage: Boolean) -> Unit,
    viewModel: BlocklistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The chip counts, and the list itself, refetch on arrival, so a change made from a title's own
    // detail page — a removal there bumps nothing here directly, see BlocklistViewModel.setScreenVisible — shows without a poll.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    BlocklistScreen(
        state = state,
        showBack = showBack,
        itemsFor = viewModel::items,
        events = viewModel.events,
        shouldRefresh = viewModel::shouldRefresh,
        actions =
            BlocklistActions(
                onBack = onBack,
                onFilterChange = viewModel::setFilter,
                onSearchChange = viewModel::setSearch,
                onOpen = onOpen,
                onRemove = viewModel::remove,
                onRetry = viewModel::retry,
            ),
    )
}

@Composable
internal fun BlocklistDetailEntry(
    item: BlocklistItem,
    canManage: Boolean,
    onBack: () -> Unit,
    viewModel: BlocklistDetailViewModel =
        hiltViewModel<BlocklistDetailViewModel, BlocklistDetailViewModel.Factory>(
            creationCallback = { factory -> factory.create(item, canManage) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BlocklistDetailScreen(
        state = state,
        events = viewModel.events,
        actions = BlocklistDetailActions(onBack = onBack, onUnblock = viewModel::unblock),
    )
}
