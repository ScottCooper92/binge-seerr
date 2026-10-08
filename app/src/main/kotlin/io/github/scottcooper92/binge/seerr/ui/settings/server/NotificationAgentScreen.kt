package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.NotificationType
import io.github.scottcooper92.binge.seerr.ui.users.settings.choiceSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import io.github.scottcooper92.binge.seerr.ui.users.settings.labelRes
import io.github.scottcooper92.binge.seerr.ui.users.settings.textSettingItem
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow

/** What the agent page does beside the editor: change a field, the switch or a type, and send a test. */
class AgentActions(
    val onSetEnabled: (Boolean) -> Unit,
    val onSetOption: (AgentOption, String) -> Unit,
    val onSetEncryption: (EmailEncryption) -> Unit,
    val onToggleType: (Int) -> Unit,
    val onTest: () -> Unit,
)

/**
 * One agent's page as the web client lays it out, in groups of list rows: its switch, the settings it can't send
 * without, the rest, the events it's sent, and a row that sends a test through the draft as typed. A required setting
 * left blank while the agent is on says so in its row, and Save stays off until none is.
 */
@Composable
fun NotificationAgentScreen(
    state: ExtrasEditorUiState<AgentForm, AgentExtras>,
    events: Flow<EditorEvent>,
    actions: EditorActions<AgentForm>,
    agentActions: AgentActions,
    agent: ServerAgent,
) {
    val extras = (state as? ExtrasEditorUiState.Ready<AgentForm, AgentExtras>)?.extras ?: AgentExtras()
    EditorPage(
        title = stringResource(agent.labelRes()),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        ItemGroup(
            title = null,
            rows =
                listOf(
                    editorToggle(
                        Icons.Filled.Notifications,
                        stringResource(R.string.user_settings_agent_enabled),
                        draft.enabled,
                        enabled,
                        onToggle = agentActions.onSetEnabled,
                    ),
                ),
        )
        OptionGroup(required = true, draft, extras, enabled, agentActions)
        OptionGroup(required = false, draft, extras, enabled, agentActions)
        TypeGroups(draft, enabled, agentActions.onToggleType)
        ItemGroup(
            title = null,
            rows =
                listOf(
                    ListItem(
                        icon = Icons.Filled.Send,
                        label = stringResource(R.string.server_settings_agent_test),
                        loading = extras.testing,
                        clickable = enabled && draft.valid && !extras.testing,
                        disabled = !enabled || !draft.valid,
                        onClick = agentActions.onTest,
                    ),
                ),
        )
    }
}

/** The agent's options it can't send without ([required]), or the rest; no group when it has none of that kind. */
@Composable
internal fun OptionGroup(
    required: Boolean,
    draft: AgentForm,
    extras: AgentExtras,
    enabled: Boolean,
    actions: AgentActions,
) {
    val options = AgentOption.of(draft.agent).filter { it.ownControl && it.required == required }
    if (options.isEmpty()) return
    ItemGroup(
        title =
            stringResource(
                if (required) R.string.server_settings_agent_section_options else R.string.server_settings_agent_section_more,
            ),
        rows = options.map { option -> optionItem(option, draft, extras, enabled, actions) },
    )
}

@Composable
private fun optionItem(
    option: AgentOption,
    draft: AgentForm,
    extras: AgentExtras,
    enabled: Boolean,
    actions: AgentActions,
): ListItem {
    // An option nothing reads follows the switch that would read it. The switches themselves
    // stay live: exclusivity is enforced by turning the other one off, not by refusing this one.
    val editable = enabled && option.gatedBy?.let { draft.switched(it) } != false
    val label = stringResource(option.labelRes())
    return when {
        option == AgentOption.EmailSecure ->
            choiceSettingItem(
                icon = Icons.Filled.Tune,
                title = label,
                choices = EmailEncryption.entries.map { it to stringResource(it.labelRes()) },
                selected = EmailEncryption.of(draft),
                enabled = editable,
                onSelect = actions.onSetEncryption,
            )
        option == AgentOption.PushoverSound && extras.sounds.isNotEmpty() ->
            choiceSettingItem(
                icon = Icons.Filled.Tune,
                title = label,
                choices = extras.sounds.map { it.name to it.description },
                selected = draft.option(option).takeIf { it.isNotEmpty() },
                enabled = editable,
            ) { name -> actions.onSetOption(option, name) }
        option.kind == OptionKind.Switch ->
            editorToggle(Icons.Filled.Tune, label, draft.switched(option), enabled) { on -> actions.onSetOption(option, on.toString()) }
        else -> textOptionItem(option, label, draft, editable, actions)
    }
}

@Composable
private fun textOptionItem(
    option: AgentOption,
    label: String,
    draft: AgentForm,
    editable: Boolean,
    actions: AgentActions,
): ListItem {
    val value = draft.option(option)
    val wrongShape = stringResource(if (option.port) R.string.editor_error_port else R.string.editor_error_whole_number)
    return textSettingItem(
        icon = Icons.Filled.Tune,
        label = label,
        value = value,
        enabled = editable,
        onChange = { typed -> actions.onSetOption(option, typed) },
        hint = option.hintRes()?.let { stringResource(it) },
        placeholder = option.placeholderRes()?.let { stringResource(it) },
        required = option.required && draft.enabled,
        check = { typed -> wrongShape.takeIf { typed.isNotBlank() && !option.satisfiedBy(typed) } },
        shown =
            if (option.secret && value.isNotEmpty()) {
                stringResource(R.string.server_settings_secret_set)
            } else {
                value.ifBlank { stringResource(R.string.settings_value_not_set) }
            },
        secret = option.secret,
        multiline = option.kind == OptionKind.Multiline,
    )
}

/** The events the agent is sent, as the web client's checklist: the request ones, then the issue ones. */
@Composable
private fun TypeGroups(
    draft: AgentForm,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
    listOf(false, true).forEach { issues ->
        ItemGroup(
            title =
                stringResource(
                    if (issues) R.string.server_settings_agent_types_issues else R.string.server_settings_agent_types_requests,
                ),
            rows =
                NotificationType.entries.filter { it.isIssue == issues }.map { type ->
                    editorToggle(Icons.Filled.Notifications, stringResource(type.labelRes()), draft.types and type.bit != 0, enabled) {
                        onToggle(type.bit)
                    }
                },
        )
    }
}

private val NotificationType.isIssue: Boolean get() = name.startsWith("Issue")
