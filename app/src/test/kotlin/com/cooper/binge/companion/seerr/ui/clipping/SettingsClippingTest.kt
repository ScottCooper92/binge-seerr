package com.cooper.binge.companion.seerr.ui.clipping

import com.cooper.binge.companion.seerr.notifications.NotificationSignal
import com.cooper.binge.companion.seerr.seerr.SeerrDefaultAccess
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.settings.AppSettings
import com.cooper.binge.companion.seerr.ui.settings.ConnectionSummary
import com.cooper.binge.companion.seerr.ui.settings.GeneralSettings
import com.cooper.binge.companion.seerr.ui.settings.NotificationAgents
import com.cooper.binge.companion.seerr.ui.settings.NotificationSettings
import com.cooper.binge.companion.seerr.ui.settings.RequestLimit
import com.cooper.binge.companion.seerr.ui.settings.RequestPolicy
import com.cooper.binge.companion.seerr.ui.settings.ScheduledJob
import com.cooper.binge.companion.seerr.ui.settings.ServerConfig
import com.cooper.binge.companion.seerr.ui.settings.ServerService
import com.cooper.binge.companion.seerr.ui.settings.ServerSummary
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
import com.cooper.binge.companion.seerr.ui.settings.SettingsActions
import com.cooper.binge.companion.seerr.ui.settings.SettingsScreen
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SignInKind
import com.cooper.binge.companion.seerr.ui.settings.SystemInfo
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_URL = "https://a-rather-long-hostname.living-room.home.example.org:5055/requests/with/a/path"

/**
 * The Settings root at its worst: a long address, name, server title, service names, root folders and job names, with
 * every group present, at font scales 1.3 and 2.0, through the design system's `assertNoTextClipped`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h12000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun check(fontScale: Float) = rule.assertNoClippingAt(fontScale) { SettingsScreen(state = worstCase(), actions = noActions()) }

    @Test fun `the settings page at 1_3`() = check(FONT_SCALE_13)

    @Test fun `the settings page at 2_0`() = check(FONT_SCALE_20)

    private fun worstCase() =
        SettingsUiState.Ready(
            connection = ConnectionSummary(baseUrl = LONG_URL, signInKind = SignInKind.Session, userName = LONG_NAME),
            server =
                ServerSummary(
                    title = "Alexandria Fitzgerald-Montgomery's Front Room Jellyseerr",
                    variant = SeerrVariant.Seerr,
                    versionLabel = "3.1.0-develop.2026.10.12",
                    updateAvailable = true,
                    commitsBehind = 1_204,
                ),
            config =
                ServerConfig(
                    general =
                        GeneralSettings(
                            applicationTitle = "Alexandria Fitzgerald-Montgomery's Front Room Jellyseerr",
                            applicationUrl = LONG_URL,
                            displayLanguage = "en",
                            hideAvailable = true,
                            discoverSliders = true,
                            network = true,
                            metadata = true,
                        ),
                    requestPolicy =
                        RequestPolicy(
                            SeerrDefaultAccess.RequestWithApproval,
                            RequestLimit(5_000, 365),
                            RequestLimit(2_000, 365),
                        ),
                    agents = NotificationAgents(emailEnabled = true, discordEnabled = true),
                    system =
                        SystemInfo(
                            jobs =
                                listOf(
                                    ScheduledJob("plex-recently-added-scan", "Plex Recently Added Scan of Every Library", true, null),
                                    ScheduledJob("image-cache-cleanup", "Image Cache Cleanup and Orphaned Thumbnail Removal", false, null),
                                ),
                        ),
                    services =
                        listOf(
                            ServerService(
                                id = 1,
                                name = "Radarr 4K Remux Library on the Upstairs Server",
                                type = ServiceType.Radarr,
                                url = LONG_URL,
                                qualityProfile = "Ultra-HD Remux with Lossless Audio",
                                rootFolder = "/mnt/storage/media/movies/4k-remux-collection",
                                is4k = true,
                                isDefault = true,
                            ),
                            ServerService(
                                id = 2,
                                name = "Sonarr Anime and Documentaries",
                                type = ServiceType.Sonarr,
                                url = LONG_URL,
                                qualityProfile = "HD-1080p",
                                rootFolder = "/mnt/storage/media/tv/anime-and-documentaries",
                                is4k = false,
                                isDefault = false,
                            ),
                        ),
                ),
            notifications =
                NotificationSettings(
                    offered = NotificationSignal.entries,
                    enabled = NotificationSignal.entries.toSet(),
                    blocked = true,
                    lastRunMillis = null,
                    nextRunMillis = null,
                ),
            app = AppSettings(bugReportUrl = LONG_URL, shakeToReport = true, shareUsageData = true, sendCrashReports = true),
        )

    private fun noActions() =
        SettingsActions(
            onBack = {},
            onEditConnection = {},
            onOpenPage = {},
            onToggleSignal = { _, _ -> },
            onNotificationAccessChanged = {},
            onToggleShakeToReport = {},
            onToggleShareUsageData = {},
            onToggleSendCrashReports = {},
            onDisconnect = {},
        )
}
