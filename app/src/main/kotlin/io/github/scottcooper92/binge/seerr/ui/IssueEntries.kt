package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.ui.blocklist.BlocklistActions
import io.github.scottcooper92.binge.seerr.ui.blocklist.BlocklistScreen
import io.github.scottcooper92.binge.seerr.ui.blocklist.BlocklistViewModel
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailActions
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailScreen
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesActions
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesScreen
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesViewModel

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
            ),
    )
}

@Composable
internal fun IssueDetailEntry(
    issueId: Int,
    onBack: () -> Unit,
    viewModel: IssueDetailViewModel =
        hiltViewModel<IssueDetailViewModel, IssueDetailViewModel.Factory>(creationCallback = { factory -> factory.create(issueId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    IssueDetailScreen(
        state = state,
        events = viewModel.events,
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
    viewModel: BlocklistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The chip counts refetch on arrival, so a block elsewhere shows without a poll.
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
                onRemove = viewModel::remove,
            ),
    )
}
