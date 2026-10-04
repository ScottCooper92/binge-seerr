package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.binge.designsystem.component.BingeFilterChip
import com.binge.designsystem.component.BingeOutlinedButton
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.ChoiceRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorActions
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorPage
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorSection
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorTextField
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorToggleGroup
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorToggleRow
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorValidation
import io.github.scottcooper92.binge.seerr.ui.users.settings.ExtrasEditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.NotificationType
import io.github.scottcooper92.binge.seerr.ui.users.settings.editorToggle
import io.github.scottcooper92.binge.seerr.ui.users.settings.labelRes
import io.github.scottcooper92.binge.seerr.ui.users.settings.toEditorUiState
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

/** What the agent page does beside the editor: change a field, the switch or a type, and send a test. */
class AgentActions(
    val onSetEnabled: (Boolean) -> Unit,
    val onSetOption: (AgentOption, String) -> Unit,
    val onSetEncryption: (EmailEncryption) -> Unit,
    val onToggleType: (Int) -> Unit,
    val onTest: () -> Unit,
)

/**
 * One agent's page: its switch, then collapsible sections (#549) for the options it cannot send
 * without, the rest of its options, and the events it is sent as two groups of chips; then a test
 * that sends through the draft as typed. The optional settings start closed.
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
    val validation = remember { EditorValidation<AgentForm>(AGENT_FORM_KEY) { it.issues() } }
    EditorPage(
        title = stringResource(agent.labelRes()),
        state = state.toEditorUiState(),
        events = events,
        actions = actions,
        validation = validation,
    ) { draft, enabled ->
        EditorToggleGroup(
            stringResource(agent.labelRes()),
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
        OptionFields(AgentSections.SETTINGS, R.string.server_settings_agent_section_options, draft, extras, enabled, agentActions)
        OptionFields(AgentSections.MORE_SETTINGS, R.string.server_settings_agent_section_more, draft, extras, enabled, agentActions)
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

/**
 * The agent's options that belong in [sectionId]: the required ones open, the rest closed until asked
 * for. [defaultExpanded] is for a frame of the section alone.
 */
@Composable
internal fun OptionFields(
    sectionId: String,
    @StringRes titleRes: Int,
    draft: AgentForm,
    extras: AgentExtras,
    enabled: Boolean,
    actions: AgentActions,
    defaultExpanded: Boolean = sectionId == AgentSections.SETTINGS,
) {
    val options = AgentOption.of(draft.agent).filter { it.ownControl && it.sectionId == sectionId }
    if (options.isEmpty()) return
    EditorSection(sectionId, stringResource(titleRes), defaultExpanded = defaultExpanded) {
        options.forEach { option -> OptionField(option, draft, extras, enabled, actions) }
    }
}

@Composable
private fun OptionField(
    option: AgentOption,
    draft: AgentForm,
    extras: AgentExtras,
    enabled: Boolean,
    actions: AgentActions,
) {
    // An option nothing reads follows the switch that would read it. The switches themselves
    // stay live: exclusivity is enforced by turning the other one off, not by refusing this one.
    val editable = enabled && option.gatedBy?.let { draft.switched(it) } != false
    when {
        option == AgentOption.EmailSecure ->
            ChoiceRow(
                title = stringResource(option.labelRes()),
                choices = EmailEncryption.entries.map { it to stringResource(it.labelRes()) },
                selected = EmailEncryption.of(draft),
                onSelect = actions.onSetEncryption,
                enabled = editable,
            )
        option == AgentOption.PushoverSound && extras.sounds.isNotEmpty() ->
            ChoiceRow(
                title = stringResource(option.labelRes()),
                choices = extras.sounds.map { it.name to it.description },
                selected = draft.option(option).takeIf { it.isNotEmpty() },
                onSelect = { name -> actions.onSetOption(option, name) },
                enabled = editable,
            )
        option.kind == OptionKind.Switch ->
            EditorToggleRow(
                editorToggle(Icons.Filled.Tune, stringResource(option.labelRes()), draft.switched(option), enabled) { value ->
                    actions.onSetOption(option, value.toString())
                },
            )
        else ->
            EditorTextField(
                draft.option(option),
                stringResource(option.labelRes()),
                enabled = editable,
                secret = option.secret,
                singleLine = option.kind != OptionKind.Multiline,
                keyboardType =
                    when (option.kind) {
                        OptionKind.Number -> KeyboardType.Number
                        OptionKind.Uri -> KeyboardType.Uri
                        else -> KeyboardType.Text
                    },
                autoCorrect = option.autoCorrect,
                placeholder = option.placeholderRes()?.let { stringResource(it) },
                supporting = option.hintRes()?.let { stringResource(it) },
                fieldId = option.fieldId,
                required = option.required && draft.enabled,
            ) { value -> actions.onSetOption(option, value) }
    }
}

@Composable
private fun TypeChips(
    draft: AgentForm,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
    EditorSection(AgentSections.TYPES, stringResource(R.string.user_settings_types_title)) {
        TypeChipGroups(draft, enabled, onToggle)
    }
}

@Composable
private fun TypeChipGroups(
    draft: AgentForm,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
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
                    onClick = { onToggle(type.bit) },
                    enabled = enabled,
                )
            }
        }
    }
}

private val NotificationType.isIssue: Boolean get() = name.startsWith("Issue")
