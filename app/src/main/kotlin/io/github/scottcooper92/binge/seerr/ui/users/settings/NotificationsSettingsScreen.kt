package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.binge.designsystem.component.ItemGroup
import com.binge.designsystem.component.ListItem
import io.github.scottcooper92.binge.seerr.R
import kotlinx.coroutines.flow.Flow

/**
 * The notifications page in the web client's order: a group per agent with the values it sends with, and, while it is
 * on, the events it is sent as switch rows, the request ones and then the issue ones. Web push is not here: its
 * subscription is the browser's. A value is edited in a sheet that checks its shape, and one the agent can't send
 * without says so in its row while the agent is sent anything. Save stays off until nothing does.
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
        canSave = { it.valid },
        saveAsMade = true,
    ) { draft, enabled ->
        NotificationAgent.entries.filter { it.onPage }.forEach { agent ->
            AgentGroup(agent, draft, enabled, actions.onEdit)
            if (draft.isOn(agent)) TypeGroups(agent, draft, enabled, actions.onEdit)
        }
    }
}

@Composable
private fun AgentGroup(
    agent: NotificationAgent,
    draft: NotificationSettings,
    enabled: Boolean,
    onEdit: ((NotificationSettings) -> NotificationSettings) -> Unit,
) {
    val rows =
        when (agent) {
            NotificationAgent.Discord -> discordRows(draft, enabled, onEdit)
            NotificationAgent.Pushover ->
                listOf(
                    fieldItem(AgentField.PushoverAppToken, draft, enabled, onEdit),
                    fieldItem(AgentField.PushoverUserKey, draft, enabled, onEdit),
                    soundItem(draft, enabled, onEdit),
                )
            NotificationAgent.Telegram ->
                listOfNotNull(
                    fieldItem(AgentField.TelegramChatId, draft, enabled, onEdit),
                    // Overseerr never stored a topic, so a row it would drop is not offered there.
                    if (draft.telegramTopics) fieldItem(AgentField.TelegramThreadId, draft, enabled, onEdit) else null,
                ) +
                    // Only how a message is delivered, so it follows the agent. The IDs above do not: they are filled in
                    // before the server turns the agent on.
                    editorToggle(
                        Icons.Filled.NotificationsOff,
                        stringResource(R.string.user_settings_telegram_silent),
                        draft.agent(agent).sendSilently,
                        enabled && draft.agent(agent).enabled,
                    ) { on -> onEdit { it.update(agent) { telegram -> telegram.copy(sendSilently = on) } } }
            else -> AgentField.entries.filter { it.agent == agent }.map { fieldItem(it, draft, enabled, onEdit) }
        }
    val notes = agentNotes(agent, draft)
    ItemGroup(
        title = stringResource(agent.labelRes()),
        rows = rows,
        belowRows = notes.takeIf { it.isNotEmpty() }?.let { { notes.forEach { GroupMessage(it, error = false) } } },
    )
}

/**
 * What the group says beneath its rows. An agent the server has off is one these values wait on: neither Jellyseerr nor
 * Overseerr reads that switch from a user's save, so it is told, not offered (#410).
 */
@Composable
private fun agentNotes(
    agent: NotificationAgent,
    draft: NotificationSettings,
): List<String> =
    listOfNotNull(
        stringResource(R.string.user_settings_agent_off, stringResource(agent.labelRes())).takeIf {
            agent.hasToggle && !draft.agent(agent).enabled
        },
        draft.telegramBotUsername
            ?.takeIf { agent == NotificationAgent.Telegram }
            ?.let { stringResource(R.string.user_settings_telegram_bot, it) },
        stringResource(R.string.user_settings_discord_ids_hint).takeIf { agent == NotificationAgent.Discord && draft.multipleDiscordIds },
    )

/**
 * Each Discord user ID as its own row, and, where the server keeps a list, a row that adds one. Clearing an ID removes
 * it. One row is always there, so a server that keeps a single ID still offers it.
 */
@Composable
private fun discordRows(
    draft: NotificationSettings,
    enabled: Boolean,
    onEdit: ((NotificationSettings) -> NotificationSettings) -> Unit,
): List<ListItem> {
    val label = stringResource(R.string.user_settings_discord_id)
    val wrongShape = stringResource(R.string.user_settings_discord_id_error)
    val ids = draft.discordIds.ifEmpty { listOf("") }
    val idRows =
        ids.mapIndexed { index, id ->
            key(index) {
                textSettingItem(
                    icon = Icons.Filled.Person,
                    label = label,
                    value = id,
                    enabled = enabled,
                    onChange = { typed -> onEdit { it.copy(discordIds = it.discordIds.replaced(index, typed)) } },
                    hint = stringResource(R.string.user_settings_discord_id_hint),
                    required = index == 0 && draft.needsValues(NotificationAgent.Discord),
                    check = { typed -> wrongShape.takeIf { !typed.isDiscordIdShape() } },
                    keyboard = NumberKeyboard,
                )
            }
        }
    if (!draft.multipleDiscordIds || draft.discordIds.none { it.isNotBlank() }) return idRows
    return idRows +
        key(ids.size) {
            textSettingItem(
                icon = Icons.Filled.Add,
                label = stringResource(R.string.user_settings_discord_add),
                value = "",
                enabled = enabled,
                onChange = { typed -> if (typed.isNotBlank()) onEdit { it.copy(discordIds = it.discordIds + typed) } },
                hint = stringResource(R.string.user_settings_discord_id_hint),
                check = { typed -> wrongShape.takeIf { !typed.isDiscordIdShape() } },
                keyboard = NumberKeyboard,
            ).copy(detail = null)
        }
}

