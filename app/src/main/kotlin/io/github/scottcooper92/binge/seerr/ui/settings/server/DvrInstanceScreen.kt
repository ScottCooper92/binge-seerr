package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Https
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tag
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
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeConfirmDialog
import com.binge.designsystem.component.BingeOutlinedButton
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.ChoicePicker
import io.github.scottcooper92.binge.seerr.ui.ChoiceRow
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSectionCard
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorToggleGroup
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorToggleRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow

/**
 * One instance: the connection, a test that reaches it, the destination picked from what it
 * answered, the flags, and the kind's own fields. Delete asks first; a default instance gone is
 * every plain request with nowhere to go.
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
        EditorSectionCard(stringResource(R.string.settings_group_connection)) {
            ConnectionFields(draft, enabled, actions)
            BingeOutlinedButton(
                label = stringResource(R.string.server_settings_dvr_test),
                onClick = onTest,
                enabled = enabled && draft.connectionValid && !extras.testing,
                loading = extras.testing,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DestinationFields(draft, extras.choices, enabled, actions)
        FlagSwitches(draft, enabled, actions)
        if (draft.id != null) DeleteButton(onDelete)
    }
}

@Composable
private fun ConnectionFields(
    draft: DvrForm,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    EditorTextField(draft.name, stringResource(R.string.server_settings_dvr_name), icon = Icons.Filled.Badge, enabled = enabled) { value ->
        actions.onEdit { it.copy(name = value) }
    }
    EditorTextField(
        draft.host,
        stringResource(R.string.server_settings_host),
        icon = Icons.Filled.Dns,
        enabled = enabled,
        keyboardType = KeyboardType.Uri,
        placeholder = stringResource(R.string.placeholder_host),
    ) { value ->
        actions.onEdit { it.copy(host = value) }
    }
    EditorTextField(
        draft.port,
        stringResource(R.string.server_settings_port),
        icon = Icons.Filled.Tag,
        enabled = enabled,
        keyboardType = KeyboardType.Number,
        isError = draft.port.isNotBlank() && !draft.copy(host = "x", apiKey = "x").connectionValid,
    ) { value -> actions.onEdit { it.copy(port = value) } }
    EditorToggleRow(
        editorToggle(Icons.Filled.Https, stringResource(R.string.server_settings_use_ssl), draft.useSsl, enabled) { value ->
            actions.onEdit { it.copy(useSsl = value) }
        },
    )
    EditorTextField(
        draft.apiKey,
        stringResource(R.string.server_settings_api_key),
        icon = Icons.Filled.Key,
        enabled = enabled,
        secret = true,
    ) { value ->
        actions.onEdit { it.copy(apiKey = value) }
    }
    EditorTextField(
        draft.baseUrl,
        stringResource(R.string.server_settings_url_base),
        icon = Icons.Filled.Link,
        enabled = enabled,
        placeholder =
            stringResource(
                if (draft.type == ServiceType.Radarr) R.string.placeholder_url_base_radarr else R.string.placeholder_url_base_sonarr,
            ),
    ) { value ->
        actions.onEdit { it.copy(baseUrl = value) }
    }
    EditorTextField(
        draft.externalUrl,
        stringResource(R.string.server_settings_external_url),
        icon = Icons.Filled.Language,
        enabled = enabled,
        keyboardType = KeyboardType.Uri,
        supporting = stringResource(R.string.server_settings_dvr_external_hint),
        isError = !draft.externalUrlValid,
    ) { value -> actions.onEdit { it.copy(externalUrl = value) } }
}

/** Empty until a test answered: the pickers show what the instance offers, and nothing else is offered. */
@Composable
internal fun DestinationFields(
    draft: DvrForm,
    choices: DvrChoices?,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    if (choices == null) {
        EditorSectionCard(stringResource(R.string.server_settings_dvr_destination)) {
            Text(
                stringResource(R.string.server_settings_dvr_untested),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    EditorSectionCard(stringResource(R.string.server_settings_dvr_destination)) {
        DestinationChoices(draft, choices, enabled, actions)
    }
    if (draft.type == ServiceType.Sonarr) AnimeFields(draft, choices, enabled, actions)
}

@Composable
private fun DestinationChoices(
    draft: DvrForm,
    choices: DvrChoices,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    if (draft.type == ServiceType.Radarr) {
        ChoicePicker(
            title = stringResource(R.string.server_settings_dvr_minimum_availability),
            choices = MINIMUM_AVAILABILITIES.map { it to stringResource(it.availabilityRes()) },
            selected = draft.minimumAvailability,
            onSelect = { value -> actions.onEdit { it.copy(minimumAvailability = value) } },
            enabled = enabled,
        )
    }
    ChoiceRow(
        title = stringResource(R.string.advanced_profile),
        choices = choices.profiles.map { it.id to it.label },
        selected = draft.profileId,
        onSelect = { id -> actions.onEdit { it.copy(profileId = id) } },
        enabled = enabled,
    )
    ChoiceRow(
        title = stringResource(R.string.advanced_root_folder),
        choices = choices.rootFolders.map { it to it },
        selected = draft.rootFolder,
        onSelect = { path -> actions.onEdit { it.copy(rootFolder = path) } },
        enabled = enabled,
    )
    choices.languageProfiles?.let { profiles ->
        ChoiceRow(
            title = stringResource(R.string.server_settings_dvr_language_profile),
            choices = profiles.map { it.id to it.label },
            selected = draft.languageProfileId,
            onSelect = { id -> actions.onEdit { it.copy(languageProfileId = id) } },
            enabled = enabled,
        )
    }
    TagChips(choices.tags, draft.tagIds, enabled) { id -> actions.onEdit { it.copy(tagIds = it.tagIds.toggled(id)) } }
    if (draft.type == ServiceType.Sonarr) SonarrFields(draft, enabled, actions)
}

@Composable
private fun SonarrFields(
    draft: DvrForm,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    ChoicePicker(
        title = stringResource(R.string.server_settings_dvr_series_type),
        choices = SERIES_TYPES.map { it to stringResource(it.seriesTypeRes()) },
        selected = draft.seriesType,
        onSelect = { value -> actions.onEdit { it.copy(seriesType = value) } },
        enabled = enabled,
    )
    draft.seasonFolders?.let { on ->
        EditorToggleRow(
            editorToggle(Icons.Filled.Folder, stringResource(R.string.server_settings_dvr_season_folders), on, enabled) { value ->
                actions.onEdit { it.copy(seasonFolders = value) }
            },
        )
    }
}

@Composable
private fun AnimeFields(
    draft: DvrForm,
    choices: DvrChoices,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) = EditorSectionCard(stringResource(R.string.server_settings_dvr_anime)) {
    ChoicePicker(
        title = stringResource(R.string.server_settings_dvr_series_type),
        choices = SERIES_TYPES.map { it to stringResource(it.seriesTypeRes()) },
        selected = draft.animeSeriesType,
        onSelect = { value -> actions.onEdit { it.copy(animeSeriesType = value) } },
        enabled = enabled,
    )
    ChoiceRow(
        title = stringResource(R.string.advanced_profile),
        choices = choices.profiles.map { it.id to it.label },
        selected = draft.animeProfileId,
        onSelect = { id -> actions.onEdit { it.copy(animeProfileId = id) } },
        enabled = enabled,
    )
    ChoiceRow(
        title = stringResource(R.string.advanced_root_folder),
        choices = choices.rootFolders.map { it to it },
        selected = draft.animeRootFolder,
        onSelect = { path -> actions.onEdit { it.copy(animeRootFolder = path) } },
        enabled = enabled,
    )
    choices.languageProfiles?.let { profiles ->
        ChoiceRow(
            title = stringResource(R.string.server_settings_dvr_anime_language_profile),
            choices = profiles.map { it.id to it.label },
            selected = draft.animeLanguageProfileId,
            onSelect = { id -> actions.onEdit { it.copy(animeLanguageProfileId = id) } },
            enabled = enabled,
        )
    }
    TagChips(choices.tags, draft.animeTagIds.orEmpty(), enabled) { id ->
        actions.onEdit { it.copy(animeTagIds = it.animeTagIds.orEmpty().toggled(id)) }
    }
}

@Composable
internal fun FlagSwitches(
    draft: DvrForm,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    EditorToggleGroup(
        stringResource(R.string.server_settings_dvr_behaviour),
        listOf(
            editorToggle(Icons.Filled.Star, stringResource(R.string.server_settings_dvr_default), draft.isDefault, enabled) { value ->
                actions.onEdit { it.copy(isDefault = value) }
            },
            editorToggle(Icons.Filled.HighQuality, stringResource(R.string.server_settings_dvr_4k), draft.is4k, enabled) { value ->
                actions.onEdit { it.copy(is4k = value) }
            },
            editorToggle(Icons.Filled.Sync, stringResource(R.string.server_settings_dvr_sync), draft.syncEnabled, enabled) { value ->
                actions.onEdit { it.copy(syncEnabled = value) }
            },
            editorToggle(
                Icons.Filled.Search,
                stringResource(R.string.server_settings_dvr_prevent_search),
                draft.preventSearch,
                enabled,
            ) { value ->
                actions.onEdit { it.copy(preventSearch = value) }
            },
            editorToggle(
                Icons.Filled.Label,
                stringResource(R.string.server_settings_dvr_tag_requests),
                draft.tagRequests,
                enabled,
            ) { value ->
                actions.onEdit { it.copy(tagRequests = value) }
            },
        ),
    )
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

private fun String.availabilityRes(): Int =
    when (this) {
        "announced" -> R.string.server_settings_dvr_availability_announced
        "inCinemas" -> R.string.server_settings_dvr_availability_in_cinemas
        else -> R.string.server_settings_dvr_availability_released
    }

private fun String.seriesTypeRes(): Int =
    when (this) {
        "daily" -> R.string.server_settings_dvr_series_daily
        "anime" -> R.string.server_settings_dvr_series_anime
        else -> R.string.server_settings_dvr_series_standard
    }
