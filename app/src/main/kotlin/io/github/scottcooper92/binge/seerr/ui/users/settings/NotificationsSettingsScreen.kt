package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.BingeFilterChip
import io.github.scottcooper92.binge.seerr.R
import kotlinx.coroutines.flow.Flow
import com.binge.designsystem.R as DesR

/**
 * The notifications page: one section per agent with its switch where it has one, its own
 * fields, and the events it is sent while it is on.
 */
@Composable
fun NotificationsSettingsScreen(
    state: EditorUiState<NotificationSettings>,
    events: Flow<EditorEvent>,
    actions: EditorActions<NotificationSettings>,
) {
    EditorPage(
        title = stringResource(R.string.user_settings_page_notifications),
        state = state,
        events = events,
        actions = actions,
    ) { draft, enabled ->
        NotificationAgent.entries.forEach { agent -> AgentSection(agent, draft, enabled, actions.onEdit) }
    }
}

@Composable
private fun AgentSection(
    agent: NotificationAgent,
    settings: NotificationSettings,
    enabled: Boolean,
    onEdit: ((NotificationSettings) -> NotificationSettings) -> Unit,
) {
    val current = settings.agent(agent)
    EditorSectionTitle(stringResource(agent.labelRes()))
    if (agent.hasToggle) {
        EditorSwitchRow(stringResource(R.string.user_settings_agent_enabled), current.enabled, enabled = enabled) { value ->
            onEdit { it.update(agent) { agentSettings -> agentSettings.copy(enabled = value) } }
        }
    }
    if (agent == NotificationAgent.Telegram) {
        settings.telegramBotUsername?.let { bot ->
            Text(
                stringResource(R.string.user_settings_telegram_bot, bot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    AgentField.entries.filter { it.agent == agent }.forEach { field ->
        EditorTextField(
            current.fields[field].orEmpty(),
            stringResource(field.labelRes()),
            enabled = enabled,
            secret = field.secret,
        ) { value ->
            onEdit { it.update(agent) { agentSettings -> agentSettings.copy(fields = agentSettings.fields + (field to value)) } }
        }
    }
    if (agent == NotificationAgent.Telegram) {
        EditorSwitchRow(stringResource(R.string.user_settings_telegram_silent), current.sendSilently, enabled = enabled) { value ->
            onEdit { it.update(agent) { agentSettings -> agentSettings.copy(sendSilently = value) } }
        }
    }
    if (settings.isOn(agent)) {
        Text(stringResource(R.string.user_settings_types_title), style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(DesR.dimen.padding_s)),
        ) {
            NotificationType.entries.filter { settings.isModerator || !it.moderatorOnly }.forEach { type ->
                BingeFilterChip(
                    label = stringResource(type.labelRes()),
                    selected = current.types and type.bit != 0,
                    onClick = {
                        if (enabled) {
                            onEdit {
                                it.update(agent) { agentSettings ->
                                    agentSettings.copy(
                                        types =
                                            agentSettings.types xor type.bit,
                                    )
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
