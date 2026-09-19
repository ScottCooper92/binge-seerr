package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.ChoicePicker
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSectionTitle
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow

class OverrideRuleActions(
    val onSelectInstance: (DvrSummary) -> Unit,
    val onToggleUser: (Int) -> Unit,
    val onToggleTag: (Int) -> Unit,
    val onDelete: () -> Unit,
)

/** One override rule: the instance it applies to, the conditions a request must meet, and the overrides it gets. */
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
        if (draft.id != null) DeleteButton(ruleActions.onDelete)
    }
}

/** Which DVR instance the rule applies to; the overrides below cannot be picked until this is. */
@Composable
private fun ColumnScope.RuleInstance(
    extras: OverrideRuleExtras,
    draft: OverrideRuleForm,
    enabled: Boolean,
    onSelectInstance: (DvrSummary) -> Unit,
) {
    val separator = stringResource(R.string.hub_meta_separator)
    ChoicePicker(
        title = stringResource(R.string.advanced_server),
        choices = extras.instances.map { it to "${it.type.name}$separator${it.name}" },
        selected = extras.instances.firstOrNull { it.type == draft.serviceType && it.id == draft.serviceId },
        onSelect = onSelectInstance,
        enabled = enabled,
    )
}

/** What a request must match for the rule to fire: who asked, and what the title is. */
@Composable
private fun ColumnScope.RuleConditions(
    extras: OverrideRuleExtras,
    draft: OverrideRuleForm,
    enabled: Boolean,
    actions: EditorActions<OverrideRuleForm>,
    onToggleUser: (Int) -> Unit,
) {
    EditorSectionTitle(stringResource(R.string.server_settings_rule_conditions_title))
    if (extras.users.isNotEmpty()) {
        Text(stringResource(R.string.server_settings_rule_users), style = MaterialTheme.typography.titleSmall)
        extras.users.forEach { user ->
            FilterChip(
                selected = user.id in draft.userIds,
                onClick = { onToggleUser(user.id) },
                enabled = enabled,
                label = { Text(user.label) },
            )
        }
    }
    EditorTextField(
        draft.genres,
        stringResource(R.string.server_settings_rule_genres),
        enabled = enabled,
        supporting = stringResource(R.string.server_settings_rule_genres_hint),
    ) { value -> actions.onEdit { it.copy(genres = value) } }
    EditorTextField(
        draft.languages,
        stringResource(R.string.server_settings_rule_languages),
        enabled = enabled,
        supporting = stringResource(R.string.server_settings_rule_languages_hint),
    ) { value -> actions.onEdit { it.copy(languages = value) } }
    EditorTextField(
        draft.keywords,
        stringResource(R.string.server_settings_rule_keywords),
        enabled = enabled,
        supporting = stringResource(R.string.server_settings_rule_keywords_hint),
    ) { value -> actions.onEdit { it.copy(keywords = value) } }
}

/**
 * What a matching request gets instead. The choices are the instance's own, so until one is picked
 * and tested there is nothing to offer and the line below says which of the two is missing.
 */
@Composable
private fun ColumnScope.RuleOverrides(
    extras: OverrideRuleExtras,
    draft: OverrideRuleForm,
    enabled: Boolean,
    actions: EditorActions<OverrideRuleForm>,
    onToggleTag: (Int) -> Unit,
) {
    EditorSectionTitle(stringResource(R.string.server_settings_rule_overrides_title))
    if (extras.loadingChoices) LinearProgressIndicator(Modifier.fillMaxWidth())
    val choices = extras.choices
    if (choices == null) {
        val reason =
            if (draft.serviceId == null) R.string.server_settings_rule_pick_instance else R.string.server_settings_dvr_untested
        Text(
            stringResource(reason),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    ChoicePicker(
        title = stringResource(R.string.advanced_profile),
        choices = choices.profiles.map { it.id to it.label },
        selected = draft.profileId,
        onSelect = { id -> actions.onEdit { it.copy(profileId = if (it.profileId == id) null else id) } },
        enabled = enabled,
    )
    ChoicePicker(
        title = stringResource(R.string.advanced_root_folder),
        choices = choices.rootFolders.map { it to it },
        selected = draft.rootFolder,
        onSelect = { path -> actions.onEdit { it.copy(rootFolder = if (it.rootFolder == path) null else path) } },
        enabled = enabled,
    )
    TagChips(choices.tags, draft.tagIds, enabled, onToggleTag)
}
