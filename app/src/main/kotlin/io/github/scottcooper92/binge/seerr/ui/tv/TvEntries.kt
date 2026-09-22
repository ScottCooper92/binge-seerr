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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.material3.MaterialTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.ui.SetupViewModel
import io.github.scottcooper92.binge.seerr.ui.bingeAnswersTitleLink
import io.github.scottcooper92.binge.seerr.ui.hub.HubViewModel
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesUiState
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesViewModel
import io.github.scottcooper92.binge.seerr.ui.openTitleInBinge
import io.github.scottcooper92.binge.seerr.ui.rememberEnteredEditingGuard
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsUiState
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.JobsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.MEDIA_SERVER_SCAN_JOB_ID
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TvHubActions
import io.github.scottcooper92.binge.seerr.ui.tv.hub.TvHubBoard
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssueDetailActions
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssueDetailScreen
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesActions
import io.github.scottcooper92.binge.seerr.ui.tv.issues.TvIssuesBoard
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestDetailActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestDetailScreen
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsBoard
import io.github.scottcooper92.binge.seerr.ui.tv.settings.TvSettingsBoard
import io.github.scottcooper92.binge.seerr.ui.tvActions

/**
 * The connected television: the rail with a board per destination, each bound to the same ViewModel as
 * its phone screen, and the edit-connection form as a full-screen overlay above the rail.
 */
