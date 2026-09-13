package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.SetupUiState
import io.github.scottcooper92.binge.seerr.ui.SetupViewModel
import io.github.scottcooper92.binge.seerr.ui.actions
import io.github.scottcooper92.binge.seerr.ui.hub.HubViewModel
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesUiState
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesViewModel
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsViewModel
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TvHubActions
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TvHubBoard
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesActions
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesBoard
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsBoard
import io.github.scottcooper92.binge.seerr.ui.tv.settings.TvSettingsBoard

/**
 * The connected television: the rail with a board per destination, each bound to the same ViewModel as
 * its phone screen, and the edit-connection form as a full-screen overlay above the rail.
 */
@Composable
internal fun TvConnectedShell() {
    var selected by rememberSaveable { mutableStateOf(TvDestination.Hub) }
    var editingConnection by rememberSaveable { mutableStateOf(false) }
    TvShellScaffold(
        selected = selected,
        onSelect = { selected = it },
        overlay = if (editingConnection) ({ TvEditConnectionOverlay(onDone = { editingConnection = false }) }) else null,
    ) { destination ->
        when (destination) {
            TvDestination.Hub ->
                TvHubEntry(
                    onOpenRequests = { selected = TvDestination.Requests },
                    onOpenIssues = { selected = TvDestination.Issues },
                    onReconnect = { editingConnection = true },
                )
            TvDestination.Requests -> TvRequestsEntry(onReconnect = { editingConnection = true })
            TvDestination.Issues -> TvIssuesEntry(onReconnect = { editingConnection = true })
            TvDestination.Settings -> TvSettingsEntry(onEditConnection = { editingConnection = true })
        }
    }
}

@Composable
private fun TvHubEntry(
    onOpenRequests: () -> Unit,
    onOpenIssues: () -> Unit,
    onReconnect: () -> Unit,
    viewModel: HubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The downloading poll and the auto-retry run only while the hub is on screen.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    TvHubBoard(
        state = state,
        actions =
            TvHubActions(
                onOpenRequests = onOpenRequests,
                onOpenIssues = onOpenIssues,
                onRetry = viewModel::recheck,
                onReconnect = onReconnect,
                onDisconnect = viewModel::disconnect,
            ),
    )
}

@Composable
private fun TvRequestsEntry(
    onReconnect: () -> Unit,
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    val ready = state as? RequestsUiState.Ready
    // The pager is collected here and handed down as a count and an accessor: the board never touches
    // LazyPagingItems, which do not progress under a Compose test rule.
    val lazyItems = ready?.let { viewModel.requests(it.filter).collectAsLazyPagingItems() }
    val version by viewModel.listVersion.collectAsStateWithLifecycle()
    LaunchedEffect(version, ready?.filter, lazyItems) {
        val filter = ready?.filter ?: return@LaunchedEffect
        if (viewModel.shouldRefresh(filter, version)) lazyItems?.refresh()
    }
    TvRequestsBoard(
        state = state,
        rows = lazyItems.toRows(),
        events = viewModel.moderation.events,
        actions =
            TvRequestsActions(
                onFilterChange = viewModel::setFilter,
                onOpenActions = viewModel::openActions,
                onDismissActions = viewModel::dismissActions,
                onApprove = viewModel.moderation::approve,
                onRetry = viewModel.moderation::retry,
                onDecline = viewModel.moderation::decline,
                onRemove = viewModel.moderation::remove,
                onRetryLoad = { lazyItems?.retry() },
                onReconnect = onReconnect,
            ),
    )
}

@Composable
private fun TvIssuesEntry(
    onReconnect: () -> Unit,
    viewModel: IssuesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    val ready = state as? IssuesUiState.Ready
    val lazyItems = ready?.let { viewModel.issues(it.filter).collectAsLazyPagingItems() }
    TvIssuesBoard(
        state = state,
        rows = lazyItems.toRows(),
        events = viewModel.events,
        actions =
            TvIssuesActions(
                onFilterChange = viewModel::setFilter,
                onOpenActions = viewModel::openActions,
                onDismissActions = viewModel::dismissActions,
                onResolve = viewModel::resolve,
                onReopen = viewModel::reopen,
                onDelete = viewModel::delete,
                onRetryLoad = { lazyItems?.retry() },
                onReconnect = onReconnect,
            ),
    )
}

@Composable
private fun TvSettingsEntry(
    onEditConnection: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Refetched on every arrival, so returning from Edit connection shows the new server.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    TvSettingsBoard(state = state, onEditConnection = onEditConnection, onDisconnect = viewModel::disconnect)
}

/** The setup form on the live connection, above the rail; leaves once new credentials are saved, or on Back. */
@Composable
private fun TvEditConnectionOverlay(
    onDone: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.beginEdit() }
    LaunchedEffect(state) { if (state is SetupUiState.Connected) onDone() }
    BackHandler(onBack = onDone)
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TvSetupScreen(state = state, actions = viewModel.actions())
    }
}

/** The pager's count, accessor and load states, in the form the boards take; empty while there is no pager. */
private fun <T : Any> LazyPagingItems<T>?.toRows(): TvPagedRows<T> {
    if (this == null) return TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Loading)
    return TvPagedRows(
        count = itemCount,
        at = { index -> this[index] },
        itemKey = itemKey { it.hashCode() },
        refresh = loadState.refresh.toPhase(),
        append = loadState.append.toPhase(),
    )
}

private fun LoadState.toPhase(): TvLoadPhase =
    when (this) {
        is LoadState.Loading -> TvLoadPhase.Loading
        is LoadState.Error -> TvLoadPhase.Failed(rejected = error.toSeerrError() == SeerrError.Unauthorized)
        is LoadState.NotLoading -> TvLoadPhase.Idle
    }
