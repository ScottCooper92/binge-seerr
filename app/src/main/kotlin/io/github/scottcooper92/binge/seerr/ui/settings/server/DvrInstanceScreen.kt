package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSectionTitle
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSwitchRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import kotlinx.coroutines.flow.Flow

/**
 * One instance: the connection, a test that reaches it, the destination picked from what it
 * answered, the flags, and the kind's own fields. Delete asks first; a default instance gone is
 * every plain request with nowhere to go.
 */
@Composable
fun DvrInstanceScreen(
    state: EditorUiState<DvrForm>,
    extras: DvrExtras,
    events: Flow<EditorEvent>,
    actions: EditorActions<DvrForm>,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    val title = (state as? EditorUiState.Ready)?.draft?.let { if (it.id == null) null else it.name.ifBlank { it.type.name } }
    EditorPage(
        title = title ?: stringResource(R.string.server_settings_dvr_new),
        state = state,
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        ConnectionFields(draft, enabled, actions)
        BingeOutlinedButton(
            label = stringResource(R.string.server_settings_dvr_test),
            onClick = onTest,
            enabled = enabled && draft.connectionValid && !extras.testing,
            loading = extras.testing,
            modifier = Modifier.fillMaxWidth(),
        )
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
    EditorTextField(draft.name, stringResource(R.string.server_settings_dvr_name), enabled = enabled) { value ->
        actions.onEdit { it.copy(name = value) }
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
        isError = draft.port.isNotBlank() && !draft.copy(host = "x", apiKey = "x").connectionValid,
    ) { value -> actions.onEdit { it.copy(port = value) } }
    EditorSwitchRow(stringResource(R.string.server_settings_use_ssl), draft.useSsl, enabled = enabled) { value ->
        actions.onEdit { it.copy(useSsl = value) }
    }
    EditorTextField(draft.apiKey, stringResource(R.string.server_settings_api_key), enabled = enabled, secret = true) { value ->
        actions.onEdit { it.copy(apiKey = value) }
    }
    EditorTextField(draft.baseUrl, stringResource(R.string.server_settings_url_base), enabled = enabled) { value ->
        actions.onEdit { it.copy(baseUrl = value) }
    }
    EditorTextField(
        draft.externalUrl,
        stringResource(R.string.server_settings_external_url),
        enabled = enabled,
        keyboardType = KeyboardType.Uri,
        supporting = stringResource(R.string.server_settings_dvr_external_hint),
    ) { value -> actions.onEdit { it.copy(externalUrl = value) } }
}

/** Empty until a test answered: the pickers show what the instance offers, and nothing else is offered. */
@Composable
private fun DestinationFields(
    draft: DvrForm,
    choices: DvrChoices?,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    EditorSectionTitle(stringResource(R.string.server_settings_dvr_destination))
    if (choices == null) {
        Text(
            stringResource(R.string.server_settings_dvr_untested),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (draft.type == ServiceType.Radarr) {
        ChoicePicker(
            title = stringResource(R.string.server_settings_dvr_minimum_availability),
            choices = MINIMUM_AVAILABILITIES.map { it to stringResource(it.availabilityRes()) },
            selected = draft.minimumAvailability,
            onSelect = { value -> actions.onEdit { it.copy(minimumAvailability = value) } },
        )
    }
    ChoicePicker(
        title = stringResource(R.string.advanced_profile),
        choices = choices.profiles.map { it.id to it.label },
        selected = draft.profileId,
        onSelect = { id -> actions.onEdit { it.copy(profileId = id) } },
    )
    ChoicePicker(
        title = stringResource(R.string.advanced_root_folder),
        choices = choices.rootFolders.map { it to it },
        selected = draft.rootFolder,
        onSelect = { path -> actions.onEdit { it.copy(rootFolder = path) } },
    )
    choices.languageProfiles?.let { profiles ->
        ChoicePicker(
            title = stringResource(R.string.server_settings_dvr_language_profile),
            choices = profiles.map { it.id to it.label },
            selected = draft.languageProfileId,
            onSelect = { id -> actions.onEdit { it.copy(languageProfileId = id) } },
        )
    }
    TagChips(choices.tags, draft.tagIds, enabled) { id -> actions.onEdit { it.copy(tagIds = it.tagIds.toggled(id)) } }
    if (draft.type == ServiceType.Sonarr) SonarrFields(draft, choices, enabled, actions)
}

@Composable
private fun SonarrFields(
    draft: DvrForm,
    choices: DvrChoices,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    ChoicePicker(
        title = stringResource(R.string.server_settings_dvr_series_type),
        choices = SERIES_TYPES.map { it to stringResource(it.seriesTypeRes()) },
        selected = draft.seriesType,
        onSelect = { value -> actions.onEdit { it.copy(seriesType = value) } },
    )
    draft.seasonFolders?.let { on ->
        EditorSwitchRow(stringResource(R.string.server_settings_dvr_season_folders), on, enabled = enabled) { value ->
            actions.onEdit { it.copy(seasonFolders = value) }
        }
    }
    EditorSectionTitle(stringResource(R.string.server_settings_dvr_anime))
    ChoicePicker(
        title = stringResource(R.string.server_settings_dvr_series_type),
        choices = SERIES_TYPES.map { it to stringResource(it.seriesTypeRes()) },
        selected = draft.animeSeriesType,
        onSelect = { value -> actions.onEdit { it.copy(animeSeriesType = value) } },
    )
    ChoicePicker(
        title = stringResource(R.string.advanced_profile),
        choices = choices.profiles.map { it.id to it.label },
        selected = draft.animeProfileId,
        onSelect = { id -> actions.onEdit { it.copy(animeProfileId = id) } },
    )
    ChoicePicker(
        title = stringResource(R.string.advanced_root_folder),
        choices = choices.rootFolders.map { it to it },
        selected = draft.animeRootFolder,
        onSelect = { path -> actions.onEdit { it.copy(animeRootFolder = path) } },
    )
    TagChips(choices.tags, draft.animeTagIds.orEmpty(), enabled) { id ->
        actions.onEdit { it.copy(animeTagIds = it.animeTagIds.orEmpty().toggled(id)) }
    }
}

@Composable
private fun FlagSwitches(
    draft: DvrForm,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    EditorSectionTitle(stringResource(R.string.server_settings_dvr_behaviour))
    EditorSwitchRow(stringResource(R.string.server_settings_dvr_default), draft.isDefault, enabled = enabled) { value ->
        actions.onEdit { it.copy(isDefault = value) }
    }
    EditorSwitchRow(stringResource(R.string.server_settings_dvr_4k), draft.is4k, enabled = enabled) { value ->
        actions.onEdit { it.copy(is4k = value) }
    }
    EditorSwitchRow(stringResource(R.string.server_settings_dvr_sync), draft.syncEnabled, enabled = enabled) { value ->
        actions.onEdit { it.copy(syncEnabled = value) }
    }
    EditorSwitchRow(stringResource(R.string.server_settings_dvr_prevent_search), draft.preventSearch, enabled = enabled) { value ->
        actions.onEdit { it.copy(preventSearch = value) }
    }
    EditorSwitchRow(stringResource(R.string.server_settings_dvr_tag_requests), draft.tagRequests, enabled = enabled) { value ->
        actions.onEdit { it.copy(tagRequests = value) }
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
        contentColor = MaterialTheme.colorScheme.error,
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
