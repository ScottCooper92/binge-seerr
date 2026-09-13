package io.github.scottcooper92.binge.seerr.ui.settings.server

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
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
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
    state: EditorUiState<OverrideRuleForm>,
    extras: OverrideRuleExtras,
    events: Flow<EditorEvent>,
    actions: EditorActions<OverrideRuleForm>,
    ruleActions: OverrideRuleActions,
) {
    EditorPage(
        title = stringResource(R.string.server_settings_rule_title),
        state = state,
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        ChoicePicker(
            title = stringResource(R.string.advanced_server),
            choices = extras.instances.map { it to "${it.type.name} · ${it.name}" },
            selected = extras.instances.firstOrNull { it.type == draft.serviceType && it.id == draft.serviceId },
            onSelect = ruleActions.onSelectInstance,
        )
        EditorSectionTitle(stringResource(R.string.server_settings_rule_conditions_title))
        if (extras.users.isNotEmpty()) {
            Text(stringResource(R.string.server_settings_rule_users), style = MaterialTheme.typography.titleSmall)
            extras.users.forEach { user ->
                FilterChip(
                    selected = user.id in draft.userIds,
                    onClick = { ruleActions.onToggleUser(user.id) },
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
        EditorSectionTitle(stringResource(R.string.server_settings_rule_overrides_title))
        if (extras.loadingChoices) LinearProgressIndicator(Modifier.fillMaxWidth())
        val choices = extras.choices
        if (choices == null) {
            Text(
                stringResource(
                    if (draft.serviceId ==
                        null
                    ) {
                        R.string.server_settings_rule_pick_instance
                    } else {
                        R.string.server_settings_dvr_untested
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ChoicePicker(
                title = stringResource(R.string.advanced_profile),
                choices = choices.profiles.map { it.id to it.label },
                selected = draft.profileId,
                onSelect = { id -> actions.onEdit { it.copy(profileId = if (it.profileId == id) null else id) } },
            )
            ChoicePicker(
                title = stringResource(R.string.advanced_root_folder),
                choices = choices.rootFolders.map { it to it },
                selected = draft.rootFolder,
                onSelect = { path -> actions.onEdit { it.copy(rootFolder = if (it.rootFolder == path) null else path) } },
            )
            TagChips(choices.tags, draft.tagIds, enabled, ruleActions.onToggleTag)
        }
        if (draft.id != null) DeleteButton(ruleActions.onDelete)
    }
}
