package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.cooper.binge.companion.seerr.ui.bingeTitleHandOff
import com.cooper.binge.companion.seerr.ui.hub.ConnectionHealth
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.HubViewModel
import com.cooper.binge.companion.seerr.ui.hub.isProblem
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailUiState
import com.cooper.binge.companion.seerr.ui.requests.RequestDetailViewModel
import com.cooper.binge.companion.seerr.ui.requests.RequestFilter
import com.cooper.binge.companion.seerr.ui.requests.RequestsUiState
import com.cooper.binge.companion.seerr.ui.requests.RequestsViewModel
import com.cooper.binge.companion.seerr.ui.tv.hub.TvHubActions
import com.cooper.binge.companion.seerr.ui.tv.hub.TvHubBoard
import com.cooper.binge.companion.seerr.ui.tv.requests.RequestRowFilters
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestDetailScreen
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestEditActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsActions
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsGrid
import com.cooper.binge.companion.seerr.ui.tv.requests.TvRequestsRowsBoard

/**
 * Home: the server's problem when it cannot be reached or the session was rejected — the same panel, with the same
 * ways out, the hub always showed — and otherwise the requests. The hub's state is read first, so a server that is
 * not answering is said once, here, rather than as a failed load in every row.
 */
@Composable
internal fun TvHomeEntry(
    onReconnect: () -> Unit,
    onOpenRequest: (Int) -> Unit,
    onSeeAll: (RequestFilter) -> Unit,
    onSeeAllMine: () -> Unit,
    hubViewModel: HubViewModel = hiltViewModel(),
) {
    val hub by hubViewModel.uiState.collectAsStateWithLifecycle()
    // Kept across a re-probe: Checking is neither answer, and swapping Home for it would restart the retry backoff.
    var answering by remember { mutableStateOf(false) }
    answering = homeAnswering(hub, answering)
    if (answering) {
        TvRequestsEntry(
            onOpenRequest = onOpenRequest,
            onSeeAll = onSeeAll,
            accountId = (hub as? HubUiState.Ready)?.overview?.account?.id,
            onSeeAllMine = onSeeAllMine,
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
    onOpenRequest: (Int) -> Unit,
    onSeeAll: (RequestFilter) -> Unit,
    accountId: Int?,
    onSeeAllMine: () -> Unit,
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // On every resume, not only on entering composition: leaving the app and coming back keeps Home composed, and
    // TV has no pull to refresh, so this is what re-reads the counts and the rows the server changed meanwhile.
    LifecycleResumeEffect(viewModel) {
        viewModel.setScreenVisible(true)
        viewModel.refreshRowsOnReturn()
        onPauseOrDispose { viewModel.setScreenVisible(false) }
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
    val mine = rememberMyRequests(accountId, onSeeAllMine)
    TvRequestsRowsBoard(
        state = state,
        rowsFor = { rowsByFilter.getValue(it) },
        mine = mine,
        actions =
            TvRequestsActions(
                onOpenDetail = { item -> onOpenRequest(item.id) },
                onSeeAll = onSeeAll,
                onRetryLoad = { pagers.values.forEach { it.retry() } },
                onRetryScope = viewModel::retry,
            ),
    )
}

/** Every request behind one filter's row, as a paged grid above Home, on the same ViewModel as the board. */
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
 * A request's page, above Home exactly as the connection form is: the same
 * [RequestDetailViewModel] the phone's `RequestDetailEntry` binds. The shell gives each opening its own view-model
 * scope, so the view model is this request's and is gone when the page closes. Open in Binge checks whether Binge
 * would answer the link once per detail load — this surface has no browser to fall back to, so the button is hidden
 * rather than tried and abandoned. Open in the media server's TV app is offered the same way: only when that app is here.
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
    val onOpenInBinge = remember(context, detail) { detail?.let { context.bingeTitleHandOff(it.item.mediaType, it.item.tmdbId) } }
    val onOpenInMediaServer = remember(context, detail) { context.mediaServerHandOff(detail?.mediaServerItem) }
    TvRequestDetailScreen(
        state = state,
        events = viewModel.events,
        actions =
            TvRequestDetailActions(
                onBack = onDone,
                onRetry = viewModel::reload,
                onOpenInBinge = onOpenInBinge,
                onOpenInMediaServer = onOpenInMediaServer,
                onApprove = { viewModel.moderation.approve(requestId) },
                onRetryRequest = { viewModel.moderation.retry(requestId) },
                onDecline = { block -> detail?.let { viewModel.moderation.decline(it.item, block) } },
                onRemove = { block -> detail?.let { viewModel.moderation.remove(it.item, block) } },
                onBlock = { detail?.let { viewModel.moderation.blockTitle(it.item) } },
                onSetMediaStatus = { mediaId, status, is4k ->
                    viewModel.moderation.setMediaStatus(requestId, mediaId, status, is4k, detail?.seasons?.map { it.number }.orEmpty())
                },
                edit = TvRequestEditActions(onStart = viewModel::startEdit, controls = viewModel.editor),
                onManageOpened = viewModel::onManageOpened,
            ),
    )
}
