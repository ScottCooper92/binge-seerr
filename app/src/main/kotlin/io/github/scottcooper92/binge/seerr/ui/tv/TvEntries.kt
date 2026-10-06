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
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.telemetry.LocalAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.screenName
import io.github.scottcooper92.binge.seerr.ui.SetupViewModel
import io.github.scottcooper92.binge.seerr.ui.bingeAnswersTitleLink
import io.github.scottcooper92.binge.seerr.ui.hub.ConnectionHealth
import io.github.scottcooper92.binge.seerr.ui.hub.HubUiState
import io.github.scottcooper92.binge.seerr.ui.hub.HubViewModel
import io.github.scottcooper92.binge.seerr.ui.hub.isProblem
import io.github.scottcooper92.binge.seerr.ui.hub.openBingeOnPlayStore
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailUiState
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesUiState
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesViewModel
import io.github.scottcooper92.binge.seerr.ui.openTitleInBinge
import io.github.scottcooper92.binge.seerr.ui.rememberEnteredEditingGuard
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
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
import io.github.scottcooper92.binge.seerr.ui.tv.requests.RequestRowFilters
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestDetailActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestDetailScreen
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsActions
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsGrid
import io.github.scottcooper92.binge.seerr.ui.tv.requests.TvRequestsRowsBoard
import io.github.scottcooper92.binge.seerr.ui.tv.settings.TvSettingsBoard
import io.github.scottcooper92.binge.seerr.ui.tvActions

/**
 * The connected television: the rail with a board per destination, each bound to the same ViewModel as
 * its phone screen, and the edit-connection form as a full-screen overlay above the rail.
 */
@Composable
internal fun TvConnectedShell() {
    var selected by rememberSaveable { mutableStateOf(TvDestination.Hub) }
    val analytics = LocalAnalytics.current
    LaunchedEffect(selected) { analytics.screen(selected.screenName()) }
    var editingConnection by rememberSaveable { mutableStateOf(false) }
    // The requests board's open detail page, above the rail exactly as the connection form is. The two
    // overlays are mutually exclusive by construction — nothing opens one while the other is showing.
    var openRequestId by rememberSaveable { mutableStateOf<Int?>(null) }
    // The issues board's own read-only detail page, on the same footing.
    var openIssueId by rememberSaveable { mutableStateOf<Int?>(null) }
    // A see-all grid (a filter's name), above the rail. Unlike the others it stays up under a detail page opened
    // from one of its cards, so the page above and the grid below can both be showing.
    var seeAllRequests by rememberSaveable { mutableStateOf<String?>(null) }
    TvShellScaffold(
        selected = selected,
        onSelect = { selected = it },
        overlay =
            when {
                editingConnection ->
                    {
                        { TvEditConnectionOverlay(onDone = { editingConnection = false }) }
                    }
                openRequestId != null || seeAllRequests != null ->
                    {
                        {
                            seeAllRequests?.let { name ->
                                TvRequestsGridOverlay(
                                    filter = RequestFilter.valueOf(name),
                                    detailOpen = openRequestId != null,
                                    onOpenRequest = { openRequestId = it },
                                    onDone = { seeAllRequests = null },
                                    onReconnect = { editingConnection = true },
                                )
                            }
                            openRequestId?.let { TvRequestDetailOverlay(requestId = it, onDone = { openRequestId = null }) }
                        }
                    }
                openIssueId != null ->
                    {
                        { TvIssueDetailOverlay(issueId = requireNotNull(openIssueId), onDone = { openIssueId = null }) }
                    }
                else -> null
            },
    ) { destination ->
        when (destination) {
            // Home is what needs attention: the requests as rows over a backdrop, once the server answers.
            TvDestination.Hub ->
                TvHomeEntry(
                    onReconnect = { editingConnection = true },
                    openRequestId = openRequestId,
                    onOpenRequest = { openRequestId = it },
                    seeAllOpen = seeAllRequests != null,
                    onSeeAll = { seeAllRequests = it.name },
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

/**
 * Home: the server's problem when it cannot be reached or the session was rejected — the same panel, with the same
 * ways out, the hub always showed — and otherwise the requests. The hub's state is read first, so a server that is
 * not answering is said once, here, rather than as a failed load in every row.
 */
@Composable
private fun TvHomeEntry(
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
            onReconnect = onReconnect,
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
        is HubUiState.Ready -> if (hub.health == ConnectionHealth.Checking) previous else !hub.health.isProblem()
        is HubUiState.Error, HubUiState.Loading -> false
    }

@Composable
private fun TvHubEntry(
    onReconnect: () -> Unit,
    viewModel: HubViewModel,
) {
    val context = LocalContext.current
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
                onOpenRequests = {},
                onOpenIssues = {},
                onRetry = viewModel::recheck,
                onReconnect = onReconnect,
                onDisconnect = viewModel::disconnect,
                onOpenBingeListing = { context.openBingeOnPlayStore() },
            ),
    )
}

@Composable
private fun TvRequestsEntry(
    onReconnect: () -> Unit,
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
                onReconnect = onReconnect,
            ),
    )
}

