package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.ui.requests.EditRequestActions
import io.github.scottcooper92.binge.seerr.ui.requests.ManageMediaActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailScreen
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsScreen
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsViewModel

@Composable
internal fun RequestDetailEntry(
    requestId: Int,
    onBack: () -> Unit,
    onOpenRequest: (Int) -> Unit,
    onOpenUser: (Int) -> Unit,
    viewModel: RequestDetailViewModel =
        hiltViewModel<RequestDetailViewModel, RequestDetailViewModel.Factory>(creationCallback = { factory -> factory.create(requestId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RequestDetailScreen(
        state = state,
        events = viewModel.moderation.events,
        actions =
            RequestDetailActions(
                onBack = onBack,
                onRetry = viewModel::reload,
                onReportIssue = viewModel::reportIssue,
                onDismissReport = viewModel::dismissReport,
                onApprove = { viewModel.moderation.approve(requestId) },
                onRetryRequest = { viewModel.moderation.retry(requestId) },
                onDecline = { block ->
                    (state as? RequestDetailUiState.Ready)?.let { viewModel.moderation.decline(it.detail.item, block) }
                },
                onRemove = { block -> (state as? RequestDetailUiState.Ready)?.let { viewModel.moderation.remove(it.detail.item, block) } },
                onStartEdit = viewModel::startEdit,
                onOpenSibling = onOpenRequest,
                onOpenUser = onOpenUser,
                edit =
                    EditRequestActions(
                        onToggleSeason = viewModel.editor::toggleSeason,
                        onSelectServer = viewModel.editor::selectServer,
                        onSelectProfile = viewModel.editor::selectProfile,
                        onSelectRootFolder = viewModel.editor::selectRootFolder,
                        onToggleTag = viewModel.editor::toggleTag,
                        onSave = viewModel.editor::save,
                        onDismiss = viewModel.editor::cancel,
                    ),
                media =
                    ManageMediaActions(
                        onSetStatus = { mediaId, status, is4k ->
                            val seasons =
                                (state as? RequestDetailUiState.Ready)
                                    ?.detail
                                    ?.seasons
                                    ?.map { it.number }
                                    .orEmpty()
                            viewModel.moderation.setMediaStatus(requestId, mediaId, status, is4k, seasons)
                        },
                        onClearData = { mediaId -> viewModel.moderation.clearMedia(requestId, mediaId) },
                        onDeleteFiles = { mediaId, is4k -> viewModel.moderation.deleteMediaFiles(requestId, mediaId, is4k) },
                    ),
            ),
    )
}

@Composable
internal fun RequestsEntry(
    onBack: () -> Unit,
    showBack: Boolean,
    onOpen: (Int) -> Unit,
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The chip counts refetch on arrival, so a moderation elsewhere shows without a poll.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    RequestsScreen(
        state = state,
        showBack = showBack,
        requestsFor = viewModel::requests,
        events = viewModel.moderation.events,
        shouldRefresh = viewModel::shouldRefresh,
        actions =
            RequestsActions(
                onBack = onBack,
                onFilterChange = viewModel::setFilter,
                onSortChange = viewModel::setSort,
                onOpen = { item -> onOpen(item.id) },
                onOpenActions = viewModel::openActions,
                onDismissActions = viewModel::dismissActions,
                onApprove = viewModel.moderation::approve,
                onRetry = viewModel.moderation::retry,
                onDecline = viewModel.moderation::decline,
                onRemove = viewModel.moderation::remove,
            ),
    )
}
