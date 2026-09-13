package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.blocklist.BlocklistActions
import io.github.scottcooper92.binge.seerr.ui.blocklist.BlocklistScreen
import io.github.scottcooper92.binge.seerr.ui.blocklist.BlocklistViewModel
import io.github.scottcooper92.binge.seerr.ui.hub.HubActions
import io.github.scottcooper92.binge.seerr.ui.hub.HubScreen
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection
import io.github.scottcooper92.binge.seerr.ui.hub.HubViewModel
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailActions
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailScreen
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesActions
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesScreen
import io.github.scottcooper92.binge.seerr.ui.issues.IssuesViewModel
import io.github.scottcooper92.binge.seerr.ui.requests.EditRequestActions
import io.github.scottcooper92.binge.seerr.ui.requests.ListRefresh
import io.github.scottcooper92.binge.seerr.ui.requests.ManageMediaActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailScreen
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailUiState
import io.github.scottcooper92.binge.seerr.ui.requests.RequestDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsActions
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsScreen
import io.github.scottcooper92.binge.seerr.ui.requests.RequestsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsActions
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsScreen
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsViewModel
import io.github.scottcooper92.binge.seerr.ui.state.EmptyScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.users.UserAdmissionActions
import io.github.scottcooper92.binge.seerr.ui.users.UserDetailActions
import io.github.scottcooper92.binge.seerr.ui.users.UserDetailScreen
import io.github.scottcooper92.binge.seerr.ui.users.UserDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.users.UsersActions
import io.github.scottcooper92.binge.seerr.ui.users.UsersScreen
import io.github.scottcooper92.binge.seerr.ui.users.UsersUiState
import io.github.scottcooper92.binge.seerr.ui.users.UsersViewModel

/**
 * The one [NavDisplay]. Every screen is an entry here; the ViewModel-store decorator gives each
 * entry its own store, so a screen's ViewModel lives and dies with its place on the stack rather
 * than with the Activity.
 */
@Composable
fun SeerrNavHost(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<HomeRoute> {
                    HomeEntry(
                        onOpenAccount = { id -> backStack.add(UserDetailRoute(id)) },
                        onOpenSection = { section ->
                            backStack.add(
                                when (section) {
                                    HubSection.Requests -> RequestsRoute
                                    HubSection.Issues -> IssuesRoute
                                    HubSection.Users -> UsersRoute
                                    HubSection.Blocklist -> BlocklistRoute
                                    HubSection.Settings -> SettingsRoute
                                    else -> SectionRoute(section)
                                },
                            )
                        },
                    )
                }
                entry<SetupRoute> { SetupEntry() }
                entry<RequestsRoute> {
                    RequestsEntry(onBack = { backStack.removeLastOrNull() }, onOpen = { id -> backStack.add(RequestDetailRoute(id)) })
                }
                entry<RequestDetailRoute> { route -> RequestDetailEntry(route.requestId, onBack = { backStack.removeLastOrNull() }) }
                entry<IssuesRoute> {
                    IssuesEntry(onBack = { backStack.removeLastOrNull() }, onOpen = { id -> backStack.add(IssueDetailRoute(id)) })
                }
                entry<IssueDetailRoute> { route -> IssueDetailEntry(route.issueId, onBack = { backStack.removeLastOrNull() }) }
                entry<BlocklistRoute> { BlocklistEntry(onBack = { backStack.removeLastOrNull() }) }
                entry<UsersRoute> {
                    UsersEntry(onBack = { backStack.removeLastOrNull() }, onOpen = { id -> backStack.add(UserDetailRoute(id)) })
                }
                entry<UserDetailRoute> { route ->
                    UserDetailEntry(
                        route.userId,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenRequest = { id -> backStack.add(RequestDetailRoute(id)) },
                        onOpenSettings = { backStack.add(UserSettingsRoute(route.userId)) },
                    )
                }
                entry<UserSettingsRoute> { route ->
                    UserSettingsEntry(
                        route.userId,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenPage = { page -> backStack.add(UserSettingsPageRoute(route.userId, page)) },
                    )
                }
                entry<UserSettingsPageRoute> { route ->
                    UserSettingsPageEntry(route.userId, route.page, onBack = { backStack.removeLastOrNull() })
                }
                entry<SettingsRoute> {
                    SettingsEntry(onBack = { backStack.removeLastOrNull() }, onEditConnection = { backStack.add(EditConnectionRoute) })
                }
                entry<EditConnectionRoute> { EditConnectionEntry(onDone = { backStack.removeLastOrNull() }) }
                entry<SectionRoute> { route ->
                    EmptyScreen(title = stringResource(route.section.titleRes), message = stringResource(R.string.section_coming_soon))
                }
            },
    )
}

/** The home swaps between setup and the hub on the saved credentials, so neither has to know the other. */
@Composable
private fun HomeEntry(
    onOpenSection: (HubSection) -> Unit,
    onOpenAccount: (Int) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val connected by viewModel.isConnected.collectAsStateWithLifecycle()
    when (connected) {
        null -> LoadingScreen()
        false -> SetupEntry()
        true -> HubEntry(onOpenSection, onOpenAccount)
    }
}

