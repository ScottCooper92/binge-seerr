package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.runtime.Composable
import com.cooper.binge.companion.seerr.seerr.ManageablePermission
import com.cooper.binge.companion.seerr.seerr.PermissionScope
import com.cooper.binge.companion.seerr.ui.users.UserOrigin
import com.cooper.binge.companion.seerr.ui.users.settings.AgentField
import com.cooper.binge.companion.seerr.ui.users.settings.AgentSettings
import com.cooper.binge.companion.seerr.ui.users.settings.EditorActions
import com.cooper.binge.companion.seerr.ui.users.settings.EditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.ExtrasEditorUiState
import com.cooper.binge.companion.seerr.ui.users.settings.GeneralSettings
import com.cooper.binge.companion.seerr.ui.users.settings.GeneralSettingsScreen
import com.cooper.binge.companion.seerr.ui.users.settings.LinkedAccount
import com.cooper.binge.companion.seerr.ui.users.settings.LinkedAccountsActions
import com.cooper.binge.companion.seerr.ui.users.settings.LinkedAccountsScreen
import com.cooper.binge.companion.seerr.ui.users.settings.LinkedAccountsUiState
import com.cooper.binge.companion.seerr.ui.users.settings.NotificationAgent
import com.cooper.binge.companion.seerr.ui.users.settings.NotificationSettings
import com.cooper.binge.companion.seerr.ui.users.settings.NotificationType
import com.cooper.binge.companion.seerr.ui.users.settings.NotificationsSettingsScreen
import com.cooper.binge.companion.seerr.ui.users.settings.PasswordSettings
import com.cooper.binge.companion.seerr.ui.users.settings.PasswordSettingsScreen
import com.cooper.binge.companion.seerr.ui.users.settings.PermissionSettings
import com.cooper.binge.companion.seerr.ui.users.settings.PermissionsSettingsScreen
import com.cooper.binge.companion.seerr.ui.users.settings.QuotaDefault
import com.cooper.binge.companion.seerr.ui.users.settings.ServerDiscoverDefaults
import com.cooper.binge.companion.seerr.ui.users.settings.UserGeneralExtras
import com.cooper.binge.companion.seerr.ui.users.settings.UserRole
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsActions
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsIndex
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsPage
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsScreen
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsUiState
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_EMAIL = "alexandria.fitzgerald-montgomery@a-rather-long-domain-name.example.com"

/**
 * A user's own settings pages with long names, addresses and tokens and every optional group present, at font scales
 * 1.3 and 2.0, through the design system's `assertNoTextClipped`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UserSettingsClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test fun `the settings index at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { Index() }

    @Test fun `the settings index at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Index() }

    @Test fun `the general page at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { General() }

    @Test fun `the general page at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { General() }

    @Test fun `the password page at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { Password() }

    @Test fun `the password page at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Password() }

    @Test fun `the permissions page at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { Permissions() }

    @Test fun `the permissions page at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Permissions() }

    @Test fun `the notifications page at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { Notifications() }

    @Test fun `the notifications page at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Notifications() }

    @Test fun `the linked accounts page at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { Linked() }

    @Test fun `the linked accounts page at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Linked() }

    private fun <T> actions() = EditorActions<T>(onBack = {}, onRetry = {}, onEdit = {}, onSave = {})

    @Composable
    private fun Index() =
        UserSettingsScreen(
            state = UserSettingsUiState.Ready(UserSettingsIndex(LONG_NAME, UserSettingsPage.entries)),
            actions = UserSettingsActions(onBack = {}, onRetry = {}, onOpenPage = {}),
        )

    @Composable
    private fun General() {
        val form =
            GeneralSettings(
                accountType = UserOrigin.Plex,
                role = UserRole.Admin,
                displayName = LONG_NAME,
                fallbackName = "alexandria.fitzgerald-montgomery",
                email = LONG_EMAIL,
                discordId = "123456789012345678",
                locale = "en",
                region = "GB",
                streamingRegion = "IE",
                originalLanguage = "ja|ko",
                movieQuotaOverride = true,
                movieQuotaLimit = 5_000,
                movieQuotaDays = 365,
                watchlistSyncMovies = true,
                watchlistSyncTv = true,
                defaultMovieQuota = QuotaDefault(limit = 10_000, days = 365),
                defaultTvQuota = QuotaDefault(limit = 8_000, days = 365),
                canEditQuotas = true,
                canEditEmail = true,
            )
        GeneralSettingsScreen(
            state =
                ExtrasEditorUiState.Ready(
                    draft = form,
                    saved = form,
                    extras =
                        UserGeneralExtras(
                            serverDefaults = ServerDiscoverDefaults(locale = "fr", region = "FR", originalLanguage = "fr|en"),
                        ),
                ),
            events = emptyFlow(),
            actions = actions(),
        )
    }

    @Composable
    private fun Password() {
        val form =
            PasswordSettings(
                hasPassword = true,
                currentRequired = true,
                current = "old-password",
                new = "correct-horse",
                confirm = "correct-hors",
            )
        PasswordSettingsScreen(
            state = EditorUiState.Ready(draft = form, saved = PasswordSettings(hasPassword = true, currentRequired = true)),
            events = emptyFlow(),
            actions = actions(),
        )
    }

    @Composable
    private fun Permissions() {
        val form =
            PermissionSettings(
                selected = setOf(ManageablePermission.Request, ManageablePermission.ViewRequests, ManageablePermission.CreateIssues),
                original = 0,
                offered = ManageablePermission.offered(PermissionScope()),
                locked = setOf(ManageablePermission.Admin, ManageablePermission.ManageSettings, ManageablePermission.ManageUsers),
            )
        PermissionsSettingsScreen(
            state = EditorUiState.Ready(draft = form, saved = form),
            events = emptyFlow(),
            actions = actions(),
            onToggle = {},
        )
    }

    @Composable
    private fun Notifications() {
        val form =
            NotificationSettings(
                agents =
                    mapOf(
                        NotificationAgent.Email to
                            AgentSettings(enabled = true, types = NotificationType.entries.fold(0) { m, t -> m or t.bit }),
                        NotificationAgent.Discord to AgentSettings(enabled = true, types = NotificationType.MediaAvailable.bit),
                        NotificationAgent.Telegram to
                            AgentSettings(enabled = true, fields = mapOf(AgentField.TelegramChatId to "-1001234567890123")),
                        NotificationAgent.Pushover to
                            AgentSettings(
                                enabled = true,
                                fields =
                                    mapOf(
                                        AgentField.PushoverAppToken to "azGDORePK8gMaC0QOYAMyEEuzJnyUi",
                                        AgentField.PushoverUserKey to "uQiRzpo4DXghDmr9QzzfQu27cmVRsG",
                                    ),
                            ),
                    ),
                discordIds = listOf("123456789012345678", "scott#1234"),
                multipleDiscordIds = true,
                telegramBotUsername = "a_rather_long_binge_requests_notification_bot",
                isModerator = true,
            )
        NotificationsSettingsScreen(state = EditorUiState.Ready(draft = form, saved = form), events = emptyFlow(), actions = actions())
    }

    @Composable
    private fun Linked() =
        LinkedAccountsScreen(
            state =
                LinkedAccountsUiState.Ready(
                    plex = LinkedAccount(UserOrigin.Plex, linked = true, linkedAs = "alexandria.fitzgerald-montgomery.plex"),
                    mediaServer = LinkedAccount(UserOrigin.Jellyfin, linked = true, linkedAs = "alexandria.fitzgerald-montgomery.jellyfin"),
                    canQuickConnect = true,
                ),
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
}
