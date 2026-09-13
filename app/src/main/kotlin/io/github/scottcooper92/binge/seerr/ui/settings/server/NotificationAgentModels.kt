package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrNotificationAgentDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrPushoverSoundDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Base64

/** The server's notification agents; [segment] is the path the server keys each on. */
enum class ServerAgent(
    val segment: String,
) {
    Email("email"),
    Discord("discord"),
    Telegram("telegram"),
    Pushover("pushover"),
    Pushbullet("pushbullet"),
    Slack("slack"),
    Gotify("gotify"),
    Ntfy("ntfy"),
    LunaSea("lunasea"),
    Webhook("webhook"),
    WebPush("webpush"),
}

/** How an option is typed and sent: text and its masked, address and multi-line forms, a number, or a switch. */
enum class OptionKind { Text, Secret, Uri, Number, Switch, Multiline }

/**
 * Every agent's own options, keyed by the name the server stores them under, so one form renders
 * them all. A [required] one is what the agent cannot send without, and blocks the save while the
 * agent is on.
 */
enum class AgentOption(
    val agent: ServerAgent,
    val key: String,
    val kind: OptionKind = OptionKind.Text,
    val required: Boolean = false,
) {
    EmailFrom(ServerAgent.Email, "emailFrom", required = true),
    EmailSenderName(ServerAgent.Email, "senderName"),
    EmailSmtpHost(ServerAgent.Email, "smtpHost", required = true),
    EmailSmtpPort(ServerAgent.Email, "smtpPort", OptionKind.Number, required = true),
    EmailSecure(ServerAgent.Email, "secure", OptionKind.Switch),
    EmailIgnoreTls(ServerAgent.Email, "ignoreTls", OptionKind.Switch),
    EmailRequireTls(ServerAgent.Email, "requireTls", OptionKind.Switch),
    EmailAllowSelfSigned(ServerAgent.Email, "allowSelfSigned", OptionKind.Switch),
    EmailAuthUser(ServerAgent.Email, "authUser"),
    EmailAuthPass(ServerAgent.Email, "authPass", OptionKind.Secret),
    EmailPgpPrivateKey(ServerAgent.Email, "pgpPrivateKey", OptionKind.Multiline),
    EmailPgpPassword(ServerAgent.Email, "pgpPassword", OptionKind.Secret),
    EmailUserEmailRequired(ServerAgent.Email, "userEmailRequired", OptionKind.Switch),
    DiscordWebhookUrl(ServerAgent.Discord, "webhookUrl", OptionKind.Uri, required = true),
    DiscordBotUsername(ServerAgent.Discord, "botUsername"),
    DiscordBotAvatarUrl(ServerAgent.Discord, "botAvatarUrl", OptionKind.Uri),
    DiscordWebhookRoleId(ServerAgent.Discord, "webhookRoleId"),
    DiscordEnableMentions(ServerAgent.Discord, "enableMentions", OptionKind.Switch),
    TelegramBotApi(ServerAgent.Telegram, "botAPI", OptionKind.Secret, required = true),
    TelegramBotUsername(ServerAgent.Telegram, "botUsername"),
    TelegramChatId(ServerAgent.Telegram, "chatId", required = true),
    TelegramMessageThreadId(ServerAgent.Telegram, "messageThreadId"),
    TelegramSendSilently(ServerAgent.Telegram, "sendSilently", OptionKind.Switch),
    PushoverAccessToken(ServerAgent.Pushover, "accessToken", OptionKind.Secret, required = true),
    PushoverUserToken(ServerAgent.Pushover, "userToken", OptionKind.Secret, required = true),
    PushoverSound(ServerAgent.Pushover, "sound"),
    PushbulletAccessToken(ServerAgent.Pushbullet, "accessToken", OptionKind.Secret, required = true),
    PushbulletChannelTag(ServerAgent.Pushbullet, "channelTag"),
    SlackWebhookUrl(ServerAgent.Slack, "webhookUrl", OptionKind.Uri, required = true),
    GotifyUrl(ServerAgent.Gotify, "url", OptionKind.Uri, required = true),
    GotifyToken(ServerAgent.Gotify, "token", OptionKind.Secret, required = true),
    NtfyUrl(ServerAgent.Ntfy, "url", OptionKind.Uri, required = true),
    NtfyTopic(ServerAgent.Ntfy, "topic", required = true),
    NtfyTags(ServerAgent.Ntfy, "tags"),
    NtfyPriority(ServerAgent.Ntfy, "priority", OptionKind.Number),
    NtfyAuthByPassword(ServerAgent.Ntfy, "authMethodUsernamePassword", OptionKind.Switch),
    NtfyUsername(ServerAgent.Ntfy, "username"),
    NtfyPassword(ServerAgent.Ntfy, "password", OptionKind.Secret),
    NtfyAuthByToken(ServerAgent.Ntfy, "authMethodToken", OptionKind.Switch),
    NtfyToken(ServerAgent.Ntfy, "token", OptionKind.Secret),
    LunaSeaWebhookUrl(ServerAgent.LunaSea, "webhookUrl", OptionKind.Uri, required = true),
    LunaSeaProfileName(ServerAgent.LunaSea, "profileName"),
    WebhookUrl(ServerAgent.Webhook, "webhookUrl", OptionKind.Uri, required = true),
    WebhookAuthHeader(ServerAgent.Webhook, "authHeader"),
    WebhookJsonPayload(ServerAgent.Webhook, "jsonPayload", OptionKind.Multiline, required = true),
    ;

    val secret: Boolean get() = kind == OptionKind.Secret

    /** Whether [value] is something this option can be saved with: non-blank, and parseable if [kind] is [OptionKind.Number]. */
    fun satisfiedBy(value: String): Boolean =
        when (kind) {
            OptionKind.Number -> value.trim().toIntOrNull() != null
            else -> value.isNotBlank()
        }

    companion object {
        fun of(agent: ServerAgent): List<AgentOption> = entries.filter { it.agent == agent }
    }
}

