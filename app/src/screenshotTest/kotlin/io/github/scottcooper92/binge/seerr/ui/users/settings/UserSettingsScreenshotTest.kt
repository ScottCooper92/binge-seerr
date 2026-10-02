package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.users.UserOrigin
import kotlinx.coroutines.flow.emptyFlow

/**
 * The pages under a user's settings. Each page takes its layout across the device matrix once, then
 * the states its draft can be in on the phone cell alone. Every page is an [EditorPage] but the
 * index and the linked accounts, so the loading and failed arms are framed on the pages with a
 * layout of their own rather than once for the shared frame.
 */
class UserSettingsIndexScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun allPagesLayout() = IndexFrame(UserSettingsUiState.Ready(UserSettingsIndex("Scott", UserSettingsPage.entries)))

    /** A viewer who may open only some pages, as on someone else's account. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun somePages() =
        IndexFrame(
            UserSettingsUiState.Ready(UserSettingsIndex("Alex", listOf(UserSettingsPage.General, UserSettingsPage.Notifications))),
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun nothingToOpen() = IndexFrame(UserSettingsUiState.Ready(UserSettingsIndex("Alex", emptyList())))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = IndexFrame(UserSettingsUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = IndexFrame(UserSettingsUiState.Error(SeerrError.Unreachable))
}

class UserGeneralSettingsScreenshotTest {
    /** A manager editing a user: email editable, both watchlist syncs, and the quotas with the server's defaults beneath. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun managerLayout() = GeneralFrame(settled(managerGeneral()))

    /** A user editing themselves: no quotas, the email locked, and nothing offered for the watchlist. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun ownSettings() =
        GeneralFrame(
            settled(managerGeneral().copy(canEditQuotas = false, canEditEmail = false, watchlistSyncMovies = null, watchlistSyncTv = null)),
        )

    /** A quota that is not a whole number is marked and keeps Save off. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun invalidQuota() =
        GeneralFrame(EditorUiState.Ready(draft = managerGeneral().copy(movieQuotaLimit = "five"), saved = managerGeneral()))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun saving() = GeneralFrame(EditorUiState.Ready(draft = managerGeneral(), saved = managerGeneral(), saving = true))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = GeneralFrame(EditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = GeneralFrame(EditorUiState.Error(SeerrError.Server))
}

class UserPasswordScreenshotTest {
    /** An account with a password, changed by someone who must give the current one. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun changeLayout() = PasswordFrame(settled(PasswordSettings(hasPassword = true, currentRequired = true)))

    /** An account with no password yet: it says so, and asks for no current one. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun noPasswordYet() = PasswordFrame(settled(PasswordSettings(hasPassword = false)))

    /** The two new entries differ, so the confirm field says so. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun mismatch() =
        PasswordFrame(
            EditorUiState.Ready(
                draft =
                    PasswordSettings(
                        hasPassword = true,
                        currentRequired = true,
                        current = "old-password",
                        new = "correct-horse",
                        confirm = "correct-hors",
                    ),
                saved = PasswordSettings(hasPassword = true, currentRequired = true),
            ),
        )

    /** Everything entered and matching, so Save is live. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun readyToSave() =
        PasswordFrame(
            EditorUiState.Ready(
                draft =
                    PasswordSettings(
                        hasPassword = true,
                        currentRequired = true,
                        current = "old-password",
                        new = "correct-horse",
                        confirm = "correct-horse",
                    ),
                saved = PasswordSettings(hasPassword = true, currentRequired = true),
            ),
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = PasswordFrame(EditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = PasswordFrame(EditorUiState.Error(SeerrError.Unauthorized))
}

class UserPermissionsScreenshotTest {
    /** The Jellyseerr lineage's full set, one granted, and what the viewer may not grant dimmed. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun lockedLayout() = PermissionsFrame(settled(permissions()))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun saving() = PermissionsFrame(EditorUiState.Ready(draft = permissions(), saved = permissions(), saving = true))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = PermissionsFrame(EditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = PermissionsFrame(EditorUiState.Error(SeerrError.Unreachable))
}

class UserNotificationsScreenshotTest {
    /** A moderator's page: every agent, the ones with a key on, and the moderation events among the chips. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun moderatorLayout() = NotificationsFrame(settled(notifications(moderator = true)))

    /** A plain user is not offered the moderation events. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun plainUser() = NotificationsFrame(settled(notifications(moderator = false)))

    /** A pasted username in the Discord ID: the field is flagged and Save stays off. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun badDiscordId() =
        NotificationsFrame(
            EditorUiState.Ready(
                draft =
                    notifications(moderator = false).update(NotificationAgent.Discord) {
                        it.copy(
                            fields =
                                mapOf(
                                    AgentField.DiscordId to "scott#1234",
                                ),
                        )
                    },
                saved = notifications(moderator = false),
            ),
        )

    /** Nothing configured: each agent offers its fields, and no event chips until one is on. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun nothingConfigured() = NotificationsFrame(settled(NotificationSettings()))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = NotificationsFrame(EditorUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = NotificationsFrame(EditorUiState.Error(SeerrError.Server))
}

class UserLinkedAccountsScreenshotTest {
    /** Plex linked, and a Jellyfin account not yet linked. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun mixedLayout() = LinkedFrame(linkedReady())

    /** A Plex-only server: the one row. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun plexOnly() = LinkedFrame(linkedReady().copy(mediaServer = null))

    /** A link in flight disables the buttons. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun busy() = LinkedFrame(linkedReady().copy(busy = true))

    /** Both linked, the media server as a named account. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun bothLinked() =
        LinkedFrame(
            linkedReady().copy(
                plex = LinkedAccount(UserOrigin.Plex, linked = true),
                mediaServer = LinkedAccount(UserOrigin.Jellyfin, linked = true, linkedAs = "scott"),
            ),
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = LinkedFrame(LinkedAccountsUiState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun failed() = LinkedFrame(LinkedAccountsUiState.Error(SeerrError.Unreachable))
}

private fun <T> settled(form: T) = EditorUiState.Ready(draft = form, saved = form)

private fun managerGeneral() =
    GeneralSettings(
        displayName = "Scott",
        fallbackName = "scott.cooper",
        email = "scott@home.lan",
        discordId = "123456789012345678",
        locale = "en",
        region = "GB",
        originalLanguage = "ja|ko",
        movieQuotaLimit = "5",
        movieQuotaDays = "7",
        tvQuotaLimit = "",
        tvQuotaDays = "",
        watchlistSyncMovies = true,
        watchlistSyncTv = false,
        defaultMovieQuota = QuotaDefault(limit = 10, days = 7),
        defaultTvQuota = QuotaDefault(limit = 0, days = 0),
        canEditQuotas = true,
        canEditEmail = true,
    )

private fun permissions() =
    PermissionSettings(
        selected = setOf(ManageablePermission.Request, ManageablePermission.ViewRequests, ManageablePermission.CreateIssues),
        original = 0,
        offered = ManageablePermission.offered(jellyseerrLineage = true),
        locked = setOf(ManageablePermission.Admin, ManageablePermission.ManageSettings, ManageablePermission.ManageUsers),
    )

private fun notifications(moderator: Boolean) =
    NotificationSettings(
        agents =
            mapOf(
                NotificationAgent.Email to
                    AgentSettings(enabled = true, types = NotificationType.MediaApproved.bit or NotificationType.MediaAvailable.bit),
                NotificationAgent.Discord to AgentSettings(enabled = true, fields = mapOf(AgentField.DiscordId to "123456789012345678")),
                NotificationAgent.Telegram to AgentSettings(enabled = false, fields = mapOf(AgentField.TelegramChatId to "-1001234")),
                NotificationAgent.Pushover to
                    AgentSettings(fields = mapOf(AgentField.PushoverUserKey to "uQiRzpo4DXghDmr9QzzfQu27cmVRsG")),
            ),
        telegramBotUsername = "binge_requests_bot",
        isModerator = moderator,
    )

private fun linkedReady() =
    LinkedAccountsUiState.Ready(
        plex = LinkedAccount(UserOrigin.Plex, linked = true, linkedAs = "scott_plex"),
        mediaServer = LinkedAccount(UserOrigin.Jellyfin, linked = false),
        canQuickConnect = true,
    )

private fun <T> noActions() = EditorActions<T>(onBack = {}, onRetry = {}, onEdit = {}, onSave = {})

@Composable
private fun IndexFrame(state: UserSettingsUiState) =
    UserSettingsScreen(state = state, actions = UserSettingsActions(onBack = {}, onRetry = {}, onOpenPage = {}))

@Composable
private fun GeneralFrame(state: EditorUiState<GeneralSettings>) =
    GeneralSettingsScreen(state = state, events = emptyFlow(), actions = noActions())

@Composable
private fun PasswordFrame(state: EditorUiState<PasswordSettings>) =
    PasswordSettingsScreen(state = state, events = emptyFlow(), actions = noActions())

@Composable
private fun PermissionsFrame(state: EditorUiState<PermissionSettings>) =
    PermissionsSettingsScreen(state = state, events = emptyFlow(), actions = noActions(), onToggle = {})

@Composable
private fun NotificationsFrame(state: EditorUiState<NotificationSettings>) =
    NotificationsSettingsScreen(state = state, events = emptyFlow(), actions = noActions())

@Composable
private fun LinkedFrame(state: LinkedAccountsUiState) =
    LinkedAccountsScreen(
        state = state,
        events = emptyFlow(),
        actions =
            LinkedAccountsActions(
                onBack = {},
                onRetry = {},
                onLinkPlex = {},
                onUnlinkPlex = {},
                onLinkQuickConnect = {},
                onLinkMediaServer = { _, _ -> },
                onUnlinkMediaServer = {},
                onPlexLaunched = {},
                onCancelLink = {},
            ),
    )
