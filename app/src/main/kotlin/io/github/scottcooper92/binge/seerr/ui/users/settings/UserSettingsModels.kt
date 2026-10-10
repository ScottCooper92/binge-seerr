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

/** The pages under a user, in the order the web client's menu lists them. */
enum class UserSettingsPage { General, Password, LinkedAccounts, Notifications, Permissions }

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
    /** Null where the server keeps one region: without `SeerrServerProfile.hasStreamingRegion` (Overseerr, Jellyseerr before 2.2). */
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
     * The server never clears an address: it keeps the one it has for a blank (#1020). So a blank is valid only where there
     * is nothing to clear and none is required, or where the viewer could not change it. The value the server sent is kept
     * as it was; a changed one has to look like an address.
     */
    val emailValid: Boolean
        get() =
            if (email.isBlank()) {
                !canEditEmail || (!emailRequired && loadedEmail.isBlank())
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
 * The agents a user may be notified through, in the web client's order; slack and webhooks are the server's, not the
 * user's. An agent with no toggle is on whenever its key is set, which is how the server reads it too. [onPage] is false
 * for Web push: its subscription is the browser's, so there is nothing here to set, and it is carried only so a save
 * keeps its event types.
 */
enum class NotificationAgent(
    val hasToggle: Boolean,
    val onPage: Boolean = true,
) {
    Email(hasToggle = true),
    Discord(hasToggle = true),
    Pushbullet(hasToggle = false),
    Pushover(hasToggle = false),
    Telegram(hasToggle = true),
    WebPush(hasToggle = true, onPage = false),
}

/** One agent's settings: on or off, its own fields, and which events it is sent. */
data class AgentSettings(
    val enabled: Boolean = false,
    val fields: Map<AgentField, String> = emptyMap(),
    val sendSilently: Boolean = false,
    val types: Int = 0,
)

/**
 * The text fields the agents need, keyed so one form renders them all, in the order the web client lists them. A
 * [secret] one is masked. A [key] is one whose being set is what turns an agent without a toggle on. The Discord user
 * IDs are a list, so they live on [NotificationSettings] rather than here.
 */
enum class AgentField(
    val agent: NotificationAgent,
    val secret: Boolean = false,
    val key: Boolean = false,
) {
    PgpKey(NotificationAgent.Email),
    PushbulletToken(NotificationAgent.Pushbullet, secret = true, key = true),
    PushoverAppToken(NotificationAgent.Pushover, key = true),
    PushoverUserKey(NotificationAgent.Pushover, key = true),
    PushoverSound(NotificationAgent.Pushover),
    TelegramChatId(NotificationAgent.Telegram),
    TelegramThreadId(NotificationAgent.Telegram),
}

/** Seerr's `Notification` enum: the events a user may be told about, each its bit. */
enum class NotificationType(
    val bit: Int,
    /** Only a moderator is told about these; the toggle is hidden for a user without `MANAGE_REQUESTS`. */
    val moderatorOnly: Boolean = false,
    /** An issue's event rather than a request's, which is the group the web client lists it in. */
    val issue: Boolean = false,
) {
    MediaPending(1 shl 1, moderatorOnly = true),
    MediaApproved(1 shl 2),
    MediaAvailable(1 shl 3),
    MediaFailed(1 shl 4, moderatorOnly = true),
    MediaDeclined(1 shl 6),
    MediaAutoApproved(1 shl 7, moderatorOnly = true),
    IssueCreated(1 shl 8, moderatorOnly = true, issue = true),
    IssueComment(1 shl 9, issue = true),
    IssueResolved(1 shl 10, issue = true),
    IssueReopened(1 shl 11, issue = true),
    MediaAutoRequested(1 shl 12),
}

/**
 * The notifications page, with the web client's rules: a value that is there has to have its shape, and an agent that
 * is on and sent any event needs what it sends to. A blank value is otherwise allowed, as the server allows it.
 */
data class NotificationSettings(
    val agents: Map<NotificationAgent, AgentSettings> = emptyMap(),
    val telegramBotUsername: String? = null,
    /** Discord user IDs, in order. Overseerr and Seerr up to 3.2 keep one, and Seerr 3.3 a list. */
    val discordIds: List<String> = emptyList(),
    /** Whether the server keeps a list of Discord user IDs, which is whether a second one can be added. */
    val multipleDiscordIds: Boolean = false,
    /** Whether the server keeps a Telegram topic per user, which is whether its row is offered. */
    val telegramTopics: Boolean = false,
    /** The sounds the saved Pushover application offers; empty where the server would not list them. */
    val pushoverSounds: List<PushoverSoundChoice> = emptyList(),
    /** Whether the user is told about moderation events at all: those toggles are hidden otherwise. */
    val isModerator: Boolean = false,
) {
    fun agent(agent: NotificationAgent): AgentSettings = agents[agent] ?: AgentSettings()

    fun field(field: AgentField): String = agent(field.agent).fields[field].orEmpty()

    /** An agent without a toggle is on once it has a key; the server reads it the same way. */
    fun isOn(agent: NotificationAgent): Boolean =
        if (agent.hasToggle) {
            agent(agent).enabled
        } else {
            AgentField.entries.any { it.agent == agent && it.key && field(it).isNotBlank() }
        }

    /** Whether [agent] needs its own values filled in: it is on, and sent at least one event, as the web client asks. */
    fun needsValues(agent: NotificationAgent): Boolean = isOn(agent) && agent(agent).types != 0

    /** Each ID has to be blank or a Discord ID's shape, and one has to be there while Discord is sent anything. */
    val discordIdsValid: Boolean
        get() =
            discordIds.all { it.isBlank() || it.isDiscordIdShape() } &&
                (!needsValues(NotificationAgent.Discord) || discordIds.any { it.isNotBlank() })

    /** Whether [field]'s value, as typed, is one the server would take; a blank one is the required rule's business. */
    fun fieldValid(field: AgentField): Boolean = field.accepts(field(field).trim())

    /** Whether [field] has to be filled in for this draft to save. */
    fun required(field: AgentField): Boolean = field in REQUIRED && needsValues(field.agent)

    val valid: Boolean
        get() = discordIdsValid && AgentField.entries.all { fieldValid(it) && !(required(it) && field(it).isBlank()) }

    fun update(
        agent: NotificationAgent,
        transform: (AgentSettings) -> AgentSettings,
    ): NotificationSettings = copy(agents = agents + (agent to transform(agent(agent))))

    fun set(
        field: AgentField,
        value: String,
    ): NotificationSettings = update(field.agent) { it.copy(fields = it.fields + (field to value)) }

    private companion object {
        /** The fields the web client requires while their agent is sent anything. */
        val REQUIRED = setOf(AgentField.PushbulletToken, AgentField.PushoverAppToken, AgentField.PushoverUserKey, AgentField.TelegramChatId)
    }
}

/** One sound a Pushover application offers: the name the server stores, and what the user is shown. */
data class PushoverSoundChoice(
    val name: String,
    val description: String,
)

/**
 * The web client's shape for each value: Pushover's 30-character keys, Telegram's IDs and an armoured PGP key. A blank
 * value is accepted, since clearing one is how it is removed; whether it may be blank is the required rule's business.
 */
internal fun AgentField.accepts(value: String): Boolean =
    value.isEmpty() ||
        when (this) {
            AgentField.PgpKey -> PGP_KEY.containsMatchIn(value)
            AgentField.PushoverAppToken, AgentField.PushoverUserKey -> PUSHOVER_KEY.matches(value)
            AgentField.TelegramChatId -> TELEGRAM_CHAT_ID.matches(value)
            AgentField.TelegramThreadId -> value.all { it in '0'..'9' }
            AgentField.PushbulletToken, AgentField.PushoverSound -> true
        }

private val PGP_KEY = Regex("-----BEGIN PGP PUBLIC KEY BLOCK-----.+-----END PGP PUBLIC KEY BLOCK-----", RegexOption.DOT_MATCHES_ALL)
private val PUSHOVER_KEY = Regex("[a-zA-Z0-9]{30}")
private val TELEGRAM_CHAT_ID = Regex("-?[0-9]+")

/**
 * The permissions page: the toggles offered, what is selected, and the bits the editor leaves
 * alone. [locked] are the toggles this viewer may not flip: Admin, for anyone but the owner. That is
 * the server's one rule on a permissions write, and the web client's (#1016).
 */
data class PermissionSettings(
    val selected: Set<ManageablePermission> = emptySet(),
    val original: Int = 0,
    val offered: List<ManageablePermission> = ManageablePermission.entries,
    val locked: Set<ManageablePermission> = emptySet(),
    /**
     * The user is an admin and the viewer is not the owner. The server refuses any change to an admin's mask from anyone
     * but the owner, so every toggle is locked and the page says why (#1134).
     */
    val ownerOnly: Boolean = false,
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
