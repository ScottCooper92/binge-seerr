package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.scottcooper92.binge.seerr.ui.bingeAnswersTitleLink
import io.github.scottcooper92.binge.seerr.ui.hub.ConnectionHealth
import io.github.scottcooper92.binge.seerr.ui.hub.HubUiState
import io.github.scottcooper92.binge.seerr.ui.hub.HubViewModel
import io.github.scottcooper92.binge.seerr.ui.hub.isProblem
import io.github.scottcooper92.binge.seerr.ui.openTitleInBinge
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsViewModel
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TvHubActions
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TvHubBoard
import io.github.scottcooper92.binge.seerr.ui.tv.requests.RequestRowFilters
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestDetailActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestDetailScreen
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsGrid
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsRowsBoard

/**
 * Home: the server's problem when it cannot be reached or the session was rejected — the same panel, with the same
 * ways out, the hub always showed — and otherwise the requests. The hub's state is read first, so a server that is
 * not answering is said once, here, rather than as a failed load in every row.
 */
@Composable
internal fun TvHomeEntry(
    onReconnect: () -> Unit,
    openRequestId: Int?,
    onOpenRequest: (Int) -> Unit,
    seeAllOpen: Boolean,
    onSeeAll: (RequestFilter) -> Unit,
    hubViewModel: HubViewModel = hiltViewModel(),
) {
    val hub by hubViewModel.uiState.collectAsStateWithLifecycle()
    // Kept across a re-probe: Checking is neither answer, and swapping Home for it would restart the retry backoff.
    var answering by remember { mutableStateOf(false) }
    answering = homeAnswering(hub, answering)
    if (answering) {
        TvRequestsEntry(
            openRequestId = openRequestId,
            onOpenRequest = onOpenRequest,
            seeAllOpen = seeAllOpen,
            onSeeAll = onSeeAll,
        )
    } else {
        TvHubEntry(onReconnect = onReconnect, viewModel = hubViewModel)
    }
}

/**
 * Whether Home shows the requests rather than the server's problem. A re-probe's [ConnectionHealth.Checking] is not
 * an answer, so it keeps [previous]: the problem page stays composed through it and the auto-retry's backoff is not
 * restarted by the visibility change a swap would cause.
 */
internal fun homeAnswering(
    hub: HubUiState,
    previous: Boolean,
): Boolean =
    when (hub) {
        is HubUiState.Ready ->
            when (hub.health) {
                ConnectionHealth.Checking -> previous
                // Not an answer either: the app goes to sign-in (#810), or the hub re-reads and finds the session fine.
                ConnectionHealth.Unauthorized -> false
                else -> !hub.health.isProblem()
            }
        is HubUiState.Error, HubUiState.Loading -> false
    }

@Composable
private fun TvHubEntry(
    onReconnect: () -> Unit,
    viewModel: HubViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The problem page needs the auto-retry and nothing of the dashboard's, so no downloads poll and no counts (#827).
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true, dashboard = false)
        onDispose { viewModel.setScreenVisible(false) }
    }
    TvHubBoard(
        state = state,
        actions =
            TvHubActions(onRetry = viewModel::recheck, onReconnect = onReconnect, onDisconnect = viewModel::disconnect),
    )
}

@Composable
private fun TvRequestsEntry(
    openRequestId: Int?,
    onOpenRequest: (Int) -> Unit,
    seeAllOpen: Boolean,
    onSeeAll: (RequestFilter) -> Unit,
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    val ready = state as? RequestsUiState.Ready
    // One pager per filter, each handed down as a count and an accessor: the board never touches LazyPagingItems,
    // which do not progress under a Compose test rule.
    val pagers = RequestRowFilters.associateWith { viewModel.requests(it).collectAsLazyPagingItems() }
    RequestRowFilters.forEach { filter ->
        LaunchedEffect(ready?.listVersion) {
            if (ready != null && viewModel.shouldRefresh(filter, ready.listVersion)) pagers.getValue(filter).refresh()
        }
    }
    val rowsByFilter = RequestRowFilters.associateWith { pagers.getValue(it).toRows(ready?.refreshes?.get(it)) { item -> item.id } }
    TvRequestsRowsBoard(
        state = state,
        rowsFor = { rowsByFilter.getValue(it) },
        openRequestId = openRequestId,
        seeAllOpen = seeAllOpen,
        actions =
            TvRequestsActions(
                onOpenDetail = { item -> onOpenRequest(item.id) },
                onSeeAll = onSeeAll,
                onRetryLoad = { pagers.values.forEach { it.retry() } },
                onRetryScope = viewModel::retry,
            ),
    )
}

/** Every request behind one filter's row, as a paged grid above the rail, on the same ViewModel as the board. */
@Composable
internal fun TvRequestsGridOverlay(
    filter: RequestFilter,
    detailOpen: Boolean,
    onOpenRequest: (Int) -> Unit,
    onDone: () -> Unit,
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? RequestsUiState.Ready
    val lazyItems = viewModel.requests(filter).collectAsLazyPagingItems()
    TvRequestsGrid(
        filter = filter,
        counts = ready?.counts,
        rows = lazyItems.toRows(ready?.refreshes?.get(filter)) { it.id },
        detailOpen = detailOpen,
        now = ready?.now ?: System.currentTimeMillis(),
        onOpenDetail = { onOpenRequest(it.id) },
        onRetryLoad = { lazyItems.retry() },
        onBack = onDone,
    )
}

/**
 * A request's page, above the rail exactly as the connection form is: the same
 * [RequestDetailViewModel] the phone's `RequestDetailEntry` binds. The shell gives each opening its own view-model
 * scope, so the view model is this request's and is gone when the page closes. Open in Binge checks whether Binge
 * would answer the link once per detail load — this surface has no browser to fall back to, so the button is hidden
 * rather than tried and abandoned.
 */
@Composable
internal fun TvRequestDetailOverlay(
    requestId: Int,
    onDone: () -> Unit,
    viewModel: RequestDetailViewModel =
        hiltViewModel<RequestDetailViewModel, RequestDetailViewModel.Factory>(creationCallback = { factory -> factory.create(requestId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    val context = LocalContext.current
    val detail = (state as? RequestDetailUiState.Ready)?.detail
    val onOpenInBinge =
        remember(context, detail) {
            detail?.let { ready ->
                { context.openTitleInBinge(ready.item.mediaType, ready.item.tmdbId) }
                    .takeIf { context.bingeAnswersTitleLink(ready.item.mediaType, ready.item.tmdbId) }
            }
        }
    TvRequestDetailScreen(
        state = state,
        events = viewModel.events,
        actions =
            TvRequestDetailActions(
                onBack = onDone,
                onRetry = viewModel::reload,
                onOpenInBinge = onOpenInBinge,
                onApprove = { viewModel.moderation.approve(requestId) },
                onRetryRequest = { viewModel.moderation.retry(requestId) },
                onDecline = { block -> detail?.let { viewModel.moderation.decline(it.item, block) } },
                onRemove = { block -> detail?.let { viewModel.moderation.remove(it.item, block) } },
                onBlock = { detail?.let { viewModel.moderation.blockTitle(it.item) } },
                onSetMediaStatus = { mediaId, status, is4k ->
                    viewModel.moderation.setMediaStatus(requestId, mediaId, status, is4k, detail?.seasons?.map { it.number }.orEmpty())
                },
                onReportIssue = viewModel::reportIssue,
                onDismissReport = viewModel::dismissReport,
            ),
    )
}
