package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeOutlinedButton
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import com.binge.designsystem.theme.BingeSentiment
import com.binge.designsystem.theme.fill
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.choiceSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import io.github.scottcooper92.binge.seerr.ui.users.settings.textSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow

/**
 * One instance in the web client's field order, as groups of list rows: the server and a row that tests it, the
 * destination picked from what the test answered (and a Sonarr's anime destination), then the options. Each text
 * value is edited in a sheet that checks it. Delete asks first; a default instance gone is every plain request with
 * nowhere to go.
 */
@Composable
fun DvrInstanceScreen(
    state: ExtrasEditorUiState<DvrForm, DvrExtras>,
    events: Flow<EditorEvent>,
    actions: EditorActions<DvrForm>,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    val ready = state as? ExtrasEditorUiState.Ready<DvrForm, DvrExtras>
    val extras = ready?.extras ?: DvrExtras()
    val title = ready?.draft?.let { if (it.id == null) null else it.name.ifBlank { it.type.name } }
    EditorPage(
        title = title ?: stringResource(R.string.server_settings_dvr_new),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        ServerGroup(draft, extras, enabled, actions, onTest)
        DestinationGroup(draft, extras.choices, enabled, actions)
        if (draft.type == ServiceType.Sonarr) AnimeGroup(draft, extras.choices, enabled, actions)
        OptionsGroup(draft, enabled, actions)
        if (draft.id != null) DeleteGroup(onDelete)
    }
}

/** Default and 4K, then how the instance is reached, ending in the test that loads what it offers. */
@Composable
private fun ServerGroup(
    draft: DvrForm,
    extras: DvrExtras,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
    onTest: () -> Unit,
) {
    val nameLabel = stringResource(R.string.server_settings_dvr_name)
    val hostLabel = stringResource(R.string.server_settings_host)
    val keyLabel = stringResource(R.string.server_settings_api_key)
    val portError = stringResource(R.string.editor_error_port)
    ItemGroup(
        title = stringResource(R.string.settings_group_connection),
        rows =
            listOf(
                editorToggle(Icons.Filled.Star, stringResource(R.string.server_settings_dvr_default), draft.isDefault, enabled) { on ->
                    actions.onEdit { it.copy(isDefault = on) }
                },
                editorToggle(Icons.Filled.HighQuality, stringResource(R.string.server_settings_dvr_4k), draft.is4k, enabled) { on ->
                    actions.onEdit { it.copy(is4k = on) }
                },
                textSettingItem(
                    icon = Icons.Filled.Badge,
                    label = nameLabel,
                    value = draft.name,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(name = value) } },
                    check = { value -> nameLabel.takeIf { value.isBlank() } },
                ),
                textSettingItem(
                    icon = Icons.Filled.Dns,
                    label = hostLabel,
                    value = draft.host,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(host = value) } },
                    check = { value -> hostLabel.takeIf { value.isBlank() } },
                ),
                textSettingItem(
                    icon = Icons.Filled.Tag,
                    label = stringResource(R.string.server_settings_port),
                    value = draft.port,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(port = value) } },
                    check = { value -> portError.takeIf { !portValid(value) } },
                ),
                editorToggle(Icons.Filled.Https, stringResource(R.string.server_settings_use_ssl), draft.useSsl, enabled) { on ->
                    actions.onEdit { it.copy(useSsl = on) }
                },
                textSettingItem(
                    icon = Icons.Filled.Key,
                    label = keyLabel,
                    value = draft.apiKey,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(apiKey = value) } },
                    check = { value -> keyLabel.takeIf { value.isBlank() } },
                    shown = draft.apiKey.maskedKey() ?: stringResource(R.string.settings_value_not_set),
                ),
                textSettingItem(
                    icon = Icons.Filled.Link,
                    label = stringResource(R.string.server_settings_url_base),
                    value = draft.baseUrl,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(baseUrl = value) } },
                ),
                ListItem(
                    icon = Icons.Filled.NetworkCheck,
                    label = stringResource(R.string.server_settings_dvr_test),
                    detail =
                        stringResource(
                            if (extras.choices == null) R.string.server_settings_dvr_untested else R.string.server_settings_dvr_tested,
                        ),
                    loading = extras.testing,
                    clickable = enabled && draft.connectionValid && !extras.testing,
                    disabled = !enabled || !draft.connectionValid,
                    onClick = onTest,
                ),
            ),
    )
}

