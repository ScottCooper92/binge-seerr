package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import io.github.scottcooper92.binge.seerr.R
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
    ) { draft, enabled ->
        RuleInstance(extras, draft, enabled, ruleActions.onSelectInstance)
        RuleConditions(extras, draft, enabled, actions, ruleActions.onToggleUser)
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
    onToggleUser: (Int) -> Unit,
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
                    onToggle = onToggleUser,
                ),
                textSettingItem(
                    icon = Icons.Filled.Category,
                    label = stringResource(R.string.server_settings_rule_genres),
                    value = draft.genres,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(genres = value) } },
                    keyboard = VerbatimKeyboard,
                    emptyLabel = any,
                    hint = stringResource(R.string.server_settings_rule_genres_hint),
                ),
                textSettingItem(
                    icon = Icons.Filled.Translate,
                    label = stringResource(R.string.server_settings_rule_languages),
                    value = draft.languages,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(languages = value) } },
                    keyboard = VerbatimKeyboard,
                    emptyLabel = any,
                    hint = stringResource(R.string.server_settings_rule_languages_hint),
                ),
                textSettingItem(
                    icon = Icons.Filled.Key,
                    label = stringResource(R.string.server_settings_rule_keywords),
                    value = draft.keywords,
                    enabled = enabled,
                    onChange = { value -> actions.onEdit { it.copy(keywords = value) } },
                    keyboard = VerbatimKeyboard,
                    emptyLabel = any,
                    hint = stringResource(R.string.server_settings_rule_keywords_hint),
                ),
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
