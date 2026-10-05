package io.github.scottcooper92.binge.seerr.ui.users.settings

import io.github.scottcooper92.binge.seerr.R

internal const val NOTIFICATIONS_FORM_KEY = "user_notifications"

/** Each agent's section id; one per agent, in the order the page lays them out. */
internal val NotificationAgent.sectionId: String get() = name

/** Each agent field's id, so an issue on it is shown beside it. */
internal val AgentField.fieldId: String get() = name

/**
 * Everything standing between this draft and a save. The only rule is the Discord ID's: blank, or
 * digits only. Its hint already says so, so the hint is the message. It is empty exactly when
 * [NotificationSettings.valid] is true, which `NotificationsValidationTest` holds it to.
 */
internal fun NotificationSettings.issues(): List<EditorIssue> =
    listOfNotNull(
        invalid(NotificationAgent.Discord.sectionId, AgentField.DiscordId.fieldId, R.string.user_settings_discord_id_hint)
            .takeIf { !discordIdValid },
    )
