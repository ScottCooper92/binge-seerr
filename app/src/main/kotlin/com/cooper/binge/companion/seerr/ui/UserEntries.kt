package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cooper.binge.companion.seerr.flags.LocalDiscoveryEnabled
import com.cooper.binge.companion.seerr.ui.users.UserAdmissionActions
import com.cooper.binge.companion.seerr.ui.users.UserDetailActions
import com.cooper.binge.companion.seerr.ui.users.UserDetailScreen
import com.cooper.binge.companion.seerr.ui.users.UserDetailViewModel
import com.cooper.binge.companion.seerr.ui.users.UsersActions
import com.cooper.binge.companion.seerr.ui.users.UsersScreen
import com.cooper.binge.companion.seerr.ui.users.UsersUiState
import com.cooper.binge.companion.seerr.ui.users.UsersViewModel
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

@Composable
internal fun UsersEntry(
    onBack: () -> Unit,
    showBack: Boolean,
    onOpen: (Int) -> Unit,
    viewModel: UsersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // The scope re-resolves on arrival, so a permission changed in the web client shows here.
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    UsersScreen(
        state = state,
        showBack = showBack,
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
                onRetryLoad = viewModel::retry,
                admission =
                    UserAdmissionActions(
                        onStart = viewModel.admission::start,
                        onCancel = viewModel.admission::cancel,
                        onBackToChoice = viewModel.admission::backToChoice,
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
internal fun UserDetailEntry(
    userId: Int,
    onBack: () -> Unit,
    showBack: Boolean,
    onOpenRequest: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWatchlist: (title: String) -> Unit,
    viewModel: UserDetailViewModel =
        hiltViewModel<UserDetailViewModel, UserDetailViewModel.Factory>(creationCallback = { factory -> factory.create(userId) }),
) {
    val watchlistTitle = stringResource(AdminR.string.user_watchlist)
    val discovery = LocalDiscoveryEnabled.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onDispose { viewModel.setScreenVisible(false) }
    }
    UserDetailScreen(
        state = state,
        requests = viewModel.requests,
        events = viewModel.events,
        showBack = showBack,
        actions =
            UserDetailActions(
                onBack = onBack,
                onRetry = viewModel::reload,
                onOpenRequest = { item -> onOpenRequest(item.id) },
                onOpenSettings = onOpenSettings,
                onDeleteUser = viewModel::deleteUser,
                onOpenWatchlist = { onOpenWatchlist(watchlistTitle) }.takeIf { discovery },
            ),
    )
}
