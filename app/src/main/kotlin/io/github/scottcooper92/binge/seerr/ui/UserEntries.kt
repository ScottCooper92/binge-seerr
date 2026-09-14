package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.ui.users.UserAdmissionActions
import io.github.scottcooper92.binge.seerr.ui.users.UserDetailActions
import io.github.scottcooper92.binge.seerr.ui.users.UserDetailScreen
import io.github.scottcooper92.binge.seerr.ui.users.UserDetailViewModel
import io.github.scottcooper92.binge.seerr.ui.users.UsersActions
import io.github.scottcooper92.binge.seerr.ui.users.UsersScreen
import io.github.scottcooper92.binge.seerr.ui.users.UsersUiState
import io.github.scottcooper92.binge.seerr.ui.users.UsersViewModel

@Composable
internal fun UsersEntry(
    onBack: () -> Unit,
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
internal fun UserDetailEntry(
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