/**
 * One agent's form: on or off, the events it is sent, and its options as typed. [raw] is the
 * option object the server sent, so a key this form does not show goes back unchanged.
 */
data class AgentForm(
    val agent: ServerAgent,
    val enabled: Boolean = false,
    val types: Int = 0,
    val options: Map<AgentOption, String> = emptyMap(),
    val raw: JsonObject = JsonObject(emptyMap()),
) {
    /** An agent that is off may be saved half-typed; one that is on needs what it cannot send without. */
    val valid: Boolean
        get() = !enabled || AgentOption.of(agent).filter { it.required }.all { it.satisfiedBy(options[it].orEmpty()) }

    fun option(option: AgentOption): String = options[option].orEmpty()

    fun switched(option: AgentOption): Boolean = options[option].toBoolean()
}

data class PushoverSound(
    val name: String,
    val description: String,
)

/** Beside the form: the sounds the typed Pushover application offers, and whether a test is in flight. */
data class AgentExtras(
    val sounds: List<PushoverSound> = emptyList(),
    val testing: Boolean = false,
)

/** One agent as the agents page lists it; [enabled] is null where its settings could not be read. */
data class AgentSummary(
    val agent: ServerAgent,
    val enabled: Boolean?,
)

sealed interface AgentsUiState {
    data object Loading : AgentsUiState

    data class Error(
        val error: SeerrError,
    ) : AgentsUiState

    data class Ready(
        val agents: List<AgentSummary>,
    ) : AgentsUiState
}

/** The agents this server has: the common set, ntfy on Jellyseerr 2.6+, LunaSea on Overseerr, Gotify where it arrived. */
fun SeerrServerProfile.offeredAgents(): List<ServerAgent> =
    ServerAgent.entries.filter { agent ->
        when (agent) {
            ServerAgent.Ntfy -> hasNtfy
            ServerAgent.LunaSea -> hasLunaSea
            ServerAgent.Gotify -> hasGotify
            else -> true
        }
    }

internal fun SeerrNotificationAgentDto.toForm(agent: ServerAgent): AgentForm =
    AgentForm(
        agent = agent,
        enabled = enabled,
        types = types,
        options =
            AgentOption.of(agent).associateWith { option ->
                val value = (options[option.key] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (option == AgentOption.WebhookJsonPayload) value.decodePayload() else value
            },
        raw = options,
    )

/** The known options overlay what the server sent, each typed as the server stores it; a blank one is sent as empty. */
internal fun AgentForm.toDto(): SeerrNotificationAgentDto =
    SeerrNotificationAgentDto(
        enabled = enabled,
        types = types,
        options =
            JsonObject(
                raw +
                    AgentOption.of(agent).associate { option ->
                        val value = option(option)
                        option.key to
                            when (option.kind) {
                                OptionKind.Switch -> JsonPrimitive(value.toBoolean())
                                OptionKind.Number -> value.trim().toIntOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive("")
                                else -> JsonPrimitive(if (option == AgentOption.WebhookJsonPayload) value.encodePayload() else value.trim())
                            }
                    },
            ),
    )

internal fun SeerrPushoverSoundDto.toSound(): PushoverSound = PushoverSound(name = name, description = description ?: name)

/**
 * The webhook template is stored as the web client stores it: the text JSON-encoded as one string,
 * then base64. A value that is not in that shape — a server storing the text plainly — is shown as is.
 */
internal fun String.decodePayload(): String =
    runCatching {
        val decoded = String(Base64.getDecoder().decode(trim()), Charsets.UTF_8)
        Json.decodeFromString<String>(decoded)
    }.getOrDefault(this)

internal fun String.encodePayload(): String = Base64.getEncoder().encodeToString(Json.encodeToString(this).toByteArray(Charsets.UTF_8))
