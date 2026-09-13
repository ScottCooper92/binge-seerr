package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.scottcooper92.binge.seerr.R

@StringRes
internal fun UserSettingsPage.titleRes(): Int =
    when (this) {
        UserSettingsPage.General -> R.string.user_settings_page_general
        UserSettingsPage.Password -> R.string.user_settings_page_password
        UserSettingsPage.Notifications -> R.string.user_settings_page_notifications
        UserSettingsPage.Permissions -> R.string.user_permissions_title
        UserSettingsPage.LinkedAccounts -> R.string.user_settings_page_linked
    }

@StringRes
internal fun UserSettingsPage.descriptionRes(): Int =
    when (this) {
        UserSettingsPage.General -> R.string.user_settings_page_general_desc
        UserSettingsPage.Password -> R.string.user_settings_page_password_desc
        UserSettingsPage.Notifications -> R.string.user_settings_page_notifications_desc
        UserSettingsPage.Permissions -> R.string.user_settings_page_permissions_desc
        UserSettingsPage.LinkedAccounts -> R.string.user_settings_page_linked_desc
    }

internal fun UserSettingsPage.icon(): ImageVector =
    when (this) {
        UserSettingsPage.General -> Icons.Filled.Person
        UserSettingsPage.Password -> Icons.Filled.Lock
        UserSettingsPage.Notifications -> Icons.Filled.Notifications
        UserSettingsPage.Permissions -> Icons.Filled.Security
        UserSettingsPage.LinkedAccounts -> Icons.Filled.Link
    }

@StringRes
internal fun NotificationAgent.labelRes(): Int =
    when (this) {
        NotificationAgent.Email -> R.string.settings_agent_email
        NotificationAgent.Discord -> R.string.settings_agent_discord
        NotificationAgent.Telegram -> R.string.user_settings_agent_telegram
        NotificationAgent.Pushbullet -> R.string.user_settings_agent_pushbullet
        NotificationAgent.Pushover -> R.string.user_settings_agent_pushover
        NotificationAgent.WebPush -> R.string.user_settings_agent_webpush
    }

@StringRes
internal fun AgentField.labelRes(): Int =
    when (this) {
        AgentField.PgpKey -> R.string.user_settings_field_pgp
        AgentField.DiscordId -> R.string.user_settings_discord_id
        AgentField.TelegramChatId -> R.string.user_settings_field_telegram_chat
        AgentField.PushbulletToken -> R.string.user_settings_field_pushbullet_token
        AgentField.PushoverUserKey -> R.string.user_settings_field_pushover_user
        AgentField.PushoverAppToken -> R.string.user_settings_field_pushover_token
        AgentField.PushoverSound -> R.string.user_settings_field_pushover_sound
    }

@StringRes
internal fun NotificationType.labelRes(): Int =
    when (this) {
        NotificationType.MediaPending -> R.string.user_settings_type_media_pending
        NotificationType.MediaApproved -> R.string.user_settings_type_media_approved
        NotificationType.MediaAvailable -> R.string.user_settings_type_media_available
        NotificationType.MediaFailed -> R.string.user_settings_type_media_failed
        NotificationType.MediaDeclined -> R.string.user_settings_type_media_declined
        NotificationType.MediaAutoApproved -> R.string.user_settings_type_media_auto_approved
        NotificationType.IssueCreated -> R.string.user_settings_type_issue_created
        NotificationType.IssueComment -> R.string.user_settings_type_issue_comment
        NotificationType.IssueResolved -> R.string.user_settings_type_issue_resolved
        NotificationType.IssueReopened -> R.string.user_settings_type_issue_reopened
        NotificationType.MediaAutoRequested -> R.string.user_settings_type_media_auto_requested
    }