/** [ids] with the one at [index] set to [typed], or dropped where [typed] is blank; an index past the end appends. */
private fun List<String>.replaced(
    index: Int,
    typed: String,
): List<String> =
    when {
        typed.isBlank() -> filterIndexed { at, _ -> at != index }
        index < size -> toMutableList().also { it[index] = typed }
        else -> this + typed
    }

@Composable
private fun fieldItem(
    field: AgentField,
    draft: NotificationSettings,
    enabled: Boolean,
    onEdit: ((NotificationSettings) -> NotificationSettings) -> Unit,
): ListItem {
    val value = draft.field(field)
    val problem = field.errorRes()?.let { stringResource(it) }
    return textSettingItem(
        icon = field.icon(),
        label = stringResource(field.labelRes()),
        value = value,
        enabled = enabled,
        onChange = { typed -> onEdit { it.set(field, typed) } },
        hint = field.hintRes()?.let { stringResource(it) },
        required = draft.required(field),
        check = { typed -> problem.takeIf { !field.accepts(typed) } },
        // Whether a hidden value is there is all its row needs to say.
        shown =
            if (field.hidden && value.isNotBlank()) {
                stringResource(R.string.server_settings_secret_set)
            } else {
                value.ifBlank {
                    stringResource(R.string.settings_value_not_set)
                }
            },
        secret = field.secret,
        multiline = field == AgentField.PgpKey,
        keyboard = field.keyboard(),
    )
}

/** The sound as a pick from the saved application's sounds, the device's own first; a sound it no longer lists stays. */
@Composable
private fun soundItem(
    draft: NotificationSettings,
    enabled: Boolean,
    onEdit: ((NotificationSettings) -> NotificationSettings) -> Unit,
): ListItem {
    val current = draft.field(AgentField.PushoverSound)
    val listed = draft.pushoverSounds.map { it.name to it.description }
    val kept = listOf(current to current).filter { (name, _) -> name.isNotBlank() && listed.none { it.first == name } }
    return choiceSettingItem(
        icon = Icons.Filled.MusicNote,
        title = stringResource(AgentField.PushoverSound.labelRes()),
        choices = listOf("" to stringResource(R.string.user_settings_pushover_sound_default)) + kept + listed,
        selected = current,
        enabled = enabled,
    ) { name -> onEdit { it.set(AgentField.PushoverSound, name) } }
}

/** The events [agent] is sent, as the web client's checklist: the request ones, then the issue ones. */
@Composable
private fun TypeGroups(
    agent: NotificationAgent,
    draft: NotificationSettings,
    enabled: Boolean,
    onEdit: ((NotificationSettings) -> NotificationSettings) -> Unit,
) {
    val types = draft.agent(agent).types
    val offered = NotificationType.entries.filter { draft.isModerator || !it.moderatorOnly }
    listOf(false, true).forEach { issues ->
        ItemGroup(
            title = null,
            rows =
                offered.filter { it.issue == issues }.map { type ->
                    editorToggle(Icons.Filled.Notifications, stringResource(type.labelRes()), types and type.bit != 0, enabled) {
                        onEdit { it.update(agent) { settings -> settings.copy(types = settings.types xor type.bit) } }
                    }
                },
        )
    }
}

/** A value the row does not spell out: a secret, or a PGP key's many lines. */
private val AgentField.hidden: Boolean get() = secret || this == AgentField.PgpKey

private fun AgentField.icon(): ImageVector =
    when (this) {
        AgentField.PushoverUserKey -> Icons.Filled.Person
        AgentField.TelegramChatId -> Icons.Filled.Forum
        AgentField.TelegramThreadId -> Icons.Filled.Tag
        AgentField.PushoverSound -> Icons.Filled.MusicNote
        AgentField.PgpKey, AgentField.PushbulletToken, AgentField.PushoverAppToken -> Icons.Filled.Key
    }

/** IDs take digits; a key the server matches exactly is typed with no autocorrect. */
private fun AgentField.keyboard(): KeyboardOptions =
    when (this) {
        AgentField.TelegramChatId, AgentField.TelegramThreadId -> NumberKeyboard
        else -> VerbatimKeyboard
    }
