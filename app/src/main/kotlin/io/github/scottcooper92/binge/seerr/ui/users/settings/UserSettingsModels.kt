package io.github.scottcooper92.binge.seerr.ui.users.settings

import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.ui.LanguageCodeShapes
import io.github.scottcooper92.binge.seerr.ui.LinkFlow
import io.github.scottcooper92.binge.seerr.ui.settings.server.ListChoices
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerList
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin
import io.github.scottcooper92.binge.seerr.ui.users.isEmailShape

/** The pages under a user, in the order the index lists them. */
enum class UserSettingsPage { General, Password, Notifications, Permissions, LinkedAccounts }

/** Which pages this viewer gets for this user, per the server's own rules. */
data class UserSettingsIndex(
    val userName: String,
    val pages: List<UserSettingsPage>,
)

sealed interface UserSettingsUiState {
    data object Loading : UserSettingsUiState

    data class Ready(
        val index: UserSettingsIndex,
    ) : UserSettingsUiState

    data class Error(
        val error: SeerrError,
    ) : UserSettingsUiState
}

/** A quota the server applies by default: [limit] requests per [days]; a zero limit is unlimited. */
data class QuotaDefault(
    val limit: Int,
    val days: Int,
)

/** What the web client shows as a user's role: the server's first account, an admin, or anyone else. */
enum class UserRole { Owner, Admin, User }

/**
 * The general page. A quota is the server's default until its override is on; the `default*` values say what that
 * default is. [canEditQuotas] is a manager's. The region and language fields hold the server's encoding for a user:
 * blank is "the server's", and `all` is no filter.
 */
data class GeneralSettings(
    /** How the account signs in, and its role, which the page shows and does not edit. */
    val accountType: UserOrigin? = null,
    val role: UserRole = UserRole.User,
    val displayName: String = "",
    /** What the server falls back to when the display name is blank: the media-server username, or the email. */
    val fallbackName: String = "",
    val email: String = "",
    /** The email as the server sent it. Seerr stores a media-server username there for users with no address. */
    val loadedEmail: String = "",
    /** The web client requires an email of everyone but a Jellyfin or Emby user who is not the owner. */
    val emailRequired: Boolean = false,
    /** Edited on the notifications page, as the web client does; carried so a save here does not clear it. */
    val discordId: String = "",
    val locale: String = "",
    val region: String = "",
    /** Null where the lineage has no streaming region: Overseerr. */
    val streamingRegion: String? = null,
    val originalLanguage: String = "",
    /** While an override is off its limit and window are the server's default, which turning it on starts from. */
    val movieQuotaOverride: Boolean = false,
    val movieQuotaLimit: Int = 0,
    val movieQuotaDays: Int = DEFAULT_QUOTA_DAYS,
    val tvQuotaOverride: Boolean = false,
    val tvQuotaLimit: Int = 0,
    val tvQuotaDays: Int = DEFAULT_QUOTA_DAYS,
    val watchlistSyncMovies: Boolean? = null,
    val watchlistSyncTv: Boolean? = null,
    val defaultMovieQuota: QuotaDefault? = null,
    val defaultTvQuota: QuotaDefault? = null,
    val canEditQuotas: Boolean = false,
    val canEditEmail: Boolean = false,
) {
    /**
     * Blank clears the address where that is allowed, and the value the server sent is kept as it was; a changed one has
     * to look like an address.
     */
    val emailValid: Boolean
        get() =
            if (email.isBlank()) {
                !emailRequired || !canEditEmail
            } else {
                email.trim() == loadedEmail.trim() || email.isEmailShape()
            }

    /** Blank is "use the server's" and `all` is no filter; anything else has to have the shape of its code. */
    val localeValid: Boolean get() = locale.isBlank() || LanguageCodeShapes.isLocale(locale)
    val regionValid: Boolean get() = region.isUserRegion()
    val streamingRegionValid: Boolean get() = streamingRegion?.isUserRegion() != false
    val originalLanguageValid: Boolean
        get() = originalLanguage.trim().let { it.isEmpty() || it == "all" || it == "server" || LanguageCodeShapes.isOriginalLanguage(it) }

    val valid: Boolean
        get() = emailValid && localeValid && regionValid && streamingRegionValid && originalLanguageValid
}

private fun String.isUserRegion(): Boolean = trim().let { it.isEmpty() || it == "all" || LanguageCodeShapes.isRegion(it) }

/** The web client's window for a quota no one has set: a week. */
internal const val DEFAULT_QUOTA_DAYS = 7

/**
 * What the general page shows beside its form: the fork, for its display languages; the server's own Discover
 * settings, which a user's blank ones fall back to; and the server's lists, each read once its picker opens.
 */
data class UserGeneralExtras(
    val variant: SeerrVariant = SeerrVariant.Unknown,
    val serverDefaults: ServerDiscoverDefaults = ServerDiscoverDefaults(),
    val lists: Map<ServerList, ListChoices> = emptyMap(),
)

/** The server's display language and Discover filters, from its public settings. */
data class ServerDiscoverDefaults(
    val locale: String = "",
    val region: String = "",
    val streamingRegion: String = "",
    val originalLanguage: String = "",
)

/** The password page: whether the account has one, whether the caller must give it, and the two new entries. */
data class PasswordSettings(
    val hasPassword: Boolean = false,
    val currentRequired: Boolean = false,
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
) {
    val valid: Boolean get() = new.length >= MIN_PASSWORD_LENGTH && new == confirm && (!currentRequired || current.isNotEmpty())

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
    }
}