/** Every request behind one filter's row, as a paged grid above the rail, on the same ViewModel as the board. */
@Composable
private fun TvRequestsGridOverlay(
    filter: RequestFilter,
    detailOpen: Boolean,
    onOpenRequest: (Int) -> Unit,
    onDone: () -> Unit,
    onReconnect: () -> Unit,
    viewModel: RequestsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? RequestsUiState.Ready
    val lazyItems = viewModel.requests(filter).collectAsLazyPagingItems()
    TvRequestsGrid(
        filter = filter,
        counts = ready?.counts,
        rows = lazyItems.toRows(ready?.refreshes?.get(filter)) { it.id },
        actingIds = ready?.actingIds.orEmpty(),
        detailOpen = detailOpen,
        onOpenDetail = { onOpenRequest(it.id) },
        onRetryLoad = { lazyItems.retry() },
        onReconnect = onReconnect,
        onBack = onDone,
    )
}

/**
 * A request's read-only page, above the rail exactly as the connection form is: the same
 * [RequestDetailViewModel] the phone's `RequestDetailEntry` binds, and one instance per request id: the lookup is
 * keyed by [requestId], because Hilt stores view models by class and key, so a lookup without a key would answer
 * every later request with the first one opened. Open in Binge checks whether Binge would answer the link once
 * per detail load — this surface has no browser to fall back to, so the button is hidden rather than tried
 * and abandoned.
 */
@Composable
private fun TvRequestDetailOverlay(
    requestId: Int,
    onDone: () -> Unit,
    viewModel: RequestDetailViewModel =
        hiltViewModel<RequestDetailViewModel, RequestDetailViewModel.Factory>(
            key = "request-detail-$requestId",
            creationCallback = { factory -> factory.create(requestId) },
        ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // A page opened before keeps its loaded state, so reopening it refreshes rather than showing what it last saw.
    LaunchedEffect(viewModel) { if (viewModel.uiState.value is RequestDetailUiState.Ready) viewModel.reload() }
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
        rows = lazyItems.toRows(ready?.refreshes?.get(ready.filter)) { it.id },
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
 * [IssueDetailViewModel] the phone's `IssueDetailEntry` binds, and one instance per issue id, keyed by
 * [issueId] for the same reason as the request page.
 */
@Composable
private fun TvIssueDetailOverlay(
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

@Composable
private fun TvSettingsEntry(
    onEditConnection: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    hubViewModel: HubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hub by hubViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Refetched on every arrival, so returning from Edit connection shows the new server.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    // The hub view model is shared with Home and is `Lazily`, so it re-reads whether Binge is installed and the
    // pending count only when told it is visible; without this, installing Binge from the Binge row stays stale.
    DisposableEffect(hubViewModel) {
        hubViewModel.setScreenVisible(true)
        onDispose { hubViewModel.setScreenVisible(false) }
    }
    // The jobs view model is only stood up once the row it feeds can actually appear — admin-only, same
    // gate as the row itself — so a non-admin viewer never pays for a `/settings/jobs` fetch they cannot use.
    if ((state as? SettingsUiState.Ready)?.config != null) {
        TvAdminSettingsEntry(
            state = state,
            onEditConnection = onEditConnection,
            viewModel = viewModel,
            hub = hub,
            onOpenBingeListing = { context.openBingeOnPlayStore() },
        )
    } else {
        TvSettingsBoard(
            state = state,
            onEditConnection = onEditConnection,
            onDisconnect = viewModel::disconnect,
            onToggleShareUsageData = viewModel::setShareUsageData,
            onToggleSendCrashReports = viewModel::setSendCrashReports,
            hub = hub,
            onOpenBingeListing = { context.openBingeOnPlayStore() },
        )
    }
}

@Composable
private fun TvAdminSettingsEntry(
    state: SettingsUiState,
    onEditConnection: () -> Unit,
    viewModel: SettingsViewModel,
    hub: HubUiState,
    onOpenBingeListing: () -> Unit,
    jobsViewModel: JobsViewModel = hiltViewModel(),
) {
    TvSettingsBoard(
        hub = hub,
        onOpenBingeListing = onOpenBingeListing,
        state = state,
        onEditConnection = onEditConnection,
        onDisconnect = viewModel::disconnect,
        onToggleShareUsageData = viewModel::setShareUsageData,
        onToggleSendCrashReports = viewModel::setSendCrashReports,
        // The Settings System group's job-row run action, reused rather than a second call to the same endpoint:
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
        TvSetupScreen(state = state, actions = viewModel.tvActions(), offerHandOff = true)
    }
}
