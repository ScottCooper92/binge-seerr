package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.GroupMessage
import io.github.scottcooper92.binge.seerr.ui.users.settings.VerbatimKeyboard
import io.github.scottcooper92.binge.seerr.ui.users.settings.choiceSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.multiChoiceSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.textSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow

class OverrideRuleActions(
    val onSelectInstance: (DvrSummary) -> Unit,
    val onToggleUser: (Int) -> Unit,
    val onToggleTag: (Int) -> Unit,
    val onToggleGenre: (Int) -> Unit,
    val onLoadLanguages: () -> Unit,
    val onSelectLanguages: (String) -> Unit,
    val onToggleKeyword: (Int) -> Unit,
    val onSearchKeywords: (String) -> Unit,
    val onLoadKeywordNames: (List<Int>) -> Unit,
    val onDelete: () -> Unit,
)

/**
 * One override rule as the web client lays it out, in groups of list rows: the instance it applies to, the conditions
 * a request must meet, and what it gets instead. All three are required, as in Seerr's web client (#733), so a group
 * that is still short says what it needs and Save stays off until none is.
 */
@Composable
fun OverrideRuleScreen(
    state: ExtrasEditorUiState<OverrideRuleForm, OverrideRuleExtras>,
    events: Flow<EditorEvent>,
    actions: EditorActions<OverrideRuleForm>,
    ruleActions: OverrideRuleActions,
) {
    val extras = (state as? ExtrasEditorUiState.Ready<OverrideRuleForm, OverrideRuleExtras>)?.extras ?: OverrideRuleExtras()
    EditorPage(
        title = stringResource(R.string.server_settings_rule_title),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        canSave = { it.valid },
        footerCommit = true,
        commitLabel = (state as? ExtrasEditorUiState.Ready)?.takeIf { it.draft.id == null }?.let { stringResource(R.string.editor_create) },
    ) { draft, enabled ->
        RuleInstance(extras, draft, enabled, ruleActions.onSelectInstance)
        RuleConditions(extras, draft, enabled, actions, ruleActions)
        RuleOverrides(extras, draft, enabled, actions, ruleActions.onToggleTag)
        if (draft.id != null) DeleteGroup(ruleActions.onDelete)
    }
}

/** Which DVR instance the rule applies to; the overrides below can't be picked until this is. */
@Composable
internal fun RuleInstance(
    extras: OverrideRuleExtras,
    draft: OverrideRuleForm,
    enabled: Boolean,
    onSelectInstance: (DvrSummary) -> Unit,
) {
    val separator = stringResource(R.string.hub_meta_separator)
    ItemGroup(
        title = stringResource(R.string.server_settings_rule_applies_to),
        rows =
            listOf(
                choiceSettingItem(
                    icon = Icons.Filled.Storage,
                    title = stringResource(R.string.advanced_server),
                    choices = extras.instances.map { it to "${it.type.name}$separator${it.name}" },
                    choiceIcon = { instance -> if (instance.type == ServiceType.Radarr) Icons.Filled.Movie else Icons.Filled.Tv },
                    selected = extras.instances.firstOrNull { it.type == draft.serviceType && it.id == draft.serviceId },
                    enabled = enabled,
                    onSelect = onSelectInstance,
                ),
            ),
    )
}

/** What a request must match for the rule to fire: who asked, and what the title is. At least one is needed. */
@Composable
internal fun RuleConditions(
    extras: OverrideRuleExtras,
    draft: OverrideRuleForm,
    enabled: Boolean,
    actions: EditorActions<OverrideRuleForm>,
    ruleActions: OverrideRuleActions,
) {
    val any = stringResource(R.string.server_settings_rule_any)
    ItemGroup(
        title = stringResource(R.string.server_settings_rule_conditions_title),
        rows =
            listOf(
                multiChoiceSettingItem(
                    icon = Icons.Filled.Group,
                    title = stringResource(R.string.server_settings_rule_users),
                    choices = extras.users.map { it.id to it.label },
                    selected = draft.userIds,
                    enabled = enabled,
                    emptyLabel = any,
                    onToggle = ruleActions.onToggleUser,
                ),
                genreItem(extras, draft, enabled, actions, ruleActions.onToggleGenre),
                languageSettingItem(
                    icon = Icons.Filled.Translate,
                    title = stringResource(R.string.server_settings_rule_languages),
                    // The rule keeps codes joined by commas and the sheet by `|`; the edge maps, and what is saved is unchanged.
                    value = draft.languages.replace(',', '|'),
                    choices = extras.lists[ServerList.Languages],
                    enabled = enabled,
                    onOpen = ruleActions.onLoadLanguages,
                    onSelect = ruleActions.onSelectLanguages,
                ),
                keywordItem(extras, draft, enabled, ruleActions, any),
            ),
        belowRows =
            if (draft.hasCondition) {
                null
            } else {
                { GroupMessage(stringResource(R.string.server_settings_rule_needs_condition), error = false) }
            },
    )
}

/**
 * The genres the instance's kind matches, as a checklist of names. Until an instance is picked there is no kind to ask
 * for; a server that cannot send the list gets typed ids back, so the condition stays in reach.
 */