/**
 * The agents a user may be notified through; slack and webhooks are the server's, not the user's.
 * An agent with no toggle is on whenever its key is set, which is how the server reads it too.
 */
enum class NotificationAgent(
    val hasToggle: Boolean,
) {
    Email(hasToggle = true),
    Discord(hasToggle = true),
    Telegram(hasToggle = true),
    Pushbullet(hasToggle = false),
    Pushover(hasToggle = false),
    WebPush(hasToggle = true),
}

/** One agent's settings: on or off, its own fields, and which events it is sent. */
data class AgentSettings(
    val enabled: Boolean = false,
    val fields: Map<AgentField, String> = emptyMap(),
    val sendSilently: Boolean = false,
    val types: Int = 0,
)

/** The text fields the agents need, keyed so one form renders them all; [secret] ones are masked. */
enum class AgentField(
    val agent: NotificationAgent,
    val secret: Boolean = false,
) {
    PgpKey(NotificationAgent.Email),
    DiscordId(NotificationAgent.Discord),
    TelegramChatId(NotificationAgent.Telegram),
    PushbulletToken(NotificationAgent.Pushbullet, secret = true),
    PushoverUserKey(NotificationAgent.Pushover, secret = true),
    PushoverAppToken(NotificationAgent.Pushover, secret = true),
    PushoverSound(NotificationAgent.Pushover),
}

/** Seerr's `Notification` enum: the events a user may be told about, each its bit. */
enum class NotificationType(
    val bit: Int,
    /** Only a moderator is told about these; the toggle is hidden for a user without `MANAGE_REQUESTS`. */
    val moderatorOnly: Boolean = false,
) {
    MediaPending(1 shl 1, moderatorOnly = true),
    MediaApproved(1 shl 2),
    MediaAvailable(1 shl 3),
    MediaFailed(1 shl 4, moderatorOnly = true),
    MediaDeclined(1 shl 6),
    MediaAutoApproved(1 shl 7, moderatorOnly = true),
    IssueCreated(1 shl 8, moderatorOnly = true),
    IssueComment(1 shl 9),
    IssueResolved(1 shl 10),
    IssueReopened(1 shl 11),
    MediaAutoRequested(1 shl 12),
}

data class NotificationSettings(
    val agents: Map<NotificationAgent, AgentSettings> = emptyMap(),
    val telegramBotUsername: String? = null,
    /**
     * Seerr 3.3 turned the Discord mention id into a list. This page edits the first of them, and
     * holds the rest so a save keeps the ones it never showed.
     */
    val otherDiscordIds: List<String> = emptyList(),
    /** The Telegram topic id, which this page does not edit; carried so a save does not clear it. */
    val telegramMessageThreadId: String? = null,
    /** Whether the user is told about moderation events at all: those toggles are hidden otherwise. */
    val isModerator: Boolean = false,
) {
    fun agent(agent: NotificationAgent): AgentSettings = agents[agent] ?: AgentSettings()

    /** Blank is allowed; anything else must pass the same rule as the profile editor's Discord ID. */
    val discordIdValid: Boolean
        get() = agent(NotificationAgent.Discord).fields[AgentField.DiscordId].orEmpty().let { it.isBlank() || it.isDiscordIdShape() }

    val valid: Boolean get() = discordIdValid

    /** An agent without a toggle is on once it has a key; the server reads it the same way. */
    fun isOn(agent: NotificationAgent): Boolean =
        if (agent.hasToggle) agent(agent).enabled else agent(agent).fields.values.any { it.isNotBlank() }

    fun update(
        agent: NotificationAgent,
        transform: (AgentSettings) -> AgentSettings,
    ): NotificationSettings = copy(agents = agents + (agent to transform(agent(agent))))
}

/**
 * The permissions page: the toggles offered, what is selected, and the bits the editor leaves
 * alone. [locked] are the toggles this viewer may not flip: what they do not hold themselves, and
 * Admin for anyone but the owner, which is the web client's rule.
 */
data class PermissionSettings(
    val selected: Set<ManageablePermission> = emptySet(),
    val original: Int = 0,
    val offered: List<ManageablePermission> = ManageablePermission.entries,
    val locked: Set<ManageablePermission> = emptySet(),
)

/** One media-server account a user may link: what it is called, whether it is linked, and as whom where the server says. */
data class LinkedAccount(
    val origin: UserOrigin,
    val linked: Boolean,
    val linkedAs: String? = null,
)

sealed interface LinkedAccountsUiState {
    data object Loading : LinkedAccountsUiState

    data class Ready(
        val plex: LinkedAccount,
        /** The Jellyfin or Emby account, where the server has one of those; null on a Plex server. */
        val mediaServer: LinkedAccount?,
        val canQuickConnect: Boolean,
        val link: LinkFlow? = null,
        val busy: Boolean = false,
    ) : LinkedAccountsUiState

    data class Error(
        val error: SeerrError,
    ) : LinkedAccountsUiState
}

sealed interface LinkedAccountsEvent {
    data object Linked : LinkedAccountsEvent

    data object Unlinked : LinkedAccountsEvent

    /** The code was not approved before it expired. */
    data object LinkExpired : LinkedAccountsEvent

    data class Failed(
        val error: SeerrError,
    ) : LinkedAccountsEvent
}
