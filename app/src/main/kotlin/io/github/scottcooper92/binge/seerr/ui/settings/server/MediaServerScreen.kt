package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeBottomSheet
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.state.ErrorScreen
import io.github.scottcooper92.binge.seerr.ui.state.LoadingScreen
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSectionTitle
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSwitchRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

/** What the page does beside the form: the libraries, the scan, the Plex picker, and the way to Tautulli. */
class MediaServerActions(
    val onSetLibraryEnabled: (String, Boolean) -> Unit,
    val onSyncLibraries: () -> Unit,
    val onStartScan: () -> Unit,
    val onCancelScan: () -> Unit,
    val onOpenServerPicker: () -> Unit,
    val onCloseServerPicker: () -> Unit,
    val onChooseConnection: (PlexServerChoice, PlexConnection) -> Unit,
    val onOpenTautulli: () -> Unit,
)

@Composable
fun MediaServerScreen(
    state: EditorUiState<MediaServerForm>,
    extras: MediaServerExtras,
    events: Flow<EditorEvent>,
    actions: EditorActions<MediaServerForm>,
    serverActions: MediaServerActions,
) {
    EditorPage(
        title = stringResource(R.string.server_settings_media_server),
        state = state,
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        ConnectionFields(draft, enabled, actions, serverActions)
        LibrariesSection(extras, serverActions)
        ScanSection(extras.scan, serverActions)
        if (draft.kind == MediaServerKind.Plex) {
            EditorSectionTitle(stringResource(R.string.server_settings_tautulli))
            BingeOutlinedButton(
                label = stringResource(R.string.server_settings_tautulli_open),
                onClick = serverActions.onOpenTautulli,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    extras.picker?.let { picker -> PlexServerSheet(picker, serverActions) }
}

@Composable
private fun ConnectionFields(
    draft: MediaServerForm,
    enabled: Boolean,
    actions: EditorActions<MediaServerForm>,
    serverActions: MediaServerActions,
) {
    Text(
        stringResource(R.string.server_settings_media_server_lead, stringResource(draft.kind.labelRes()), draft.serverName)
            .trimEnd(' ', ':'),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (draft.kind == MediaServerKind.Plex) {
        BingeOutlinedButton(
            label = stringResource(R.string.server_settings_plex_pick),
            onClick = serverActions.onOpenServerPicker,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    EditorTextField(
        draft.host,
        stringResource(R.string.server_settings_host),
        enabled = enabled,
        keyboardType = KeyboardType.Uri,
    ) { value ->
        actions.onEdit { it.copy(host = value) }
    }
    EditorTextField(
        draft.port,
        stringResource(R.string.server_settings_port),
        enabled = enabled,
        keyboardType = KeyboardType.Number,
        isError = draft.port.isNotBlank() && !draft.copy(host = "x").valid,
    ) { value -> actions.onEdit { it.copy(port = value) } }
    EditorSwitchRow(stringResource(R.string.server_settings_use_ssl), draft.useSsl, enabled = enabled) { value ->
        actions.onEdit { it.copy(useSsl = value) }
    }
    draft.urlBase?.let { base ->
        EditorTextField(base, stringResource(R.string.server_settings_url_base), enabled = enabled) { value ->
            actions.onEdit { it.copy(urlBase = value) }
        }
    }
    EditorTextField(
        draft.externalUrl,
        stringResource(
            if (draft.kind ==
                MediaServerKind.Plex
            ) {
                R.string.server_settings_plex_web_url
            } else {
                R.string.server_settings_external_host
            },
        ),
        enabled = enabled,
        keyboardType = KeyboardType.Uri,
        supporting = stringResource(R.string.server_settings_external_hint),
    ) { value -> actions.onEdit { it.copy(externalUrl = value) } }
    draft.forgotPasswordUrl?.let { url ->
        EditorTextField(
            url,
            stringResource(R.string.server_settings_forgot_password_url),
            enabled = enabled,
            keyboardType = KeyboardType.Uri,
        ) { value ->
            actions.onEdit { it.copy(forgotPasswordUrl = value) }
        }
    }
    draft.apiKey?.let { key ->
        EditorTextField(key, stringResource(R.string.server_settings_api_key), enabled = enabled, secret = true) { value ->
            actions.onEdit { it.copy(apiKey = value) }
        }
    }
}

/** Each library with its toggle, and the way to re-read them; a toggle in flight is disabled rather than optimistic. */
@Composable
private fun LibrariesSection(
    extras: MediaServerExtras,
    actions: MediaServerActions,
) {
    EditorSectionTitle(stringResource(R.string.server_settings_libraries))
    if (extras.libraries.isEmpty()) {
        Text(
            stringResource(R.string.server_settings_libraries_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    extras.libraries.forEach { library ->
        EditorSwitchRow(library.name, library.enabled, enabled = library.id !in extras.busyLibraryIds) { on ->
            actions.onSetLibraryEnabled(library.id, on)
        }
        Text(
            library.detail(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    BingeTextButton(
        label = stringResource(R.string.server_settings_libraries_sync),
        onClick = actions.onSyncLibraries,
        enabled = !extras.syncingLibraries,
        loading = extras.syncingLibraries,
    )
}

@Composable
private fun MediaLibrary.detail(): String {
    val kind =
        when (type) {
            LibraryType.Movies -> stringResource(R.string.server_settings_library_movies)
            LibraryType.Shows -> stringResource(R.string.server_settings_library_shows)
            null -> null
        }
    val scanned = formatRelativeOrAbsolute(lastScanMillis)?.let { stringResource(R.string.server_settings_library_scanned, it) }
    return listOfNotNull(kind, scanned).joinToString(stringResource(R.string.hub_meta_separator)).ifEmpty {
        stringResource(R.string.server_settings_library_never_scanned)
    }
}

/** The full scan: its progress while running with a way to stop it, else a way to start one. */
@Composable
private fun ScanSection(
    scan: LibraryScan?,
    actions: MediaServerActions,
) {
    EditorSectionTitle(stringResource(R.string.server_settings_scan))
    if (scan?.running == true) {
        val fraction = if (scan.total > 0) scan.progress.toFloat() / scan.total else 0f
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Text(
            scan.currentLibrary?.let { stringResource(R.string.server_settings_scan_running_library, scan.progress, scan.total, it) }
                ?: stringResource(R.string.server_settings_scan_running, scan.progress, scan.total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BingeOutlinedButton(
            label = stringResource(R.string.server_settings_scan_cancel),
            onClick = actions.onCancelScan,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(
            stringResource(R.string.server_settings_scan_lead),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BingeOutlinedButton(
            label = stringResource(R.string.server_settings_scan_start),
            onClick = actions.onStartScan,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The admin's own Plex servers, each connection a row: picking one fills the address fields. */
@Composable
private fun PlexServerSheet(
    picker: PlexServerPicker,
    actions: MediaServerActions,
) {
    BingeBottomSheet(onDismissRequest = actions.onCloseServerPicker) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.screen_content_inset)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_m)),
        ) {
            Text(stringResource(R.string.server_settings_plex_pick), style = MaterialTheme.typography.titleLarge)
            when (picker) {
                PlexServerPicker.Loading -> LoadingScreen()
                is PlexServerPicker.Failed -> ErrorScreen(error = picker.error, onRetry = actions.onOpenServerPicker)
                is PlexServerPicker.Ready ->
                    if (picker.servers.isEmpty()) {
                        Text(stringResource(R.string.server_settings_plex_none), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        picker.servers.forEach { server -> PlexServerRows(server, actions) }
                    }
            }
        }
    }
}

@Composable
private fun PlexServerRows(
    server: PlexServerChoice,
    actions: MediaServerActions,
) {
    Text(server.name, style = MaterialTheme.typography.titleSmall)
    server.connections.forEach { connection ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { actions.onChooseConnection(server, connection) }
                    .padding(vertical = dimensionResource(DesR.dimen.padding_xs)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    stringResource(R.string.server_settings_plex_connection, connection.address, connection.port),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    listOfNotNull(
                        stringResource(if (connection.local) R.string.server_settings_plex_local else R.string.server_settings_plex_remote),
                        if (connection.useSsl) stringResource(R.string.server_settings_use_ssl) else null,
                    ).joinToString(stringResource(R.string.hub_meta_separator)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            connection.reachable?.let { reachable ->
                Text(
                    stringResource(if (reachable) R.string.server_settings_plex_reachable else R.string.server_settings_plex_unreachable),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (reachable) BingeSentiment.Positive.fill() else BingeSentiment.Negative.fill(),
                )
            }
        }
    }
}

private fun MediaServerKind.labelRes(): Int =
    when (this) {
        MediaServerKind.Plex -> R.string.user_origin_plex
        MediaServerKind.Jellyfin -> R.string.user_origin_jellyfin
        MediaServerKind.Emby -> R.string.user_origin_emby
    }
