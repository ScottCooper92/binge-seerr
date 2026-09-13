package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.settings.server.ApiKeyActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.DefaultPermissionsScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.DefaultPermissionsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.DvrInstanceScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.DvrInstanceViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.MediaServerActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.MediaServerScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.MediaServerViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.OverrideRuleActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.OverrideRuleScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.OverrideRuleViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerGeneralScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerGeneralViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerSettingsPage
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServicesActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServicesScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServicesViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.TautulliScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.TautulliViewModel
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorViewModel

/** The server-settings entries of [SeerrNavHost]: one screen per page, each over its own editor. */
@Composable
internal fun ServerSettingsPageEntry(
    page: ServerSettingsPage,
    onBack: () -> Unit,
    onOpenPage: (ServerSettingsPage) -> Unit,
    onOpenInstance: (ServiceType, Int?) -> Unit,
    onOpenRule: (Int?) -> Unit,
) {
    when (page) {
        ServerSettingsPage.Services -> {
            val viewModel = hiltViewModel<ServicesViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            // Re-read on every arrival: an instance or a rule is changed on a page of its own.
            LaunchedEffect(viewModel) { viewModel.reload() }
            ServicesScreen(
                state = state,
                actions =
                    ServicesActions(
                        onBack = onBack,
                        onRetry = viewModel::reload,
                        onOpenInstance = onOpenInstance,
                        onOpenRule = onOpenRule,
                    ),
            )
        }
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
        ServerSettingsPage.MediaServer -> {
            val viewModel = hiltViewModel<MediaServerViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val extras by viewModel.extras.collectAsStateWithLifecycle()
            MediaServerScreen(
                state = state,
                extras = extras,
                events = viewModel.events,
                actions = viewModel.editorActions(onBack),
                serverActions =
                    MediaServerActions(
                        onSetLibraryEnabled = viewModel::setLibraryEnabled,
                        onSyncLibraries = viewModel::syncLibraries,
                        onStartScan = viewModel::startScan,
                        onCancelScan = viewModel::cancelScan,
                        onOpenServerPicker = viewModel::openServerPicker,
                        onCloseServerPicker = viewModel::closeServerPicker,
                        onChooseConnection = viewModel::chooseConnection,
                        onOpenTautulli = { onOpenPage(ServerSettingsPage.Tautulli) },
                    ),
            )
        }
        ServerSettingsPage.Tautulli -> {
            val viewModel = hiltViewModel<TautulliViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            TautulliScreen(state = state, events = viewModel.events, actions = viewModel.editorActions(onBack))
        }
        ServerSettingsPage.DefaultPermissions -> {
            val viewModel = hiltViewModel<DefaultPermissionsViewModel>()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            DefaultPermissionsScreen(
                state = state,
                events = viewModel.events,
                actions = viewModel.editorActions(onBack),
                onToggle = viewModel::toggle,
            )
        }
    }
}

/** The instance editor; leaves on its own once the instance is deleted. */
@Composable
internal fun DvrInstanceEntry(
    type: ServiceType,
    id: Int?,
    onBack: () -> Unit,
) {
    val viewModel =
        hiltViewModel<DvrInstanceViewModel, DvrInstanceViewModel.Factory>(creationCallback = { factory -> factory.create(type, id) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) { if (deleted) onBack() }
    DvrInstanceScreen(
        state = state,
        extras = extras,
        events = viewModel.events,
        actions = viewModel.editorActions(onBack),
        onTest = viewModel::test,
        onDelete = viewModel::delete,
    )
}

/** The rule editor; leaves on its own once the rule is deleted. */
@Composable
internal fun OverrideRuleEntry(
    id: Int?,
    onBack: () -> Unit,
) {
    val viewModel =
        hiltViewModel<OverrideRuleViewModel, OverrideRuleViewModel.Factory>(creationCallback = { factory ->
            factory.create(id)
        })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) { if (deleted) onBack() }
    OverrideRuleScreen(
        state = state,
        extras = extras,
        events = viewModel.events,
        actions = viewModel.editorActions(onBack),
        ruleActions =
            OverrideRuleActions(
                onSelectInstance = viewModel::selectInstance,
                onToggleUser = viewModel::toggleUser,
                onToggleTag = viewModel::toggleTag,
                onDelete = viewModel::delete,
            ),
    )
}

private fun <T> EditorViewModel<T>.editorActions(onBack: () -> Unit): EditorActions<T> =
    EditorActions(onBack = onBack, onRetry = ::reload, onEdit = ::edit, onSave = ::save)
