package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.choiceSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.multiChoiceSettingItem

/**
 * Where a request lands, in the web client's order. The profiles, folders, language profiles and tags are the
 * instance's own, so until a test has answered those rows show what is saved and can't be opened.
 */
@Composable
internal fun DestinationGroup(
    draft: DvrForm,
    choices: DvrChoices?,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    ItemGroup(
        title = stringResource(R.string.server_settings_dvr_destination),
        rows =
            listOfNotNull(
                draft.seriesType?.takeIf { draft.type == ServiceType.Sonarr }?.let { type ->
                    seriesTypeItem(type, enabled) { value -> actions.onEdit { it.copy(seriesType = value) } }
                },
                testedChoiceItem(
                    Icons.Filled.HighQuality,
                    stringResource(R.string.advanced_profile),
                    choices?.profiles?.map { it.id to it.label },
                    draft.profileId,
                    draft.profileName,
                    enabled,
                ) { id -> actions.onEdit { it.copy(profileId = id) } },
                testedChoiceItem(
                    Icons.Filled.Folder,
                    stringResource(R.string.advanced_root_folder),
                    choices?.rootFolders?.map { it to it },
                    draft.rootFolder,
                    draft.rootFolder,
                    enabled,
                ) { path -> actions.onEdit { it.copy(rootFolder = path) } },
                draft.minimumAvailability?.takeIf { draft.type == ServiceType.Radarr }?.let { availability ->
                    choiceSettingItem(
                        icon = Icons.Filled.Event,
                        title = stringResource(R.string.server_settings_dvr_minimum_availability),
                        choices = MINIMUM_AVAILABILITIES.map { it to stringResource(it.availabilityRes()) },
                        choiceIcon = { availability ->
                            when (availability) {
                                "announced" -> Icons.Filled.Campaign
                                "inCinemas" -> Icons.Filled.Theaters
                                else -> Icons.Filled.CheckCircle
                            }
                        },
                        selected = availability,
                        enabled = enabled,
                    ) { value -> actions.onEdit { it.copy(minimumAvailability = value) } }
                },
                languageProfileItem(
                    stringResource(R.string.server_settings_dvr_language_profile),
                    choices,
                    draft.languageProfileId,
                    enabled,
                ) { id -> actions.onEdit { it.copy(languageProfileId = id) } }.takeIf { draft.type == ServiceType.Sonarr },
                tagsItem(choices, draft.tagIds, enabled) { id -> actions.onEdit { it.copy(tagIds = it.tagIds.toggled(id)) } },
            ),
    )
}

/** A Sonarr's destination for anime, which the web client keeps apart from the standard one. */
@Composable
internal fun AnimeGroup(
    draft: DvrForm,
    choices: DvrChoices?,
    enabled: Boolean,
    actions: EditorActions<DvrForm>,
) {
    ItemGroup(
        title = stringResource(R.string.server_settings_dvr_anime),
        rows =
            listOfNotNull(
                draft.animeSeriesType?.let { type ->
                    seriesTypeItem(type, enabled) { value -> actions.onEdit { it.copy(animeSeriesType = value) } }
                },
                testedChoiceItem(
                    Icons.Filled.HighQuality,
                    stringResource(R.string.advanced_profile),
                    choices?.profiles?.map { it.id to it.label },
                    draft.animeProfileId,
                    draft.animeProfileName,
                    enabled,
                ) { id -> actions.onEdit { it.copy(animeProfileId = id) } },
                testedChoiceItem(
                    Icons.Filled.Folder,
                    stringResource(R.string.advanced_root_folder),
                    choices?.rootFolders?.map { it to it },
                    draft.animeRootFolder,
                    draft.animeRootFolder,
                    enabled,
                ) { path -> actions.onEdit { it.copy(animeRootFolder = path) } },
                languageProfileItem(
                    stringResource(R.string.server_settings_dvr_anime_language_profile),
                    choices,
                    draft.animeLanguageProfileId,
                    enabled,
                ) { id -> actions.onEdit { it.copy(animeLanguageProfileId = id) } },
                tagsItem(choices, draft.animeTagIds.orEmpty(), enabled) { id ->
                    actions.onEdit { it.copy(animeTagIds = it.animeTagIds.orEmpty().toggled(id)) }
                },
            ),
    )
}

/**
 * A pick from the instance's own list: a picker row once a test has answered, and before that a row that names what
 * is saved, [savedLabel], and asks for the test.
 */
@Composable
private fun <T> testedChoiceItem(
    icon: ImageVector,
    title: String,
    choices: List<Pair<T, String>>?,
    selected: T?,
    savedLabel: String?,
    enabled: Boolean,
    onSelect: (T) -> Unit,
): ListItem =
    if (choices == null) {
        untestedItem(icon, title, savedLabel)
    } else {
        choiceSettingItem(icon, title, choices, selected, enabled, onSelect = onSelect)
    }

/** A row whose choices aren't known yet: it names [savedLabel], or what has to happen first, and can't be opened. */
@Composable
internal fun untestedItem(
    icon: ImageVector,
    title: String,
    savedLabel: String? = null,
    waitingFor: String = stringResource(R.string.server_settings_dvr_test_first),
) = ListItem(
    icon = icon,
    label = title,
    detail = savedLabel ?: waitingFor,
    clickable = false,
    disabled = true,
)

/** A Sonarr's language profiles; null on a Sonarr v4, which dropped them, and so no row. */
@Composable
private fun languageProfileItem(
    title: String,
    choices: DvrChoices?,
    selected: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
): ListItem? =
    when {
        choices == null -> untestedItem(Icons.Filled.Translate, title)
        choices.languageProfiles == null -> null
        else ->
            choiceSettingItem(
                Icons.Filled.Translate,
                title,
                choices.languageProfiles.map { it.id to it.label },
                selected,
                enabled,
                onSelect = onSelect,
            )
    }

@Composable
private fun tagsItem(
    choices: DvrChoices?,
    selected: Set<Int>,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
): ListItem {
    val title = stringResource(R.string.request_tags)
    return if (choices == null) {
        untestedItem(Icons.AutoMirrored.Filled.Label, title)
    } else {
        multiChoiceSettingItem(
            icon = Icons.AutoMirrored.Filled.Label,
            title = title,
            choices = choices.tags.map { it.id to it.label },
            selected = selected,
            enabled = enabled,
            emptyLabel = stringResource(R.string.server_settings_dvr_tags_none),
            onToggle = onToggle,
        )
    }
}

@Composable
private fun seriesTypeItem(
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) = choiceSettingItem(
    icon = Icons.Filled.Category,
    title = stringResource(R.string.server_settings_dvr_series_type),
    choices = SERIES_TYPES.map { it to stringResource(it.seriesTypeRes()) },
    choiceIcon = { type ->
        when (type) {
            "daily" -> Icons.Filled.Today
            "anime" -> Icons.Filled.Animation
            else -> Icons.Filled.Tv
        }
    },
    selected = selected,
    enabled = enabled,
    onSelect = onSelect,
)

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
