package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.ui.requests.EditRequestActions
import io.github.scottcooper92.binge.seerr.ui.requests.ManageMediaActions
import io.github.scottcooper92.binge.seerr.ui.requests.ModerationSnackbarEffect
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailScreen
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.requests.RequestManagementSheets
import io.github.scottcooper92.binge.seerr.ui.requests.RequestSheetPlaceholder
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsScreen
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsViewModel
import io.github.scottcooper92.binge.seerr.ui.requests.SheetDetailLoad
import io.github.scottcooper92.binge.seerr.ui.requests.SiblingSheet

@Composable
internal fun RequestDetailEntry(
    requestId: Int,
    onBack: () -> Unit,
    onOpenUser: (Int) -> Unit,
    viewModel: RequestDetailViewModel =
        hiltViewModel<RequestDetailViewModel, RequestDetailViewModel.Factory>(creationCallback = { factory -> factory.create(requestId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    RequestDetailScreen(
        state = state,
        events = viewModel.events,
        actions =
            viewModel.toActions(
                requestId = requestId,
                state = state,
                onBack = onBack,
                onOpenUser = onOpenUser,
                siblingSheet = { sheet -> SiblingRequestSheet(sheet, onOpenUser) },
            ),
    )
}

/**
 * Another request's actions over the request page, backed by that request's own view model. Keyed by
 * the request so each one opened is fetched once and kept while the page stays, and so opening the
 * same one again is instant. The view model is held while [SiblingSheet.open] is false too, because
 * a moderation's result arrives after the sheet has closed and its snackbar and the page's reload
 * ride that result.
 */
@Composable
private fun SiblingRequestSheet(
    sheet: SiblingSheet,
    onOpenUser: (Int) -> Unit,
) {
    val viewModel =
        hiltViewModel<RequestDetailViewModel, RequestDetailViewModel.Factory>(
            key = "request-detail-${sheet.requestId}",
            creationCallback = { factory -> factory.create(sheet.requestId) },
        )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current by rememberUpdatedState(sheet)
    ModerationSnackbarEffect(viewModel.events, sheet.snackbarHostState)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            current.onChanged()
            if (event.removesTheRequest) current.onDismiss()
        }
    }
    val page = state
    if (page is RequestDetailUiState.Ready || sheet.preview != null) {
        RequestManagementSheets(
            state = page as? RequestDetailUiState.Ready,
            actions =
                viewModel.toActions(
                    requestId = sheet.requestId,
                    state = page,
                    onBack = sheet.onDismiss,
                    onOpenUser = onOpenUser,
                    siblingSheet = {},
                    fallbackItem = sheet.preview?.item,
                ),
            acting = sheet.open,
            onDismissActing = sheet.onDismiss,
            preview = sheet.preview,
            detailLoad = page.sheetDetailLoad(),
            onRetryDetail = viewModel::reload,
        )
    } else if (sheet.open) {
        RequestSheetPlaceholder(state = page, onRetry = viewModel::reload, onDismiss = sheet.onDismiss)
    }
}

/** Every callback a request's screen or sheet makes, bound to [this] view model's request. */
private fun RequestDetailViewModel.toActions(
    requestId: Int,
    state: RequestDetailUiState,
    onBack: () -> Unit,
    onOpenUser: (Int) -> Unit,
    siblingSheet: @Composable (SiblingSheet) -> Unit,
    fallbackItem: RequestItem? = null,
): RequestDetailActions =
    RequestDetailActions(
        onBack = onBack,
        onRetry = ::reload,
        onReportIssue = ::reportIssue,
        onDismissReport = ::dismissReport,
        onApprove = { moderation.approve(requestId) },
        onRetryRequest = { moderation.retry(requestId) },
        onDecline = { block ->
            ((state as? RequestDetailUiState.Ready)?.detail?.item ?: fallbackItem)?.let { moderation.decline(it, block) }
        },
        onRemove = { block ->
            ((state as? RequestDetailUiState.Ready)?.detail?.item ?: fallbackItem)?.let { moderation.remove(it, block) }
        },
        onStartEdit = ::startEdit,
        siblingSheet = siblingSheet,
        onOpenUser = onOpenUser,
        edit =
            EditRequestActions(
                onToggleSeason = editor::toggleSeason,
                onSelectAllSeasons = editor::selectAllSeasons,
                onSelectServer = editor::selectServer,
                onSelectProfile = editor::selectProfile,
                onSelectRootFolder = editor::selectRootFolder,
                onToggleTag = editor::toggleTag,
                onSave = editor::save,
                onDismiss = editor::cancel,
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
                    moderation.setMediaStatus(requestId, mediaId, status, is4k, seasons)
                },
                onClearData = { mediaId -> moderation.clearMedia(requestId, mediaId) },
                onDeleteFiles = { mediaId, is4k -> moderation.deleteMediaFiles(requestId, mediaId, is4k) },
            ),
    )

@Composable
internal fun RequestsEntry(
    onBack: () -> Unit,
    showBack: Boolean,
    onOpen: (Int) -> Unit,
    onOpenUser: (Int) -> Unit,
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
        shouldRefresh = viewModel::shouldRefresh,
        actions =
            RequestsActions(
                onBack = onBack,
                onFilterChange = viewModel::setFilter,
                onSortChange = viewModel::setSort,
                onOpen = { item -> onOpen(item.id) },
                onRetryLoad = viewModel::retry,
                onRefreshCounts = viewModel::refreshCounts,
                onChanged = viewModel::listChanged,
                detailSheet = { sheet -> SiblingRequestSheet(sheet, onOpenUser) },
            ),
    )
}

/** What a sheet opened from a preview says about the detail still behind it. */
internal fun RequestDetailUiState.sheetDetailLoad(): SheetDetailLoad =
    when (this) {
        is RequestDetailUiState.Ready -> SheetDetailLoad.Loaded
        RequestDetailUiState.Loading -> SheetDetailLoad.Loading
        is RequestDetailUiState.Seeded -> error?.let { SheetDetailLoad.Failed(it) } ?: SheetDetailLoad.Loading
        is RequestDetailUiState.Error -> SheetDetailLoad.Failed(error)
    }
