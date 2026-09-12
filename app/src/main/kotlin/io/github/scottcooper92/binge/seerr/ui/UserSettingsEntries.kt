package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import io.github.scottcooper92.binge.seerr.ui.users.settings.GeneralSettingsScreen
import io.github.scottcooper92.binge.seerr.ui.users.settings.GeneralSettingsViewModel
import io.github.scottcooper92.binge.seerr.ui.users.settings.LinkedAccountsActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.LinkedAccountsScreen
import io.github.scottcooper92.binge.seerr.ui.users.settings.LinkedAccountsViewModel
import io.github.scottcooper92.binge.seerr.ui.users.settings.NotificationsSettingsScreen
import io.github.scottcooper92.binge.seerr.ui.users.settings.NotificationsViewModel
import io.github.scottcooper92.binge.seerr.ui.users.settings.PasswordSettingsScreen
import io.github.scottcooper92.binge.seerr.ui.users.settings.PasswordViewModel
import io.github.scottcooper92.binge.seerr.ui.users.settings.PermissionsSettingsScreen
import io.github.scottcooper92.binge.seerr.ui.users.settings.PermissionsViewModel
import io.github.scottcooper92.binge.seerr.ui.users.settings.UserSettingsActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.UserSettingsPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.UserSettingsScreen
import io.github.scottcooper92.binge.seerr.ui.users.settings.UserSettingsViewModel

/** The user-settings entries of [SeerrNavHost]: the index, and one screen per page. */
@Composable
internal fun UserSettingsEntry(
    userId: Int,
    onBack: () -> Unit,
    onOpenPage: (UserSettingsPage) -> Unit,
    viewModel: UserSettingsViewModel =
        hiltViewModel<UserSettingsViewModel, UserSettingsViewModel.Factory>(creationCallback = { factory -> factory.create(userId) }),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    UserSettingsScreen(state = state, actions = UserSettingsActions(onBack = onBack, onRetry = viewModel::reload, onOpenPage = onOpenPage))
}

/** Each page has its own ViewModel; the route's page picks which, and the entry wires the editor calls the same way for all. */
@Composable
internal fun UserSettingsPageEntry(
    userId: Int,
    page: UserSettingsPage,
    onBack: () -> Unit,
) {
    when (page) {
        UserSettingsPage.General -> {
            val viewModel =
                hiltViewModel<GeneralSettingsViewModel, GeneralSettingsViewModel.Factory>(creationCallback = { factory ->
                    factory.create(userId)
                })
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            GeneralSettingsScreen(state = state, events = viewModel.events, actions = viewModel.editorActions(onBack))
        }
        UserSettingsPage.Password -> {
            val viewModel =
                hiltViewModel<PasswordViewModel, PasswordViewModel.Factory>(creationCallback = { factory ->
                    factory.create(userId)
                })
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            PasswordSettingsScreen(state = state, events = viewModel.events, actions = viewModel.editorActions(onBack))
        }
        UserSettingsPage.Notifications -> {
            val viewModel =
                hiltViewModel<NotificationsViewModel, NotificationsViewModel.Factory>(creationCallback = { factory ->
                    factory.create(userId)
                })
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            NotificationsSettingsScreen(state = state, events = viewModel.events, actions = viewModel.editorActions(onBack))
        }
        UserSettingsPage.Permissions -> {
            val viewModel =
                hiltViewModel<PermissionsViewModel, PermissionsViewModel.Factory>(creationCallback = { factory ->
                    factory.create(userId)
                })
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            PermissionsSettingsScreen(
                state = state,
                events = viewModel.events,
                actions = viewModel.editorActions(onBack),
                onToggle = viewModel::toggle,
            )
        }
        UserSettingsPage.LinkedAccounts -> {
            val viewModel =
                hiltViewModel<LinkedAccountsViewModel, LinkedAccountsViewModel.Factory>(creationCallback = { factory ->
                    factory.create(userId)
                })
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            LinkedAccountsScreen(
                state = state,
                events = viewModel.events,
                actions =
                    LinkedAccountsActions(
                        onBack = onBack,
                        onRetry = viewModel::reload,
                        onLinkPlex = viewModel::linkPlex,
                        onUnlinkPlex = viewModel::unlinkPlex,
                        onLinkQuickConnect = viewModel::linkQuickConnect,
                        onLinkMediaServer = viewModel::linkJellyfin,
                        onUnlinkMediaServer = viewModel::unlinkMediaServer,
                        onPlexLaunched = viewModel::plexLaunched,
                        onCancelLink = viewModel::cancelLink,
                    ),
            )
        }
    }
}

private fun <T> EditorViewModel<T>.editorActions(onBack: () -> Unit): EditorActions<T> =
    EditorActions(onBack = onBack, onRetry = ::reload, onEdit = ::edit, onSave = ::save)
