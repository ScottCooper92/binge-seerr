package io.github.scottcooper92.binge.seerr.ui.users.settings

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.di.IoDispatcher
import io.github.scottcooper92.binge.seerr.seerr.SeerrNotificationTypesDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserNotificationSettingsDto
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The notifications page: each agent the user may be reached through, its own fields, and the
 * events it is sent as a bitmask. The moderation events are offered only to a user the server
 * would send them to, which is one who manages requests. The Pushover sounds are the saved
 * application's, as the web client lists them, for a viewer the server would list them to;
 * otherwise the device's own sound and the current one are the choices.
 */
@HiltViewModel(assistedFactory = NotificationsViewModel.Factory::class)
class NotificationsViewModel
    @AssistedInject
    constructor(
        private val connection: SeerrConnection,
        @IoDispatcher dispatcher: CoroutineDispatcher,
        @Assisted private val userId: Int,
    ) : EditorViewModel<NotificationSettings>(dispatcher) {
        init {
            reload()
        }

        override suspend fun load(): NotificationSettings =
            coroutineScope {
                val api = connection.api()
                val target = async { api.user(userId) }
                val settings = api.userNotificationSettings(userId)
                val sounds =
                    settings.pushoverApplicationToken
                        ?.takeIf { it.isNotBlank() }
                        ?.let { pushoverSounds(it) }
                        .orEmpty()
                settings
                    .toNotificationSettings(isModerator = target.await().toPermissions().canManageRequests)
                    .copy(telegramTopics = connection.profile().hasTelegramTopics, pushoverSounds = sounds)
            }

        /**
         * The sounds [token]'s application offers. The list is a server setting, which only an admin may read, so it is
         * asked for only where the server has it and the viewer is one; a failure leaves the device's own sound.
         */
        private suspend fun pushoverSounds(token: String): List<PushoverSoundChoice> {
            val listed = connection.profile().hasPushoverSounds && connection.authenticatedUser().toPermissions().isAdmin
            if (!listed) return emptyList()
            return runCatching { connection.api().pushoverSounds(token) }
                .getOrDefault(emptyList())
                .map { PushoverSoundChoice(name = it.name, description = it.description ?: it.name) }
        }

        override suspend fun write(draft: NotificationSettings): NotificationSettings {
            connection.api().updateUserNotificationSettings(userId, draft.toDto())
            return load()
        }

        override fun canSave(draft: NotificationSettings): Boolean = draft.valid

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
                NotificationAgent.Discord to AgentSettings(enabled = discordEnabled == true, types = types.discord ?: 0),
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
                                AgentField.PushoverAppToken to pushoverApplicationToken,
                                AgentField.PushoverUserKey to pushoverUserKey,
                                AgentField.PushoverSound to pushoverSound,
                            ),
                        types = types.pushover ?: 0,
                    ),
                NotificationAgent.Telegram to
                    AgentSettings(
                        enabled = telegramEnabled == true,
                        fields =
                            fields(
                                AgentField.TelegramChatId to telegramChatId,
                                AgentField.TelegramThreadId to telegramMessageThreadId,
                            ),
                        sendSilently = telegramSendSilently == true,
                        types = types.telegram ?: 0,
                    ),
                NotificationAgent.WebPush to AgentSettings(enabled = webPushEnabled == true, types = types.webpush ?: 0),
            ),
        telegramBotUsername = telegramBotUsername?.takeIf { it.isNotBlank() },
        discordIds = discordIds ?: listOfNotNull(discordId?.takeIf { it.isNotBlank() }),
        multipleDiscordIds = discordIds != null,
        isModerator = isModerator,
    )
}

internal fun NotificationSettings.toDto(): SeerrUserNotificationSettingsDto {
    fun sent(of: AgentField): String? = field(of).trim().takeIf { it.isNotEmpty() }
    val ids = discordIds.map { it.trim() }.filter { it.isNotEmpty() }
    return SeerrUserNotificationSettingsDto(
        emailEnabled = agent(NotificationAgent.Email).enabled,
        pgpKey = sent(AgentField.PgpKey),
        discordEnabled = agent(NotificationAgent.Discord).enabled,
        discordId = ids.firstOrNull(),
        discordIds = ids,
        pushbulletAccessToken = sent(AgentField.PushbulletToken),
        pushoverApplicationToken = sent(AgentField.PushoverAppToken),
        pushoverUserKey = sent(AgentField.PushoverUserKey),
        pushoverSound = sent(AgentField.PushoverSound),
        telegramEnabled = agent(NotificationAgent.Telegram).enabled,
        telegramChatId = sent(AgentField.TelegramChatId),
        telegramMessageThreadId = sent(AgentField.TelegramThreadId),
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
