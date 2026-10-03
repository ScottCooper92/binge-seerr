package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.ChoicePicker
import io.github.scottcooper92.binge.seerr.ui.ChoiceRow
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorFieldIssueText
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSection
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorToggleRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorField
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle

/** Empty until a test answered: the pickers show what the instance offers, and nothing else is offered. */
@Composable
internal fun DestinationFields(
    draft: DvrForm,
    choices: DvrChoices?,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    EditorSection(DvrSections.DESTINATION, stringResource(R.string.server_settings_dvr_destination)) {
        if (choices == null) {
            Text(
                stringResource(R.string.server_settings_dvr_untested),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            DestinationChoices(draft, choices, enabled, actions)
        }
    }
    if (choices != null && draft.type == ServiceType.Sonarr) AnimeFields(draft, choices, enabled, actions)
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
    Column(modifier = Modifier.editorField(DvrFields.PROFILE, takesFocus = false)) {
        ChoiceRow(
            title = stringResource(R.string.advanced_profile),
            choices = choices.profiles.map { it.id to it.label },
            selected = draft.profileId,
            onSelect = { id -> actions.onEdit { it.copy(profileId = id) } },
            enabled = enabled,
        )
        EditorFieldIssueText(DvrFields.PROFILE)
    }
    Column(modifier = Modifier.editorField(DvrFields.ROOT_FOLDER, takesFocus = false)) {
        ChoiceRow(
            title = stringResource(R.string.advanced_root_folder),
            choices = choices.rootFolders.map { it to it },
            selected = draft.rootFolder,
            onSelect = { path -> actions.onEdit { it.copy(rootFolder = path) } },
            enabled = enabled,
        )
        EditorFieldIssueText(DvrFields.ROOT_FOLDER)
    }
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
) = EditorSection(DvrSections.ANIME, stringResource(R.string.server_settings_dvr_anime), defaultExpanded = false) {
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
    Text(stringResource(R.string.server_settings_dvr_behaviour), style = MaterialTheme.typography.titleSmall)
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
    ).forEach { EditorToggleRow(it) }
}

/** The optional half of the connection and the instance's behaviour flags: nothing here blocks a save but a bad external URL. */
@Composable
internal fun AdvancedFields(
    draft: DvrForm,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    EditorSection(DvrSections.ADVANCED, stringResource(R.string.server_settings_dvr_advanced), defaultExpanded = false) {
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
            imeAction = ImeAction.Done,
            fieldId = DvrFields.EXTERNAL_URL,
        ) { value -> actions.onEdit { it.copy(externalUrl = value) } }
        FlagSwitches(draft, enabled, actions)
    }
}

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
