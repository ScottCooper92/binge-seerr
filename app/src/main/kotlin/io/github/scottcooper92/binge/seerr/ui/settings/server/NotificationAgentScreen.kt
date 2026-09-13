package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeOutlinedButton
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.ChoicePicker
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSectionTitle
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSwitchRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.NotificationType
import io.github.scottcooper92.binge.seerr.ui.users.settings.labelRes
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

/** What the agent page does beside the editor: change a field, the switch or a type, and send a test. */
class AgentActions(
    val onSetEnabled: (Boolean) -> Unit,
    val onSetOption: (AgentOption, String) -> Unit,
    val onToggleType: (Int) -> Unit,
    val onTest: () -> Unit,
)

/**
 * One agent's page: its switch, its own options as the kind of each asks, the events it is sent
 * as two groups of chips, and a test that sends through the draft as typed.
 */
@Composable
fun NotificationAgentScreen(
    state: EditorUiState<AgentForm>,
    extras: AgentExtras,
    events: Flow<EditorEvent>,
    actions: EditorActions<AgentForm>,
    agentActions: AgentActions,
    agent: ServerAgent,
) {
    EditorPage(
        title = stringResource(agent.labelRes()),
        state = state,
        events = events,
        actions = actions,
        canSave = { it.valid },
    ) { draft, enabled ->
        EditorSwitchRow(
            stringResource(R.string.user_settings_agent_enabled),
            draft.enabled,
            enabled = enabled,
            onToggle = agentActions.onSetEnabled,
        )
        OptionFields(draft, extras, enabled, agentActions)
        TypeChips(draft, enabled, agentActions.onToggleType)
        BingeOutlinedButton(
            label = stringResource(R.string.server_settings_agent_test),
            onClick = agentActions.onTest,
            enabled = enabled && draft.valid,
            loading = extras.testing,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OptionFields(
    draft: AgentForm,
    extras: AgentExtras,
    enabled: Boolean,
    actions: AgentActions,
) {
    val options = AgentOption.of(draft.agent)
    if (options.isNotEmpty()) EditorSectionTitle(stringResource(R.string.server_settings_agent_section_options))
    options.forEach { option ->
        when {
            option == AgentOption.PushoverSound && extras.sounds.isNotEmpty() ->
                ChoicePicker(
                    title = stringResource(option.labelRes()),
                    choices = extras.sounds.map { it.name to it.description },
                    selected = draft.option(option).takeIf { it.isNotEmpty() },
                    onSelect = { name -> actions.onSetOption(option, name) },
                )
            option.kind == OptionKind.Switch ->
                EditorSwitchRow(stringResource(option.labelRes()), draft.switched(option), enabled = enabled) { value ->
                    actions.onSetOption(option, value.toString())
                }
            else ->
                EditorTextField(
                    draft.option(option),
                    stringResource(option.labelRes()),
                    enabled = enabled,
                    secret = option.secret,
                    singleLine = option.kind != OptionKind.Multiline,
                    keyboardType =
                        when (option.kind) {
                            OptionKind.Number -> KeyboardType.Number
                            OptionKind.Uri -> KeyboardType.Uri
                            else -> KeyboardType.Text
                        },
                    supporting =
                        if (option ==
                            AgentOption.WebhookJsonPayload
                        ) {
                            stringResource(R.string.server_settings_agent_json_payload_hint)
                        } else {
                            null
                        },
                    isError = draft.enabled && option.required && draft.option(option).isBlank(),
                ) { value -> actions.onSetOption(option, value) }
        }
    }
}

@Composable
private fun TypeChips(
    draft: AgentForm,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
    EditorSectionTitle(stringResource(R.string.user_settings_types_title))
    listOf(false, true).forEach { issues ->
        Text(
            stringResource(if (issues) R.string.permission_group_issues else R.string.permission_group_requests),
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            NotificationType.entries.filter { it.isIssue == issues }.forEach { type ->
                BingeFilterChip(
                    label = stringResource(type.labelRes()),
                    selected = draft.types and type.bit != 0,
                    onClick = { if (enabled) onToggle(type.bit) },
                )
            }
        }
    }
}

private val NotificationType.isIssue: Boolean get() = name.startsWith("Issue")
