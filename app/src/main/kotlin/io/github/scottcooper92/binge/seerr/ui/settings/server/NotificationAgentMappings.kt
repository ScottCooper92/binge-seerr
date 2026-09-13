package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tag
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.scottcooper92.binge.seerr.R

@StringRes
internal fun ServerAgent.labelRes(): Int =
    when (this) {
        ServerAgent.Email -> R.string.settings_agent_email
        ServerAgent.Discord -> R.string.settings_agent_discord
        ServerAgent.Telegram -> R.string.user_settings_agent_telegram
        ServerAgent.Pushover -> R.string.user_settings_agent_pushover
        ServerAgent.Pushbullet -> R.string.user_settings_agent_pushbullet
        ServerAgent.Slack -> R.string.settings_agent_slack
        ServerAgent.Gotify -> R.string.settings_agent_gotify
        ServerAgent.Ntfy -> R.string.settings_agent_ntfy
        ServerAgent.LunaSea -> R.string.settings_agent_lunasea
        ServerAgent.Webhook -> R.string.settings_agent_webhook
        ServerAgent.WebPush -> R.string.user_settings_agent_webpush
    }

internal fun ServerAgent.icon(): ImageVector =
    when (this) {
        ServerAgent.Email -> Icons.Filled.Email
        ServerAgent.Discord, ServerAgent.Slack -> Icons.Filled.Forum
        ServerAgent.Telegram -> Icons.Filled.Send
        ServerAgent.Pushover, ServerAgent.Pushbullet, ServerAgent.Gotify, ServerAgent.Ntfy, ServerAgent.LunaSea, ServerAgent.WebPush ->
            Icons.Filled.Notifications
        ServerAgent.Webhook -> Icons.Filled.Http
    }

/** The icon of the two type groups on the agent page. */
internal fun typeGroupIcon(issues: Boolean): ImageVector = if (issues) Icons.Filled.Chat else Icons.Filled.Tag

@StringRes
internal fun AgentOption.labelRes(): Int =
    when (this) {
        AgentOption.EmailFrom -> R.string.server_settings_agent_email_from
        AgentOption.EmailSenderName -> R.string.server_settings_agent_sender_name
        AgentOption.EmailSmtpHost -> R.string.server_settings_agent_smtp_host
        AgentOption.EmailSmtpPort -> R.string.server_settings_agent_smtp_port
        AgentOption.EmailSecure -> R.string.server_settings_agent_secure
        AgentOption.EmailIgnoreTls -> R.string.server_settings_agent_ignore_tls
        AgentOption.EmailRequireTls -> R.string.server_settings_agent_require_tls
        AgentOption.EmailAllowSelfSigned -> R.string.server_settings_agent_allow_self_signed
        AgentOption.EmailAuthUser -> R.string.server_settings_agent_auth_user
        AgentOption.EmailAuthPass -> R.string.server_settings_agent_auth_pass
        AgentOption.EmailPgpPrivateKey -> R.string.server_settings_agent_pgp_private_key
        AgentOption.EmailPgpPassword -> R.string.server_settings_agent_pgp_password
        AgentOption.EmailUserEmailRequired -> R.string.server_settings_agent_user_email_required
        AgentOption.DiscordWebhookUrl, AgentOption.SlackWebhookUrl, AgentOption.LunaSeaWebhookUrl, AgentOption.WebhookUrl ->
            R.string.server_settings_agent_webhook_url
        AgentOption.DiscordBotUsername, AgentOption.TelegramBotUsername -> R.string.server_settings_agent_bot_username
        AgentOption.DiscordBotAvatarUrl -> R.string.server_settings_agent_bot_avatar_url
        AgentOption.DiscordWebhookRoleId -> R.string.server_settings_agent_webhook_role_id
        AgentOption.DiscordEnableMentions -> R.string.server_settings_agent_enable_mentions
        AgentOption.TelegramBotApi -> R.string.server_settings_agent_bot_token
        AgentOption.TelegramChatId -> R.string.server_settings_agent_chat_id
        AgentOption.TelegramMessageThreadId -> R.string.server_settings_agent_message_thread_id
        AgentOption.TelegramSendSilently -> R.string.user_settings_telegram_silent
        AgentOption.PushoverAccessToken -> R.string.server_settings_agent_application_token
        AgentOption.PushoverUserToken -> R.string.server_settings_agent_user_key
        AgentOption.PushoverSound -> R.string.server_settings_agent_sound
        AgentOption.PushbulletAccessToken, AgentOption.GotifyToken, AgentOption.NtfyToken -> R.string.server_settings_agent_access_token
        AgentOption.PushbulletChannelTag -> R.string.server_settings_agent_channel_tag
        AgentOption.GotifyUrl, AgentOption.NtfyUrl -> R.string.server_settings_agent_server_url
        AgentOption.NtfyTopic -> R.string.server_settings_agent_topic
        AgentOption.NtfyTags -> R.string.server_settings_agent_tags
        AgentOption.NtfyPriority -> R.string.server_settings_agent_priority
        AgentOption.NtfyAuthByPassword -> R.string.server_settings_agent_auth_by_password
        AgentOption.NtfyUsername -> R.string.server_settings_agent_username
        AgentOption.NtfyPassword -> R.string.server_settings_agent_password
        AgentOption.NtfyAuthByToken -> R.string.server_settings_agent_auth_by_token
        AgentOption.LunaSeaProfileName -> R.string.server_settings_agent_profile_name
        AgentOption.WebhookAuthHeader -> R.string.server_settings_agent_auth_header
        AgentOption.WebhookJsonPayload -> R.string.server_settings_agent_json_payload
    }
