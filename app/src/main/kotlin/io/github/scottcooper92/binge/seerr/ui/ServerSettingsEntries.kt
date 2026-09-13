package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.server.ApiKeyActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.DefaultPermissionsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerGeneralScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerGeneralViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerSettingsPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel
import io.github.scottcooper92.binge.seerr.ui.users.settings.PermissionsSettingsScreen

/** The server-settings entries of [SeerrNavHost]: one screen per page, each over its own editor. */
@Composable
internal fun ServerSettingsPageEntry(
    page: ServerSettingsPage,
    onBack: () -> Unit,
    onOpenPage: (ServerSettingsPage) -> Unit,
) {
    when (page) {
        ServerSettingsPage.General -> {
            val viewModel = hiltViewModel<ServerGeneralViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val extras by viewModel.extras.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val keyLabel = stringResource(R.string.server_settings_api_key)
            ServerGeneralScreen(
                state = state,
                extras = extras,
                events = viewModel.events,
                actions = viewModel.editorActions(onBack),
                keyActions =
                    ApiKeyActions(
                        onToggleReveal = viewModel::toggleReveal,
                        onCopy = { key -> context.copyToClipboard(keyLabel, key, sensitive = true) },
                        onRegenerate = viewModel::regenerateApiKey,
                    ),
                onOpenDefaultPermissions = { onOpenPage(ServerSettingsPage.DefaultPermissions) },
            )
        }
        ServerSettingsPage.DefaultPermissions -> {
            val viewModel = hiltViewModel<DefaultPermissionsViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            PermissionsSettingsScreen(
                state = state,
                events = viewModel.events,
                actions = viewModel.editorActions(onBack),
                onToggle = viewModel::toggle,
                titleRes = R.string.server_settings_default_permissions,
                leadRes = R.string.server_settings_default_permissions_lead,
            )
        }
    }
}

private fun <T> EditorViewModel<T>.editorActions(onBack: () -> Unit): EditorActions<T> =
    EditorActions(onBack = onBack, onRetry = ::reload, onEdit = ::edit, onSave = ::save)
