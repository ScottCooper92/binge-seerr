package com.cooper.binge.companion.seerr.ui.settings.server

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContactMail
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Token
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.ui.graphics.vector.ImageVector
import com.cooper.binge.companion.seerr.R

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
        ServerAgent.Telegram -> Icons.AutoMirrored.Filled.Send
        ServerAgent.Pushover, ServerAgent.Pushbullet, ServerAgent.Gotify, ServerAgent.Ntfy, ServerAgent.LunaSea, ServerAgent.WebPush ->
            Icons.Filled.Notifications
        ServerAgent.Webhook -> Icons.Filled.Http
    }

@StringRes
internal fun AgentOption.labelRes(): Int =
    when (this) {
        AgentOption.EmailFrom -> R.string.server_settings_agent_email_from
        AgentOption.EmailSenderName -> R.string.server_settings_agent_sender_name
        AgentOption.EmailSmtpHost -> R.string.server_settings_agent_smtp_host
        AgentOption.EmailSmtpPort -> R.string.server_settings_agent_smtp_port
        AgentOption.EmailSecure, AgentOption.EmailIgnoreTls, AgentOption.EmailRequireTls ->
            R.string.server_settings_agent_encryption
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

/** The option's own icon in its row's tinted box: what the value is, a host, a port, a key, a webhook. */
internal fun AgentOption.icon(): ImageVector =
    when (this) {
        AgentOption.EmailFrom -> Icons.Filled.Email
        AgentOption.EmailSenderName -> Icons.Filled.Badge
        AgentOption.EmailSmtpHost -> Icons.Filled.Dns
        AgentOption.EmailSmtpPort -> Icons.Filled.Tag
        AgentOption.EmailSecure, AgentOption.EmailIgnoreTls, AgentOption.EmailRequireTls -> Icons.Filled.Lock
        AgentOption.EmailAllowSelfSigned -> Icons.Filled.GppMaybe
        AgentOption.EmailAuthUser, AgentOption.NtfyUsername -> Icons.Filled.Person
        AgentOption.EmailAuthPass, AgentOption.EmailPgpPassword, AgentOption.NtfyPassword -> Icons.Filled.Password
        AgentOption.EmailPgpPrivateKey -> Icons.Filled.EnhancedEncryption
        AgentOption.EmailUserEmailRequired -> Icons.Filled.ContactMail
        AgentOption.DiscordWebhookUrl, AgentOption.SlackWebhookUrl, AgentOption.LunaSeaWebhookUrl, AgentOption.WebhookUrl ->
            Icons.Filled.Webhook
        AgentOption.DiscordBotUsername, AgentOption.TelegramBotUsername -> Icons.Filled.SmartToy
        AgentOption.DiscordBotAvatarUrl -> Icons.Filled.Face
        AgentOption.DiscordWebhookRoleId -> Icons.Filled.Groups
        AgentOption.DiscordEnableMentions -> Icons.Filled.AlternateEmail
        AgentOption.TelegramBotApi, AgentOption.PushoverAccessToken -> Icons.Filled.Token
        AgentOption.PushbulletAccessToken, AgentOption.GotifyToken, AgentOption.NtfyToken -> Icons.Filled.Token
        AgentOption.TelegramChatId -> Icons.AutoMirrored.Filled.Chat
        AgentOption.TelegramMessageThreadId -> Icons.Filled.Forum
        AgentOption.TelegramSendSilently -> Icons.Filled.NotificationsOff
        AgentOption.PushoverUserToken -> Icons.Filled.Key
        AgentOption.PushoverSound -> Icons.Filled.MusicNote
        AgentOption.PushbulletChannelTag, AgentOption.NtfyTags -> Icons.Filled.Sell
        AgentOption.GotifyUrl, AgentOption.NtfyUrl -> Icons.Filled.Link
        AgentOption.NtfyTopic -> Icons.Filled.Topic
        AgentOption.NtfyPriority -> Icons.Filled.PriorityHigh
        AgentOption.NtfyAuthByPassword -> Icons.AutoMirrored.Filled.Login
        AgentOption.NtfyAuthByToken -> Icons.Filled.VpnKey
        AgentOption.LunaSeaProfileName -> Icons.Filled.AccountCircle
        AgentOption.WebhookAuthHeader -> Icons.Filled.Key
        AgentOption.WebhookJsonPayload -> Icons.Filled.DataObject
    }

/**
 * The hint under an option whose value an administrator cannot derive from its label: what blank
 * does, where the provider issues the value, or what the number means. Null where the label says it.
 */
@StringRes
internal fun AgentOption.hintRes(): Int? =
    when (this) {
        AgentOption.EmailSmtpPort -> R.string.server_settings_agent_smtp_port_hint
        AgentOption.EmailPgpPrivateKey, AgentOption.EmailPgpPassword -> R.string.server_settings_agent_pgp_hint
        AgentOption.DiscordBotUsername -> R.string.server_settings_agent_discord_bot_username_hint
        AgentOption.TelegramBotUsername -> R.string.server_settings_agent_telegram_bot_username_hint
        AgentOption.TelegramChatId -> R.string.server_settings_agent_chat_id_hint
        AgentOption.PushoverUserToken -> R.string.server_settings_agent_user_key_hint
        AgentOption.PushoverAccessToken -> R.string.server_settings_agent_application_token_hint
        AgentOption.PushbulletAccessToken -> R.string.server_settings_agent_pushbullet_token_hint
        AgentOption.NtfyTopic -> R.string.server_settings_agent_topic_hint
        AgentOption.LunaSeaProfileName -> R.string.server_settings_agent_profile_name_hint
        AgentOption.WebhookJsonPayload -> R.string.server_settings_agent_json_payload_hint
        else -> null
    }

/** An example of the value, for the options where the shape of one is the thing that is unclear. */
@StringRes
internal fun AgentOption.placeholderRes(): Int? =
    when (this) {
        AgentOption.GotifyUrl -> R.string.placeholder_gotify_url
        AgentOption.NtfyUrl -> R.string.placeholder_ntfy_url
        AgentOption.NtfyTags -> R.string.placeholder_ntfy_tags
        else -> null
    }

internal fun NtfyPriorityLevel.labelRes(): Int =
    when (this) {
        NtfyPriorityLevel.Min -> R.string.server_settings_agent_priority_min
        NtfyPriorityLevel.Low -> R.string.server_settings_agent_priority_low
        NtfyPriorityLevel.Default -> R.string.server_settings_agent_priority_default
        NtfyPriorityLevel.High -> R.string.server_settings_agent_priority_high
        NtfyPriorityLevel.Max -> R.string.server_settings_agent_priority_max
    }

/** A priority by its arrows, down for the quiet levels and up for the loud ones, a dash for ntfy's default. */
internal fun NtfyPriorityLevel.icon(): ImageVector =
    when (this) {
        NtfyPriorityLevel.Min -> Icons.Filled.KeyboardDoubleArrowDown
        NtfyPriorityLevel.Low -> Icons.Filled.KeyboardArrowDown
        NtfyPriorityLevel.Default -> Icons.Filled.Remove
        NtfyPriorityLevel.High -> Icons.Filled.KeyboardArrowUp
        NtfyPriorityLevel.Max -> Icons.Filled.KeyboardDoubleArrowUp
    }

/**
 * The icon for one of the priority sheet's values: blank, which leaves it to the server, a level by its arrows, and null
 * for a saved number that is none of them, which takes the setting's own.
 */
internal fun ntfyPriorityIcon(value: String): ImageVector? =
    if (value.isBlank()) Icons.Filled.SettingsBackupRestore else NtfyPriorityLevel.entries.firstOrNull { it.value == value }?.icon()

/** Open for no encryption, closed for every kind of TLS. */
internal fun EmailEncryption.icon(): ImageVector = if (this == EmailEncryption.None) Icons.Filled.LockOpen else Icons.Filled.Lock

internal fun EmailEncryption.labelRes(): Int =
    when (this) {
        EmailEncryption.None -> R.string.server_settings_agent_encryption_none
        EmailEncryption.StartTlsIfAvailable -> R.string.server_settings_agent_encryption_starttls_optional
        EmailEncryption.StartTlsAlways -> R.string.server_settings_agent_encryption_starttls_always
        EmailEncryption.ImplicitTls -> R.string.server_settings_agent_encryption_implicit
    }