@Composable
private fun HubEntry(
    onOpenSection: (HubSection) -> Unit,
    onOpenAccount: (Int) -> Unit,
    viewModel: HubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The downloading poll and the auto-retry run only while the hub is on screen.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    HubScreen(
        state = state,
        actions =
            HubActions(
                onOpenSection = onOpenSection,
                onOpenAccount = onOpenAccount,
                onRetry = viewModel::recheck,
                onDisconnect = viewModel::disconnect,
            ),
    )
}

@Composable
private fun RequestDetailEntry(
    requestId: Int,
    onBack: () -> Unit,
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
                        onSetStatus = { mediaId, status, is4k -> viewModel.moderation.setMediaStatus(requestId, mediaId, status, is4k) },
                        onClearData = { mediaId -> viewModel.moderation.clearMedia(requestId, mediaId) },
                        onDeleteFiles = { mediaId, is4k -> viewModel.moderation.deleteMediaFiles(requestId, mediaId, is4k) },
                    ),
            ),
    )
}

@Composable
private fun RequestsEntry(
    onBack: () -> Unit,
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
        requestsFor = viewModel::requests,
        events = viewModel.moderation.events,
        refresh = ListRefresh(viewModel.listVersion, viewModel::shouldRefresh),
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

@Composable
private fun IssuesEntry(
    onBack: () -> Unit,
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
private fun IssueDetailEntry(
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
private fun BlocklistEntry(
    onBack: () -> Unit,
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
        items = viewModel.items,
        events = viewModel.events,
        actions =
            BlocklistActions(
                onBack = onBack,
                onFilterChange = viewModel::setFilter,
                onSearchChange = viewModel::setSearch,
                onRemove = viewModel::remove,
            ),
    )
}

@Composable
private fun UsersEntry(
    onBack: () -> Unit,
    onOpen: (Int) -> Unit,
    viewModel: UsersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UsersScreen(
        state = state,
        users = viewModel.users,
        events = viewModel.events,
        actions =
            UsersActions(
                onBack = onBack,
                onSortChange = viewModel::setSort,
                onOpen = { item -> onOpen(item.id) },
                onToggleSelected = { item -> viewModel.toggleSelected(item.id) },
                onClearSelection = viewModel::clearSelection,
                onStartBulkEdit = viewModel::startBulkEdit,
                onTogglePermission = viewModel::togglePermission,
                onApplyBulkEdit = viewModel::applyBulkEdit,
                onCancelBulkEdit = viewModel::cancelBulkEdit,
                admission =
                    UserAdmissionActions(
                        onStart = viewModel.admission::start,
                        onCancel = viewModel.admission::cancel,
                        onStartCreate = {
                            viewModel.admission.startCreate((state as? UsersUiState.Ready)?.canGeneratePassword == true)
                        },
                        onEditDraft = viewModel.admission::editDraft,
                        onCreate = viewModel.admission::create,
                        onStartImport = viewModel.admission::startImport,
                        onToggleCandidate = viewModel.admission::toggleCandidate,
                        onSelectAllCandidates = viewModel.admission::selectAllCandidates,
                        onImport = viewModel.admission::import,
                    ),
            ),
    )
}

@Composable
private fun UserDetailEntry(
    userId: Int,
    onBack: () -> Unit,
    onOpenRequest: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: UserDetailViewModel =
        hiltViewModel<UserDetailViewModel, UserDetailViewModel.Factory>(creationCallback = { factory -> factory.create(userId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserDetailScreen(
        state = state,
        requests = viewModel.requests,
        events = viewModel.events,
        actions =
            UserDetailActions(
                onBack = onBack,
                onRetry = viewModel::reload,
                onOpenRequest = { item -> onOpenRequest(item.id) },
                onOpenSettings = onOpenSettings,
                onDeleteUser = viewModel::deleteUser,
            ),
    )
}

@Composable
private fun SettingsEntry(
    onBack: () -> Unit,
    onEditConnection: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Refetched on every arrival, so returning from Edit connection shows the new server.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    SettingsScreen(
        state = state,
        actions =
            SettingsActions(
                onBack = onBack,
                onEditConnection = onEditConnection,
                // The home swaps to setup on the credentials clearing; leaving Settings is what lets it show.
                onDisconnect = {
                    viewModel.disconnect()
                    onBack()
                },
            ),
    )
}

/**
 * The setup form on the live connection, prefilled with its address. The connection stays in
 * place until new credentials are saved, so a rejected edit changes nothing; success pops.
 */
@Composable
private fun EditConnectionEntry(
    onDone: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.beginEdit() }
    LaunchedEffect(state) { if (state is SetupUiState.Connected) onDone() }
    SetupScreen(
        state = state,
        actions = viewModel.actions(),
        title = stringResource(R.string.settings_edit_connection),
        onBack = onDone,
    )
}

@Composable
private fun SetupEntry(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SetupScreen(state = state, actions = viewModel.actions())
}

private fun SetupViewModel.actions(): SetupActions =
    SetupActions(
        onEditAddress = ::editAddress,
        onInspect = ::inspect,
        onChangeServer = ::changeServer,
        onEditForm = ::editForm,
        onConnect = ::connect,
        onPlexLaunched = ::plexLaunched,
        onCancelLink = ::cancelLink,
        onRequestPasswordReset = ::requestPasswordReset,
    )