/** Season folders and new seasons on a Sonarr, then the link out and the instance's behaviour, as the web client ends. */
@Composable
internal fun OptionsGroup(
    draft: DvrForm,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    val urlError = stringResource(R.string.editor_error_web_url)
    ItemGroup(
        title = stringResource(R.string.server_settings_dvr_behaviour),
        rows =
            listOfNotNull(
                draft.seasonFolders?.let { on ->
                    editorToggle(Icons.Filled.Folder, stringResource(R.string.server_settings_dvr_season_folders), on, enabled) { value ->
                        actions.onEdit { it.copy(seasonFolders = value) }
                    }
                },
                draft.monitorNewItems?.let { monitor ->
                    choiceSettingItem(
                        icon = Icons.Filled.Visibility,
                        title = stringResource(R.string.server_settings_dvr_monitor_new),
                        choices =
                            listOf(
                                MONITOR_NEW_ITEMS_ALL to stringResource(R.string.server_settings_dvr_monitor_all),
                                MONITOR_NEW_ITEMS_NONE to stringResource(R.string.server_settings_dvr_monitor_none),
                            ),
                        selected = monitor,
                        enabled = enabled,
                    ) { value -> actions.onEdit { it.copy(monitorNewItems = value) } }
                },
                textSettingItem(
                    icon = Icons.Filled.Language,
                    label = stringResource(R.string.server_settings_external_url),
                    value = draft.externalUrl,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(externalUrl = value) } },
                    hint = stringResource(R.string.server_settings_dvr_external_hint),
                    check = { value -> urlError.takeIf { !draft.copy(externalUrl = value).externalUrlValid } },
                ),
                editorToggle(Icons.Filled.Sync, stringResource(R.string.server_settings_dvr_sync), draft.syncEnabled, enabled) { on ->
                    actions.onEdit { it.copy(syncEnabled = on) }
                },
                editorToggle(
                    Icons.Filled.Search,
                    stringResource(R.string.server_settings_dvr_prevent_search),
                    draft.preventSearch,
                    enabled,
                ) { on ->
                    actions.onEdit { it.copy(preventSearch = on) }
                },
                editorToggle(
                    Icons.AutoMirrored.Filled.Label,
                    stringResource(R.string.server_settings_dvr_tag_requests),
                    draft.tagRequests,
                    enabled,
                ) { on -> actions.onEdit { it.copy(tagRequests = on) } },
            ),
    )
}

/** Delete as a row of its own, asking first: the server forgets the thing at once. */
@Composable
internal fun DeleteGroup(onDelete: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    ItemGroup(
        title = null,
        rows =
            listOf(
                ListItem(
                    icon = Icons.Filled.Delete,
                    iconTint = BingeSentiment.Negative.fill(),
                    label = stringResource(R.string.server_settings_delete),
                    onClick = { confirming = true },
                ),
            ),
    )
    if (confirming) {
        BingeConfirmDialog(
            title = stringResource(R.string.server_settings_delete_title),
            message = stringResource(R.string.server_settings_delete_message),
            confirmLabel = stringResource(R.string.server_settings_delete),
            destructive = true,
            onConfirm = {
                confirming = false
                onDelete()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
internal fun TagChips(
    tags: List<io.github.scottcooper92.binge.seerr.ui.Choice>,
    selected: Set<Int>,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
    if (tags.isEmpty()) return
    Text(stringResource(R.string.request_tags), style = MaterialTheme.typography.titleSmall)
    tags.forEach { tag ->
        FilterChip(selected = tag.id in selected, onClick = { onToggle(tag.id) }, enabled = enabled, label = { Text(tag.label) })
    }
}

@Composable
internal fun DeleteButton(onDelete: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    BingeOutlinedButton(
        label = stringResource(R.string.server_settings_delete),
        onClick = { confirming = true },
        destructive = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (confirming) {
        BingeConfirmDialog(
            title = stringResource(R.string.server_settings_delete_title),
            message = stringResource(R.string.server_settings_delete_message),
            confirmLabel = stringResource(R.string.server_settings_delete),
            destructive = true,
            onConfirm = {
                confirming = false
                onDelete()
            },
            onDismiss = { confirming = false },
        )
    }
}

internal fun Set<Int>.toggled(id: Int): Set<Int> = if (id in this) this - id else this + id
