package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.formatRelativeOrAbsolute
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.AddressKeyboard
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.NumberKeyboard
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import io.github.scottcooper92.binge.seerr.ui.users.settings.textSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
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

/**
 * The media-server page in the web client's order, as groups of list rows. Plex leads with its settings, then the
 * libraries, the scan and Tautulli; Jellyfin and Emby lead with the libraries and the scan, their settings last. Each
 * text value is edited in a sheet that checks it, so the page holds nothing of the wrong shape.
 */
@Composable
fun MediaServerScreen(
    state: ExtrasEditorUiState<MediaServerForm, MediaServerExtras>,
    events: Flow<EditorEvent>,
    actions: EditorActions<MediaServerForm>,
    serverActions: MediaServerActions,
) {
    val extras = (state as? ExtrasEditorUiState.Ready<MediaServerForm, MediaServerExtras>)?.extras ?: MediaServerExtras()
    EditorPage(
        title = stringResource(R.string.server_settings_media_server),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        if (draft.kind == MediaServerKind.Plex) {
            SettingsGroup(draft, enabled, actions, serverActions)
            LibrariesGroup(extras, serverActions)
            ScanGroup(extras.scan, serverActions)
            ItemGroup(
                title = stringResource(R.string.server_settings_tautulli),
                rows =
                    listOf(
                        ListItem(
                            icon = Icons.Filled.Insights,
                            label = stringResource(R.string.server_settings_tautulli_open),
                            onClick = serverActions.onOpenTautulli,
                        ),
                    ),
            )
        } else {
            LibrariesGroup(extras, serverActions)
            ScanGroup(extras.scan, serverActions)
            SettingsGroup(draft, enabled, actions, serverActions)
        }
    }
    extras.picker?.let { picker -> PlexServerSheet(picker, serverActions) }
}

/** How the server is reached, and the links users are sent to, in the web client's field order. */
@Composable
private fun SettingsGroup(
    draft: MediaServerForm,
    enabled: Boolean,
    actions: EditorActions<MediaServerForm>,
    serverActions: MediaServerActions,
) {
    val portError = stringResource(R.string.editor_error_port)
    val requiredError = stringResource(R.string.editor_field_required)
    ItemGroup(
        title = stringResource(R.string.server_settings_media_server_settings, stringResource(draft.kind.labelRes())),
        rows =
            listOfNotNull(
                ListItem(
                    icon = Icons.Filled.Storage,
                    label = stringResource(R.string.server_settings_plex_server),
                    detail = draft.serverName.ifBlank { stringResource(R.string.settings_value_not_set) },
                    clickable = enabled,
                    disabled = !enabled,
                    onClick = serverActions.onOpenServerPicker,
                ).takeIf { draft.kind == MediaServerKind.Plex },
                textSettingItem(
                    icon = Icons.Filled.Dns,
                    label = stringResource(R.string.server_settings_host),
                    value = draft.host,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(host = value) } },
                    keyboard = AddressKeyboard,
                    required = true,
                    placeholder = stringResource(R.string.placeholder_host),
                    check = { value -> requiredError.takeIf { value.isBlank() } },
                ),
                textSettingItem(
                    icon = Icons.Filled.Tag,
                    label = stringResource(R.string.server_settings_port),
                    value = draft.port,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(port = value) } },
                    keyboard = NumberKeyboard,
                    required = true,
                    placeholder = stringResource(draft.kind.portPlaceholderRes()),
                    check = { value -> portError.takeIf { !portValid(value) } },
                ),
                editorToggle(Icons.Filled.Https, stringResource(R.string.server_settings_use_ssl), draft.useSsl, enabled) { on ->
                    actions.onEdit { it.copy(useSsl = on) }
                },
                draft.apiKey?.let { key ->
                    textSettingItem(
                        icon = Icons.Filled.Key,
                        label = stringResource(R.string.server_settings_api_key),
                        value = key,
                        enabled = enabled,
                        onChange = { value -> actions.onEdit { it.copy(apiKey = value) } },
                        shown = key.maskedKey() ?: stringResource(R.string.settings_value_not_set),
                        secret = true,
                    )
                },
                draft.urlBase?.let { base ->
                    textSettingItem(
                        icon = Icons.AutoMirrored.Filled.AltRoute,
                        label = stringResource(R.string.server_settings_url_base),
                        value = base,
                        enabled = enabled,
                        onChange = { value -> actions.onEdit { it.copy(urlBase = value) } },
                        keyboard = AddressKeyboard,
                        placeholder = stringResource(draft.kind.urlBasePlaceholderRes()),
                    )
                },
                externalItem(draft, enabled, actions),
                draft.forgotPasswordUrl?.let { url -> forgotPasswordItem(draft, url, enabled, actions) },
            ),
    )
}

