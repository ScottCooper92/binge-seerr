package io.github.scottcooper92.binge.seerr.ui.users.settings

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrNotificationTypesDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserNotificationSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The notifications page: each agent the user may be reached through, its own fields, and the
 * events it is sent as a bitmask. The moderation events are offered only to a user the server
 * would send them to, which is one who manages requests.
 */
@HiltViewModel(assistedFactory = NotificationsViewModel.Factory::class)
class NotificationsViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @Assisted private val userId: Int,
    ) : EditorViewModel<NotificationSettings>() {
        init {
            reload()
        }

        override suspend fun load(): NotificationSettings =
            coroutineScope {
                val api = connection.api()
                val target = async { api.user(userId) }
                val settings = api.userNotificationSettings(userId)
                settings.toNotificationSettings(isModerator = target.await().toPermissions().canManageRequests)
            }

        override suspend fun write(draft: NotificationSettings): NotificationSettings {
            connection.api().updateUserNotificationSettings(userId, draft.toDto())
            return load()
        }

        @AssistedFactory
        interface Factory {
            fun create(userId: Int): NotificationsViewModel
        }
    }

internal fun SeerrUserNotificationSettingsDto.toNotificationSettings(isModerator: Boolean): NotificationSettings {
    val types = notificationTypes ?: SeerrNotificationTypesDto()
    return NotificationSettings(
        agents =
            mapOf(
                NotificationAgent.Email to
                    AgentSettings(enabled = emailEnabled == true, fields = fields(AgentField.PgpKey to pgpKey), types = types.email ?: 0),
                NotificationAgent.Discord to
                    AgentSettings(
                        enabled = discordEnabled == true,
                        fields = fields(AgentField.DiscordId to discordId),
                        types = types.discord ?: 0,
                    ),
                NotificationAgent.Telegram to
                    AgentSettings(
                        enabled = telegramEnabled == true,
                        fields = fields(AgentField.TelegramChatId to telegramChatId),
                        sendSilently = telegramSendSilently == true,
                        types = types.telegram ?: 0,
                    ),
                NotificationAgent.Pushbullet to
                    AgentSettings(
                        enabled = !pushbulletAccessToken.isNullOrBlank(),
                        fields = fields(AgentField.PushbulletToken to pushbulletAccessToken),
                        types = types.pushbullet ?: 0,
                    ),
                NotificationAgent.Pushover to
                    AgentSettings(
                        enabled = !pushoverUserKey.isNullOrBlank(),
                        fields =
                            fields(
                                AgentField.PushoverUserKey to pushoverUserKey,
                                AgentField.PushoverAppToken to pushoverApplicationToken,
                                AgentField.PushoverSound to pushoverSound,
                            ),
                        types = types.pushover ?: 0,
                    ),
                NotificationAgent.WebPush to AgentSettings(enabled = webPushEnabled == true, types = types.webpush ?: 0),
            ),
        telegramBotUsername = telegramBotUsername?.takeIf { it.isNotBlank() },
        isModerator = isModerator,
    )
}

internal fun NotificationSettings.toDto(): SeerrUserNotificationSettingsDto {
    fun field(field: AgentField): String? = agent(field.agent).fields[field]?.trim()?.takeIf { it.isNotEmpty() }
    return SeerrUserNotificationSettingsDto(
        emailEnabled = agent(NotificationAgent.Email).enabled,
        pgpKey = field(AgentField.PgpKey),
        discordEnabled = agent(NotificationAgent.Discord).enabled,
        discordId = field(AgentField.DiscordId),
        pushbulletAccessToken = field(AgentField.PushbulletToken),
        pushoverApplicationToken = field(AgentField.PushoverAppToken),
        pushoverUserKey = field(AgentField.PushoverUserKey),
        pushoverSound = field(AgentField.PushoverSound),
        telegramEnabled = agent(NotificationAgent.Telegram).enabled,
        telegramChatId = field(AgentField.TelegramChatId),
        telegramSendSilently = agent(NotificationAgent.Telegram).sendSilently,
        webPushEnabled = agent(NotificationAgent.WebPush).enabled,
        notificationTypes =
            SeerrNotificationTypesDto(
                email = agent(NotificationAgent.Email).types,
                discord = agent(NotificationAgent.Discord).types,
                pushbullet = agent(NotificationAgent.Pushbullet).types,
                pushover = agent(NotificationAgent.Pushover).types,
                telegram = agent(NotificationAgent.Telegram).types,
                webpush = agent(NotificationAgent.WebPush).types,
            ),
    )
}

private fun fields(vararg entries: Pair<AgentField, String?>): Map<AgentField, String> =
    entries.associate { (field, value) -> field to value.orEmpty() }