@Composable
internal fun TvConnectedShell() {
    var selected by rememberSaveable { mutableStateOf(TvDestination.Hub) }
    var editingConnection by rememberSaveable { mutableStateOf(false) }
    // The requests board's open detail page, above the rail exactly as the connection form is. The two
    // overlays are mutually exclusive by construction — nothing opens one while the other is showing.
    var openRequestId by rememberSaveable { mutableStateOf<Int?>(null) }
    // The issues board's own read-only detail page, on the same footing.
    var openIssueId by rememberSaveable { mutableStateOf<Int?>(null) }
    TvShellScaffold(
        selected = selected,
        onSelect = { selected = it },
        overlay =
            when {
                editingConnection ->
                    {
                        { TvEditConnectionOverlay(onDone = { editingConnection = false }) }
                    }
                openRequestId != null ->
                    {
                        { TvRequestDetailOverlay(requestId = requireNotNull(openRequestId), onDone = { openRequestId = null }) }
                    }
                openIssueId != null ->
                    {
                        { TvIssueDetailOverlay(issueId = requireNotNull(openIssueId), onDone = { openIssueId = null }) }
                    }
                else -> null
            },
    ) { destination ->
        when (destination) {
            TvDestination.Hub ->
                TvHubEntry(
                    onOpenRequests = { selected = TvDestination.Requests },
                    onOpenIssues = { selected = TvDestination.Issues },
                    onReconnect = { editingConnection = true },
                )
            TvDestination.Requests ->
                TvRequestsEntry(
                    onReconnect = { editingConnection = true },
                    openRequestId = openRequestId,
                    onOpenRequest = { openRequestId = it },
                )
            TvDestination.Issues ->
                TvIssuesEntry(
                    onReconnect = { editingConnection = true },
                    openIssueId = openIssueId,
                    onOpenIssue = { openIssueId = it },
                )
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
    openRequestId: Int?,
    onOpenRequest: (Int) -> Unit,
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
    LaunchedEffect(ready?.listVersion, ready?.filter, lazyItems) {
        val filter = ready?.filter ?: return@LaunchedEffect
        if (viewModel.shouldRefresh(filter, ready.listVersion)) lazyItems?.refresh()
    }
    TvRequestsBoard(
        state = state,
        rows = lazyItems.toRows { it.id },
        openRequestId = openRequestId,
        actions =
            TvRequestsActions(
                onFilterChange = viewModel::setFilter,
                onSortChange = viewModel::setSort,
                onOpenDetail = { item -> onOpenRequest(item.id) },
                onRetryLoad = { lazyItems?.retry() },
                onReconnect = onReconnect,
            ),
    )
}

/**
 * A request's read-only page, above the rail exactly as the connection form is: the same
 * [RequestDetailViewModel] the phone's `RequestDetailEntry` binds, and a fresh instance per request id since
 * [requestId] rides Hilt's `creationCallback`. Open in Binge checks whether Binge would answer the link once
 * per detail load — this surface has no browser to fall back to, so the button is hidden rather than tried
 * and abandoned.
 */
@Composable
private fun TvRequestDetailOverlay(
    requestId: Int,
    onDone: () -> Unit,
    viewModel: RequestDetailViewModel =
        hiltViewModel<RequestDetailViewModel, RequestDetailViewModel.Factory>(creationCallback = { factory -> factory.create(requestId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
        events = viewModel.moderation.events,
        actions =
            TvRequestDetailActions(
                onBack = onDone,
                onRetry = viewModel::reload,
                onOpenInBinge = onOpenInBinge,
                onApprove = { viewModel.moderation.approve(requestId) },
                onRetryRequest = { viewModel.moderation.retry(requestId) },
                onDecline = { block -> detail?.let { viewModel.moderation.decline(it.item, block) } },
                onRemove = { block -> detail?.let { viewModel.moderation.remove(it.item, block) } },
            ),
    )
}

@Composable
private fun TvIssuesEntry(
    onReconnect: () -> Unit,
    openIssueId: Int?,
    onOpenIssue: (Int) -> Unit,
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
        rows = lazyItems.toRows { it.id },
        events = viewModel.events,
        openIssueId = openIssueId,
        actions =
            TvIssuesActions(
                onFilterChange = viewModel::setFilter,
                onSortChange = viewModel::setSort,
                onOpenActions = viewModel::openActions,
                onDismissActions = viewModel::dismissActions,
                onOpenDetail = { item -> onOpenIssue(item.id) },
                onResolve = viewModel::resolve,
                onReopen = viewModel::reopen,
                onDelete = viewModel::delete,
                onRetryLoad = { lazyItems?.retry() },
                onReconnect = onReconnect,
            ),
    )
}

/**
 * An issue's read-only page, above the rail exactly as the connection form is: the same
 * [IssueDetailViewModel] the phone's `IssueDetailEntry` binds, and a fresh instance per issue id since
 * [issueId] rides Hilt's `creationCallback`.
 */
@Composable
private fun TvIssueDetailOverlay(
    issueId: Int,
    onDone: () -> Unit,
    viewModel: IssueDetailViewModel =
        hiltViewModel<IssueDetailViewModel, IssueDetailViewModel.Factory>(creationCallback = { factory -> factory.create(issueId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TvIssueDetailScreen(
        state = state,
        actions = TvIssueDetailActions(onBack = onDone, onRetry = viewModel::reload),
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
    // The jobs view model is only stood up once the row it feeds can actually appear — admin-only, same
    // gate as the row itself — so a non-admin viewer never pays for a `/settings/jobs` fetch they cannot use.
    if ((state as? SettingsUiState.Ready)?.config != null) {
        TvAdminSettingsEntry(state = state, onEditConnection = onEditConnection, viewModel = viewModel)
    } else {
        TvSettingsBoard(
            state = state,
            onEditConnection = onEditConnection,
            onDisconnect = viewModel::disconnect,
            onToggleShareUsageData = viewModel::setShareUsageData,
            onToggleSendCrashReports = viewModel::setSendCrashReports,
        )
    }
}

@Composable
private fun TvAdminSettingsEntry(
    state: SettingsUiState,
    onEditConnection: () -> Unit,
    viewModel: SettingsViewModel,
    jobsViewModel: JobsViewModel = hiltViewModel(),
) {
    TvSettingsBoard(
        state = state,
        onEditConnection = onEditConnection,
        onDisconnect = viewModel::disconnect,
        onToggleShareUsageData = viewModel::setShareUsageData,
        onToggleSendCrashReports = viewModel::setSendCrashReports,
        // The phone Jobs page's own run action, reused rather than a second call to the same endpoint:
        // this board has no jobs list of its own, so the notice is what tells the admin it started.
        // `runWhenReady`, not `run`: this view model's own load races the row becoming visible, so an
        // early tap has to wait it out (and retry once from a failed one) rather than being dropped.
        onStartLibraryScan = { jobsViewModel.runWhenReady(MEDIA_SERVER_SCAN_JOB_ID, R.string.tv_settings_scan_started) },
        libraryScanEvents = jobsViewModel.events,
    )
}

/** The setup form on the live connection, above the rail; leaves once new credentials are saved, or on Back. */
@Composable
private fun TvEditConnectionOverlay(
    onDone: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.beginEdit() }
    rememberEnteredEditingGuard(state, onDone)
    BackHandler(onBack = onDone)
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TvSetupScreen(state = state, actions = viewModel.tvActions())
    }
}

/** The pager's count, accessor and load states, in the form the boards take; empty while there is no pager. */
private fun <T : Any> LazyPagingItems<T>?.toRows(keyOf: (T) -> Any): TvPagedRows<T> {
    if (this == null) return TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Loading)
    return TvPagedRows(
        count = itemCount,
        at = { index -> this[index] },
        itemKey = itemKey(keyOf),
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
