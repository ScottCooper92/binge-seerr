package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `user/{id}/settings/main`, read and written in the same shape. [username] is the display name.
 * The `global*` quota fields are the server's defaults, read only; the per-user ones override them.
 */
@Serializable
data class SeerrUserMainSettingsDto(
    @SerialName("username") val username: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("discordId") val discordId: String? = null,
    @SerialName("locale") val locale: String? = null,
    @SerialName("region") val region: String? = null,
    @SerialName("originalLanguage") val originalLanguage: String? = null,
    @SerialName("movieQuotaLimit") val movieQuotaLimit: Int? = null,
    @SerialName("movieQuotaDays") val movieQuotaDays: Int? = null,
    @SerialName("tvQuotaLimit") val tvQuotaLimit: Int? = null,
    @SerialName("tvQuotaDays") val tvQuotaDays: Int? = null,
    @SerialName("globalMovieQuotaLimit") val globalMovieQuotaLimit: Int? = null,
    @SerialName("globalMovieQuotaDays") val globalMovieQuotaDays: Int? = null,
    @SerialName("globalTvQuotaLimit") val globalTvQuotaLimit: Int? = null,
    @SerialName("globalTvQuotaDays") val globalTvQuotaDays: Int? = null,
    @SerialName("watchlistSyncMovies") val watchlistSyncMovies: Boolean? = null,
    @SerialName("watchlistSyncTv") val watchlistSyncTv: Boolean? = null,
)

/** `GET user/{id}/settings/password`: whether the account has one to change. */
@Serializable
data class SeerrUserPasswordInfoDto(
    @SerialName("hasPassword") val hasPassword: Boolean = false,
)

/** `POST user/{id}/settings/password`; the current one is required only for the user's own. */
@Serializable
data class SeerrUserPasswordBody(
    @SerialName("currentPassword") val currentPassword: String? = null,
    @SerialName("newPassword") val newPassword: String,
    @SerialName("confirmPassword") val confirmPassword: String,
)

/** `user/{id}/settings/notifications`, read and written in the same shape: each agent's fields and its type bitmask. */
@Serializable
data class SeerrUserNotificationSettingsDto(
    @SerialName("emailEnabled") val emailEnabled: Boolean? = null,
    @SerialName("pgpKey") val pgpKey: String? = null,
    @SerialName("discordEnabled") val discordEnabled: Boolean? = null,
    @SerialName("discordId") val discordId: String? = null,
    @SerialName("pushbulletAccessToken") val pushbulletAccessToken: String? = null,
    @SerialName("pushoverApplicationToken") val pushoverApplicationToken: String? = null,
    @SerialName("pushoverUserKey") val pushoverUserKey: String? = null,
    @SerialName("pushoverSound") val pushoverSound: String? = null,
    @SerialName("telegramEnabled") val telegramEnabled: Boolean? = null,
    @SerialName("telegramBotUsername") val telegramBotUsername: String? = null,
    @SerialName("telegramChatId") val telegramChatId: String? = null,
    @SerialName("telegramSendSilently") val telegramSendSilently: Boolean? = null,
    @SerialName("webPushEnabled") val webPushEnabled: Boolean? = null,
    @SerialName("notificationTypes") val notificationTypes: SeerrNotificationTypesDto? = null,
)

/** The notification-type bitmask per agent; an absent agent means none. */
@Serializable
data class SeerrNotificationTypesDto(
    @SerialName("email") val email: Int? = null,
    @SerialName("discord") val discord: Int? = null,
    @SerialName("pushbullet") val pushbullet: Int? = null,
    @SerialName("pushover") val pushover: Int? = null,
    @SerialName("telegram") val telegram: Int? = null,
    @SerialName("webpush") val webpush: Int? = null,
)

@Serializable
data class SeerrUserPermissionsDto(
    @SerialName("permissions") val permissions: Int = 0,
)

@Serializable
data class SeerrUserPermissionsBody(
    @SerialName("permissions") val permissions: Int,
)

@Serializable
data class SeerrLinkPlexBody(
    @SerialName("authToken") val authToken: String,
)

@Serializable
data class SeerrLinkJellyfinBody(
    @SerialName("username") val username: String,
    @SerialName("password") val password: String,
)

@Serializable
data class SeerrLinkQuickConnectBody(
    @SerialName("secret") val secret: String,
)
