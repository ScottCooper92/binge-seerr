package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.BingeTextButton
import com.binge.designsystem.formatRelativeOrAbsolute
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSection
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorToggleRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorValidation
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import io.github.scottcooper92.binge.seerr.ui.users.settings.imeActionIf
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow

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

/**
 * The media-server page, as collapsible sections (#549): the connection, with its host and port
 * required; the links users open, closed until asked for; the libraries; the full scan, closed unless
 * one is running; and, for Plex, the way to Tautulli.
 */
@Composable
fun MediaServerScreen(
    state: ExtrasEditorUiState<MediaServerForm, MediaServerExtras>,
    events: Flow<EditorEvent>,
    actions: EditorActions<MediaServerForm>,
    serverActions: MediaServerActions,
) {
    val extras = (state as? ExtrasEditorUiState.Ready<MediaServerForm, MediaServerExtras>)?.extras ?: MediaServerExtras()
    val validation = remember { EditorValidation<MediaServerForm>(MEDIA_SERVER_FORM_KEY) { it.issues() } }
    EditorPage(
        title = stringResource(R.string.server_settings_media_server),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        validation = validation,
    ) { draft, enabled ->
        ConnectionFields(draft, enabled, actions, serverActions)
        LinkFields(draft, enabled, actions)
        LibrariesSection(extras, serverActions)
        ScanSection(extras.scan, serverActions)
        if (draft.kind == MediaServerKind.Plex) {
            EditorSection(MediaServerSections.TAUTULLI, stringResource(R.string.server_settings_tautulli)) {
                BingeOutlinedButton(
                    label = stringResource(R.string.server_settings_tautulli_open),
                    onClick = serverActions.onOpenTautulli,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
    EditorSection(MediaServerSections.CONNECTION, stringResource(R.string.settings_group_connection)) {
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
        AddressFields(draft, enabled, actions)
    }
}

@Composable
private fun AddressFields(
    draft: MediaServerForm,
    enabled: Boolean,
    actions: EditorActions<MediaServerForm>,
) {
    EditorTextField(
        draft.host,
        stringResource(R.string.server_settings_host),
        icon = Icons.Filled.Dns,
        enabled = enabled,
        keyboardType = KeyboardType.Uri,
        placeholder = stringResource(R.string.placeholder_host),
        fieldId = MediaServerFields.HOST,
        required = true,
    ) { value ->
        actions.onEdit { it.copy(host = value) }
    }
    EditorTextField(
        draft.port,
        stringResource(R.string.server_settings_port),
        icon = Icons.Filled.Tag,
        enabled = enabled,
        keyboardType = KeyboardType.Number,
        placeholder = portPlaceholder(draft.kind),
        fieldId = MediaServerFields.PORT,
        required = true,
    ) { value -> actions.onEdit { it.copy(port = value) } }
    EditorToggleRow(
        editorToggle(Icons.Filled.Https, stringResource(R.string.server_settings_use_ssl), draft.useSsl, enabled) { value ->
            actions.onEdit { it.copy(useSsl = value) }
        },
    )
    draft.urlBase?.let { base ->
        EditorTextField(
            base,
            stringResource(R.string.server_settings_url_base),
            icon = Icons.AutoMirrored.Filled.AltRoute,
            enabled = enabled,
            placeholder = urlBasePlaceholder(draft.kind),
        ) { value ->
            actions.onEdit { it.copy(urlBase = value) }
        }
    }
    draft.apiKey?.let { key ->
        EditorTextField(
            key,
            stringResource(R.string.server_settings_api_key),
            icon = Icons.Filled.Key,
            enabled = enabled,
            secret = true,
        ) { value ->
            actions.onEdit { it.copy(apiKey = value) }
        }
    }
}

/** The addresses users are sent to, rather than the one the server is reached at. Optional, so closed until asked for. */
@Composable
private fun LinkFields(
    draft: MediaServerForm,
    enabled: Boolean,
    actions: EditorActions<MediaServerForm>,
) = EditorSection(MediaServerSections.LINKS, stringResource(R.string.server_settings_media_server_links), defaultExpanded = false) {
    EditorTextField(
        draft.externalUrl,
        stringResource(
            if (draft.kind == MediaServerKind.Plex) R.string.server_settings_plex_web_url else R.string.server_settings_external_host,
        ),
        icon = Icons.Filled.Link,
        enabled = enabled,
        keyboardType = KeyboardType.Uri,
        placeholder = externalUrlPlaceholder(draft.kind),
        supporting = stringResource(R.string.server_settings_external_hint),
        fieldId = MediaServerFields.EXTERNAL_URL,
        imeAction = imeActionIf(last = draft.forgotPasswordUrl == null),
    ) { value -> actions.onEdit { it.copy(externalUrl = value) } }
    draft.forgotPasswordUrl?.let { url ->
        EditorTextField(
            url,
            stringResource(R.string.server_settings_forgot_password_url),
            icon = Icons.Filled.LinkOff,
            enabled = enabled,
            keyboardType = KeyboardType.Uri,
            placeholder = stringResource(R.string.placeholder_url_https),
            fieldId = MediaServerFields.FORGOT_PASSWORD_URL,
            imeAction = ImeAction.Done,
        ) { value ->
            actions.onEdit { it.copy(forgotPasswordUrl = value) }
        }
    }
}

/** Each library with its toggle, and the way to re-read them; a toggle in flight is disabled rather than optimistic. */
@Composable
private fun LibrariesSection(
    extras: MediaServerExtras,
    actions: MediaServerActions,
) {
    EditorSection(MediaServerSections.LIBRARIES, stringResource(R.string.server_settings_libraries)) {
        if (extras.libraries.isEmpty()) {
            Text(
                stringResource(R.string.server_settings_libraries_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        extras.libraries.forEach { library ->
            EditorToggleRow(
                editorToggle(
                    Icons.Filled.VideoLibrary,
                    library.name,
                    library.enabled,
                    library.id !in extras.busyLibraryIds,
                    detail = library.detail(),
                ) { on -> actions.onSetLibraryEnabled(library.id, on) },
            )
        }
        SyncLibrariesButton(extras, actions)
    }
}

@Composable
private fun SyncLibrariesButton(
    extras: MediaServerExtras,
    actions: MediaServerActions,
) {
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

/** The full scan: its progress while running with a way to stop it, else a way to start one. Closed unless one is running. */
@Composable
private fun ScanSection(
    scan: LibraryScan?,
    actions: MediaServerActions,
) {
    EditorSection(MediaServerSections.SCAN, stringResource(R.string.server_settings_scan), defaultExpanded = scan?.running == true) {
        ScanContent(scan, actions)
    }
}

@Composable
private fun ScanContent(
    scan: LibraryScan?,
    actions: MediaServerActions,
) {
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

private fun MediaServerKind.labelRes(): Int =
    when (this) {
        MediaServerKind.Plex -> R.string.user_origin_plex
        MediaServerKind.Jellyfin -> R.string.user_origin_jellyfin
        MediaServerKind.Emby -> R.string.user_origin_emby
    }

/** The port the kind listens on out of the box. An example only: unlike the DVR form, this one starts blank. */
@Composable
private fun portPlaceholder(kind: MediaServerKind): String =
    stringResource(if (kind == MediaServerKind.Plex) R.string.placeholder_port_plex else R.string.placeholder_port_jellyfin)

/** The path each install is commonly served under when it is not at the root. Plex has no URL base. */
@Composable
private fun urlBasePlaceholder(kind: MediaServerKind): String =
    stringResource(if (kind == MediaServerKind.Emby) R.string.placeholder_url_base_emby else R.string.placeholder_url_base_jellyfin)

/** Plex asks for its web app's own address; the others for the address the server is reached at. */
@Composable
private fun externalUrlPlaceholder(kind: MediaServerKind): String =
    stringResource(if (kind == MediaServerKind.Plex) R.string.placeholder_plex_web_url else R.string.placeholder_server_url)
