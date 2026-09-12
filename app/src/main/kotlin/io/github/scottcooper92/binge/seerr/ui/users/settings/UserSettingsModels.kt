package io.github.scottcooper92.binge.seerr.ui.users.settings

import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.LinkFlow
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin

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

/**
 * The general page. Quota fields are text so a cleared field reads as "use the server default";
 * the `default*` values say what that default is. [canEditQuotas] is a manager's.
 */
data class GeneralSettings(
    val displayName: String = "",
    val email: String = "",
    val discordId: String = "",
    val locale: String = "",
    val region: String = "",
    val originalLanguage: String = "",
    val movieQuotaLimit: String = "",
    val movieQuotaDays: String = "",
    val tvQuotaLimit: String = "",
    val tvQuotaDays: String = "",
    val watchlistSyncMovies: Boolean? = null,
    val watchlistSyncTv: Boolean? = null,
    val defaultMovieQuota: QuotaDefault? = null,
    val defaultTvQuota: QuotaDefault? = null,
    val canEditQuotas: Boolean = false,
    val canEditEmail: Boolean = false,
) {
    /** A quota field is a whole number or blank; anything else is not a change the server would take. */
    val quotasValid: Boolean
        get() = listOf(movieQuotaLimit, movieQuotaDays, tvQuotaLimit, tvQuotaDays).all { it.isBlank() || (it.toIntOrNull() ?: -1) >= 0 }
}

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
    /** Whether the user is told about moderation events at all: those toggles are hidden otherwise. */
    val isModerator: Boolean = false,
) {
    fun agent(agent: NotificationAgent): AgentSettings = agents[agent] ?: AgentSettings()

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
