package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.openInBrowser
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.settings.server.AboutScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.AboutViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.AgentsActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.ApiKeyActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.CacheActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.CacheScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.CacheViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.DefaultPermissionsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.DiscoverSlidersScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.DiscoverSlidersViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.JobsActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.JobsScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.JobsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.LogsActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.LogsScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.LogsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.MediaServerActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.MediaServerScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.MediaServerViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.MetadataScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.MetadataViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.NetworkScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.NetworkViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.NotificationAgentsScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.NotificationAgentsViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerAgent
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerGeneralScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerGeneralViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerSettingsPage
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServicesActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServicesScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServicesViewModel
import io.github.scottcooper92.binge.seerr.ui.settings.server.SlidersActions
import io.github.scottcooper92.binge.seerr.ui.settings.server.TautulliScreen
import io.github.scottcooper92.binge.seerr.ui.settings.server.TautulliViewModel
import io.github.scottcooper92.binge.seerr.ui.users.settings.PermissionsSettingsScreen
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorActions

/** The server-settings entries of [SeerrNavHost]: one screen per page, each over its own editor. */
@Composable
internal fun ServerSettingsPageEntry(
    page: ServerSettingsPage,
    onBack: () -> Unit,
    onOpenPage: (ServerSettingsPage) -> Unit,
    onOpenInstance: (ServiceType, Int?) -> Unit,
    onOpenRule: (Int?) -> Unit,
    onOpenAgent: (ServerAgent) -> Unit,
    onOpenSlider: (Int?) -> Unit,
) {
    when (page) {
        ServerSettingsPage.About -> AboutPage(onBack)
        ServerSettingsPage.Logs -> LogsPage(onBack)
        ServerSettingsPage.Jobs -> JobsPage(onBack)
        ServerSettingsPage.Cache -> CachePage(onBack)
        ServerSettingsPage.Network -> NetworkPage(onBack)
        ServerSettingsPage.Metadata -> MetadataPage(onBack)
        ServerSettingsPage.DiscoverSliders -> DiscoverSlidersPage(onBack, onOpenSlider)
        ServerSettingsPage.NotificationAgents -> NotificationAgentsPage(onBack, onOpenAgent)
        ServerSettingsPage.Services -> ServicesPage(onBack, onOpenInstance, onOpenRule)
        ServerSettingsPage.General -> GeneralPage(onBack, onOpenPage)
        ServerSettingsPage.MediaServer -> MediaServerPage(onBack, onOpenPage)
        ServerSettingsPage.Tautulli -> TautulliPage(onBack)
        ServerSettingsPage.DefaultPermissions -> DefaultPermissionsPage(onBack)
    }
}

/** The server's version and the app's, plus the links the About page carries. */
@Composable
private fun AboutPage(onBack: () -> Unit) {
    val viewModel = hiltViewModel<AboutViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AboutScreen(state = state, onBack = onBack, onRetry = viewModel::reload, onOpenUrl = { url -> context.openInBrowser(url) })
}

/** The server's log, with the copy the page offers wired to the clipboard. */
@Composable
private fun LogsPage(onBack: () -> Unit) {
    val viewModel = hiltViewModel<LogsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.server_settings_logs)
    LogsScreen(
        state = state,
        entriesFor = viewModel::entries,
        events = viewModel.events,
        actions =
            LogsActions(
                onBack = onBack,
                onLevelChange = viewModel::setLevel,
                onSearchChange = viewModel::setSearch,
                onFollowingChange = viewModel::setFollowing,
                onCopy = { text -> context.copyToClipboard(copyLabel, text) },
            ),
    )
}

/** The server's scheduled jobs: run, cancel, and reschedule. */
@Composable
private fun JobsPage(onBack: () -> Unit) {
    val viewModel = hiltViewModel<JobsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    JobsScreen(
        state = state,
        events = viewModel.events,
        actions =
            JobsActions(
                onBack = onBack,
                onRetry = viewModel::reload,
                onRun = viewModel::run,
                onCancel = viewModel::cancel,
                onSchedule = viewModel::schedule,
            ),
    )
}

/** The server's caches and what flushing one does. */
@Composable
private fun CachePage(onBack: () -> Unit) {
    val viewModel = hiltViewModel<CacheViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CacheScreen(
        state = state,
        events = viewModel.events,
        actions =
            CacheActions(
                onBack = onBack,
                onRetry = viewModel::reload,
                onFlush = viewModel::flush,
                onFlushDnsEntry = viewModel::flushDnsEntry,
            ),
    )
}

/** The proxy and DNS settings, as one editor. */
@Composable
private fun NetworkPage(onBack: () -> Unit) {
    val viewModel = hiltViewModel<NetworkViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NetworkScreen(state = state, events = viewModel.events, actions = viewModel.editorActions(onBack))
}

/** Which metadata provider the server uses, with a test beside it. */
@Composable
private fun MetadataPage(onBack: () -> Unit) {
    val viewModel = hiltViewModel<MetadataViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val extras by viewModel.extras.collectAsStateWithLifecycle()
    MetadataScreen(
        state = state,
        extras = extras,
        events = viewModel.events,
        actions = viewModel.editorActions(onBack),
        onTest = viewModel::test,
    )
}

/** The discover sliders' order and which are on; a custom one is edited on its own page. */
@Composable
private fun DiscoverSlidersPage(
    onBack: () -> Unit,
    onOpenSlider: (Int?) -> Unit,
) {
    val viewModel = hiltViewModel<DiscoverSlidersViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Re-read on every arrival: a custom slider is added or changed on a page of its own.
    LaunchedEffect(viewModel) { viewModel.reload() }
    DiscoverSlidersScreen(
        state = state,
        events = viewModel.events,
        actions = viewModel.editorActions(onBack),
        sliderActions =
            SlidersActions(
                onMove = viewModel::move,
                onToggle = viewModel::toggle,
                onOpenSlider = onOpenSlider,
                onReset = viewModel::reset,
            ),
    )
}

/** Every agent and whether it is on; each is configured on its own page. */
@Composable
private fun NotificationAgentsPage(
    onBack: () -> Unit,
    onOpenAgent: (ServerAgent) -> Unit,
) {
    val viewModel = hiltViewModel<NotificationAgentsViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.reload() }
    NotificationAgentsScreen(
        state = state,
        actions = AgentsActions(onBack = onBack, onRetry = viewModel::reload, onOpenAgent = onOpenAgent),
    )
}

/** Radarr, Sonarr and the override rules, each opened on its own page. */
@Composable
private fun ServicesPage(
    onBack: () -> Unit,
    onOpenInstance: (ServiceType, Int?) -> Unit,
    onOpenRule: (Int?) -> Unit,
) {
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

/** The server's general settings, with the API key and the way into default permissions. */
@Composable
private fun GeneralPage(
    onBack: () -> Unit,
    onOpenPage: (ServerSettingsPage) -> Unit,
) {
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

/** Plex, Jellyfin or Emby: the libraries, the scan, and the way into Tautulli. */
@Composable
private fun MediaServerPage(
    onBack: () -> Unit,
    onOpenPage: (ServerSettingsPage) -> Unit,
) {
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

/** Tautulli's address and key, as one editor. */
@Composable
private fun TautulliPage(onBack: () -> Unit) {
    val viewModel = hiltViewModel<TautulliViewModel>()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TautulliScreen(state = state, events = viewModel.events, actions = viewModel.editorActions(onBack))
}

/** The permissions a new account starts with. */
@Composable
private fun DefaultPermissionsPage(onBack: () -> Unit) {
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