@Composable
private fun genreItem(
    extras: OverrideRuleExtras,
    draft: OverrideRuleForm,
    enabled: Boolean,
    actions: EditorActions<OverrideRuleForm>,
    onToggle: (Int) -> Unit,
): ListItem {
    val title = stringResource(R.string.server_settings_rule_genres)
    val any = stringResource(R.string.server_settings_rule_any)
    val genres = extras.genres
    return when {
        draft.serviceType == null ->
            untestedItem(
                Icons.Filled.Category,
                title,
                savedLabel = draft.genres.ifBlank { null },
                waitingFor = stringResource(R.string.server_settings_rule_pick_instance),
            )
        genres is GenreChoices.Ready ->
            multiChoiceSettingItem(
                icon = Icons.Filled.Category,
                title = title,
                choices = genreChecklist(genres.genres, draft.genres.tagIds().toSet()).map { it.id to it.label },
                selected = draft.genres.tagIds().toSet(),
                enabled = enabled,
                emptyLabel = any,
                onToggle = onToggle,
            )
        genres is GenreChoices.Failed ->
            textSettingItem(
                icon = Icons.Filled.Category,
                label = title,
                value = draft.genres,
                enabled = enabled,
                onChange = { value -> actions.onEdit { it.copy(genres = value) } },
                keyboard = VerbatimKeyboard,
                emptyLabel = any,
                hint = stringResource(R.string.server_settings_rule_genres_hint),
            )
        else -> untestedItem(Icons.Filled.Category, title, savedLabel = draft.genres.ifBlank { null }).copy(loading = true)
    }
}

/** The keywords a rule matches, named, with TMDB's keyword search over the rule to change them. */
@Composable
private fun keywordItem(
    extras: OverrideRuleExtras,
    draft: OverrideRuleForm,
    enabled: Boolean,
    ruleActions: OverrideRuleActions,
    any: String,
): ListItem {
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    val title = stringResource(R.string.server_settings_rule_keywords)
    val chosen = draft.keywords.tagIds()
    if (open) {
        KeywordPickerDialog(
            title = title,
            chosen = chosen,
            search = extras.keywords,
            onSearch = ruleActions.onSearchKeywords,
            onToggle = ruleActions.onToggleKeyword,
            onDismiss = { open = false },
        )
    }
    return keywordSettingItem(
        icon = Icons.Filled.Key,
        title = title,
        chosen = chosen,
        names = extras.keywords.names,
        enabled = enabled,
        onLoadNames = ruleActions.onLoadKeywordNames,
        onOpen = { open = true },
        emptyLabel = any,
    )
}

/**
 * What a matching request gets instead; at least one is needed. The choices are the instance's own, so until one is
 * picked and has answered, the rows say which of the two is still missing.
 */
@Composable
internal fun RuleOverrides(
    extras: OverrideRuleExtras,
    draft: OverrideRuleForm,
    enabled: Boolean,
    actions: EditorActions<OverrideRuleForm>,
    onToggleTag: (Int) -> Unit,
) {
    val choices = extras.choices
    val profileTitle = stringResource(R.string.advanced_profile)
    val folderTitle = stringResource(R.string.advanced_root_folder)
    val tagsTitle = stringResource(R.string.request_tags)
    val rows =
        if (choices == null) {
            val waiting =
                stringResource(
                    if (draft.serviceId ==
                        null
                    ) {
                        R.string.server_settings_rule_pick_instance
                    } else {
                        R.string.server_settings_dvr_test_first
                    },
                )
            listOf(
                untestedItem(Icons.Filled.HighQuality, profileTitle, waitingFor = waiting),
                untestedItem(Icons.Filled.Folder, folderTitle, waitingFor = waiting),
                untestedItem(Icons.AutoMirrored.Filled.Label, tagsTitle, waitingFor = waiting),
            ).map { it.copy(loading = extras.loadingChoices) }
        } else {
            val unchanged = stringResource(R.string.server_settings_rule_unchanged)
            listOf(
                choiceSettingItem(
                    icon = Icons.Filled.HighQuality,
                    title = profileTitle,
                    choices = listOf<Pair<Int?, String>>(null to unchanged) + choices.profiles.map { it.id to it.label },
                    selected = draft.profileId,
                    enabled = enabled,
                ) { id -> actions.onEdit { it.copy(profileId = id) } },
                choiceSettingItem(
                    icon = Icons.Filled.Folder,
                    title = folderTitle,
                    choices = listOf<Pair<String?, String>>(null to unchanged) + choices.rootFolders.map { it to it },
                    selected = draft.rootFolder,
                    enabled = enabled,
                ) { path -> actions.onEdit { it.copy(rootFolder = path) } },
                multiChoiceSettingItem(
                    icon = Icons.AutoMirrored.Filled.Label,
                    title = tagsTitle,
                    choices = choices.tags.map { it.id to it.label },
                    selected = draft.tagIds,
                    enabled = enabled,
                    emptyLabel = unchanged,
                    onToggle = onToggleTag,
                ),
            )
        }
    ItemGroup(
        title = stringResource(R.string.server_settings_rule_overrides_title),
        rows = rows,
        belowRows =
            if (draft.hasOverride) {
                null
            } else {
                { GroupMessage(stringResource(R.string.server_settings_rule_needs_override), error = false) }
            },
    )
}