/** Where users open the server from, rather than where the server reaches it. */
@Composable
private fun externalItem(
    draft: MediaServerForm,
    enabled: Boolean,
    actions: EditorActions<MediaServerForm>,
): ListItem {
    val plex = draft.kind == MediaServerKind.Plex
    val urlError = stringResource(if (plex) R.string.editor_error_web_url else R.string.editor_error_web_url_no_slash)
    return textSettingItem(
        icon = Icons.Filled.Link,
        label = stringResource(if (plex) R.string.server_settings_plex_web_url else R.string.server_settings_external_host),
        value = draft.externalUrl,
        enabled = enabled,
        onChange = { value -> actions.onEdit { it.copy(externalUrl = value) } },
        keyboard = AddressKeyboard,
        hint = stringResource(R.string.server_settings_external_hint),
        placeholder = stringResource(if (plex) R.string.placeholder_plex_web_url else R.string.placeholder_server_url),
        check = { value -> urlError.takeIf { !draft.copy(externalUrl = value).externalUrlValid } },
    )
}

@Composable
private fun forgotPasswordItem(
    draft: MediaServerForm,
    url: String,
    enabled: Boolean,
    actions: EditorActions<MediaServerForm>,
): ListItem {
    val noSlashError = stringResource(R.string.editor_error_web_url_no_slash)
    return textSettingItem(
        icon = Icons.Filled.LinkOff,
        label = stringResource(R.string.server_settings_forgot_password_url),
        value = url,
        enabled = enabled,
        onChange = { value -> actions.onEdit { it.copy(forgotPasswordUrl = value) } },
        keyboard = AddressKeyboard,
        check = { value -> noSlashError.takeIf { !draft.copy(forgotPasswordUrl = value).forgotPasswordUrlValid } },
    )
}

/** Each library with its switch, then the way to re-read them; a switch in flight is disabled rather than optimistic. */
@Composable
private fun LibrariesGroup(
    extras: MediaServerExtras,
    actions: MediaServerActions,
) {
    val sync =
        ListItem(
            icon = Icons.Filled.Sync,
            label = stringResource(R.string.server_settings_libraries_sync),
            detail = stringResource(R.string.server_settings_libraries_none).takeIf { extras.libraries.isEmpty() },
            loading = extras.syncingLibraries,
            clickable = !extras.syncingLibraries,
            onClick = actions.onSyncLibraries,
        )
    ItemGroup(
        title = stringResource(R.string.server_settings_libraries),
        rows =
            extras.libraries.map { library ->
                editorToggle(
                    Icons.Filled.VideoLibrary,
                    library.name,
                    library.enabled,
                    library.id !in extras.busyLibraryIds,
                    detail = library.detail(),
                ) { on -> actions.onSetLibraryEnabled(library.id, on) }
            } + sync,
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

/** The full scan: a row that starts one, and while one runs, its progress and a row that stops it. */
@Composable
private fun ScanGroup(
    scan: LibraryScan?,
    actions: MediaServerActions,
) {
    val running = scan?.running == true
    val progress =
        scan?.takeIf { it.running }?.let {
            it.currentLibrary?.let { library ->
                stringResource(R.string.server_settings_scan_running_library, it.progress, it.total, library)
            }
                ?: stringResource(R.string.server_settings_scan_running, it.progress, it.total)
        }
    ItemGroup(
        title = stringResource(R.string.server_settings_scan),
        rows =
            listOfNotNull(
                ListItem(
                    icon = Icons.Filled.Refresh,
                    label = stringResource(R.string.server_settings_scan_start),
                    detail = progress ?: stringResource(R.string.server_settings_scan_lead),
                    loading = running,
                    clickable = !running,
                    onClick = actions.onStartScan,
                ),
                ListItem(
                    icon = Icons.Filled.Stop,
                    iconTint = BingeSentiment.Negative.fill(),
                    label = stringResource(R.string.server_settings_scan_cancel),
                    onClick = actions.onCancelScan,
                ).takeIf { running },
            ),
        belowRows =
            scan?.takeIf { it.running }?.let {
                {
                    LinearProgressIndicator(
                        progress = { if (it.total > 0) it.progress.toFloat() / it.total else 0f },
                        modifier = Modifier.fillMaxWidth().padding(dimensionResource(DesR.dimen.padding_m)),
                    )
                }
            },
    )
}

private fun MediaServerKind.labelRes(): Int =
    when (this) {
        MediaServerKind.Plex -> R.string.user_origin_plex
        MediaServerKind.Jellyfin -> R.string.user_origin_jellyfin
        MediaServerKind.Emby -> R.string.user_origin_emby
    }

/** The port each kind listens on out of the box: an example in the sheet, never a value. */
private fun MediaServerKind.portPlaceholderRes(): Int =
    if (this == MediaServerKind.Plex) R.string.placeholder_port_plex else R.string.placeholder_port_jellyfin

/** The path each kind is commonly served under when it is not at the root. */
private fun MediaServerKind.urlBasePlaceholderRes(): Int =
    if (this == MediaServerKind.Emby) R.string.placeholder_url_base_emby else R.string.placeholder_url_base_jellyfin
