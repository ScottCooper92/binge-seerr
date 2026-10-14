package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.component.TvConfirmDialogContent
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrTvSpanishScreenPreviews
import com.cooper.binge.companion.seerr.ui.FIXED_NOW_MILLIS
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.hub.ConnectionHealth
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.previewReady
import com.cooper.binge.companion.seerr.ui.settings.ScheduledJob
import com.cooper.binge.companion.seerr.ui.settings.ServerConfig
import com.cooper.binge.companion.seerr.ui.settings.ServerService
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SignInKind
import com.cooper.binge.companion.seerr.ui.settings.SystemInfo
import com.cooper.binge.companion.seerr.ui.settings.server.AgentForm
import com.cooper.binge.companion.seerr.ui.settings.server.AgentOption
import com.cooper.binge.companion.seerr.ui.settings.server.AgentSummary
import com.cooper.binge.companion.seerr.ui.settings.server.AgentsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.JobInterval
import com.cooper.binge.companion.seerr.ui.settings.server.JobsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.PLEX_FULL_SCAN_JOB_ID
import com.cooper.binge.companion.seerr.ui.settings.server.ServerAgent
import com.cooper.binge.companion.seerr.ui.settings.server.ServerGeneralSettings
import com.cooper.binge.companion.seerr.ui.settings.server.ServerJob
import com.cooper.binge.companion.seerr.ui.tv.SampleSettings
import com.cooper.binge.companion.seerr.ui.tv.SampleSettingsAdmin
import com.cooper.binge.companion.seerr.ui.tv.SampleSettingsWithApp
import com.cooper.binge.companion.seerr.ui.users.settings.EditorUiState
import com.binge.designsystem.R as DesR

private const val SYNC_JOB_ID = "download-sync"

private fun jobsReady(
    scanRunning: Boolean = false,
    syncRunning: Boolean = false,
    busy: Set<String> = emptySet(),
    failed: Set<String> = emptySet(),
) = JobsUiState.Ready(
    jobs =
        listOf(
            ServerJob(PLEX_FULL_SCAN_JOB_ID, "Plex Full Library Scan", JobInterval.Long, scanRunning, null),
            ServerJob(SYNC_JOB_ID, "Download Sync", JobInterval.Short, syncRunning, FIXED_NOW_MILLIS + 3 * 60 * 60_000L),
        ),
    busyIds = busy,
    failedIds = failed,
    now = FIXED_NOW_MILLIS,
)

/** Every group an admin sees, with services to read out and jobs to run. */
private val SampleSettingsFull =
    SampleSettingsWithApp.copy(
        config =
            ServerConfig(
                system = SystemInfo(jobs = emptyList()),
                services =
                    listOf(
                        ServerService(1, "Radarr", ServiceType.Radarr, null, "HD-1080p", "/movies", is4k = false, isDefault = true),
                        ServerService(2, "Radarr 4K", ServiceType.Radarr, null, "UHD", "/movies-4k", is4k = true, isDefault = true),
                        ServerService(3, "Sonarr", ServiceType.Sonarr, null, "HD-1080p", "/tv", is4k = false, isDefault = true),
                    ),
            ),
    )

/**
 * The television settings sheet: its first page, the server at a glance over the groups with Disconnect last, then a page
 * for each kind of group: the media server with its library scan and services, the maintenance jobs, this app with Binge
 * and the code the bug report shows, the household's switches and the notification agents, and the confirm Disconnect asks.
 */
class TvSettingsScreenshotTest {
    /** The first page for a viewer who is not an admin: the summary, this app, and Disconnect after it, in the error colour. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Root() {
        Sheet(SampleSettingsWithApp, hub = previewReady())
    }

    /** An admin's first page: the summary, then every group in order, and Disconnect last. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun RootAdmin() {
        Sheet(SampleSettingsFull, hub = previewReady(), jobs = jobsReady(), actions = householdActions(SaveStatus.Idle).withAgents())
    }

    /** The admin's first page in Spanish, where every group name is longer. */
    @PreviewTest
    @SeerrTvSpanishScreenPreviews
    @Composable
    fun RootAdminSpanish() {
        Sheet(SampleSettingsFull, hub = previewReady(), jobs = jobsReady(), actions = householdActions(SaveStatus.Idle).withAgents())
    }

    /** A server behind by commits, signed in with its API key: both said in the summary, the first on its title only. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun RootBehind() {
        Sheet(
            SampleSettings.copy(
                server = SampleSettings.server.copy(updateAvailable = true, commitsBehind = 12),
                connection = SampleSettings.connection.copy(signInKind = SignInKind.ApiKey, userName = null),
            ),
            hub = previewReady(health = ConnectionHealth.Unreachable),
        )
    }

    /** Disconnect asks first, in a dialog over the sheet: what it does, then Disconnect in the error colour, and Cancel. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun DisconnectConfirm() {
        TvConfirmDialogContent(
            title = stringResource(R.string.hub_disconnect_confirm_title),
            message = stringResource(R.string.hub_disconnect_confirm_message),
            confirmLabel = stringResource(R.string.tv_settings_disconnect_confirm),
            dismissLabel = stringResource(DesR.string.action_cancel),
            destructive = true,
            onConfirm = {},
            onDismissRequest = {},
            initiallyDismissFocused = true,
        )
    }

    /** An admin's media server page: the library scan first, then the services as one card of names. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun MediaServer() {
        Sheet(SampleSettingsFull, page = mediaServerPage(), jobs = jobsReady())
    }

    /** A scan that is running: the row offers Cancel, and says so in place of the server's name. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ScanRunning() {
        Sheet(SampleSettingsAdmin, page = mediaServerPage(), jobs = jobsReady(scanRunning = true))
    }

    /** A call on its way: said on the row until it lands, not as a note that clears (#1513). */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ScanBusy() {
        Sheet(SampleSettingsAdmin, page = mediaServerPage(), jobs = jobsReady(busy = setOf(PLEX_FULL_SCAN_JOB_ID)))
    }

    /** A call the server refused or that could not be sent: kept on the row until the next attempt. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ScanFailed() {
        Sheet(SampleSettingsAdmin, page = mediaServerPage(), jobs = jobsReady(failed = setOf(PLEX_FULL_SCAN_JOB_ID)))
    }

    /** The maintenance jobs, each a row OK runs: one running, one with its next run. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Maintenance() {
        Sheet(SampleSettingsFull, page = maintenancePage(), jobs = jobsReady(syncRunning = true))
    }

    /** A job the server refused: its row says to try again, and OK does. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun MaintenanceFailed() {
        Sheet(SampleSettingsFull, page = maintenancePage(), jobs = jobsReady(failed = setOf(SYNC_JOB_ID)))
    }

    /** Before the jobs list is in: the settings read of them, as one card with nothing to press. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun MaintenanceReadOut() {
        Sheet(
            SampleSettingsFull.copy(
                config =
                    ServerConfig(
                        system =
                            SystemInfo(
                                jobs =
                                    listOf(
                                        ScheduledJob(
                                            SYNC_JOB_ID,
                                            "Download Sync",
                                            running = false,
                                            FIXED_NOW_MILLIS + 3 * 60 * 60_000L,
                                        ),
                                    ),
                            ),
                    ),
            ),
            page = maintenancePage(),
        )
    }

    /** This app's own group: Binge first, then the bug report and the two switches. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ThisApp() {
        Sheet(SampleSettingsWithApp, page = appPage(), hub = previewReady(bingeStatus = BingeStatus.Connected))
    }

    /** Binge missing: its row opens the Play Store listing. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ThisAppBingeMissing() {
        Sheet(SampleSettingsWithApp, page = appPage(), hub = previewReady(bingeStatus = BingeStatus.NotInstalled))
    }

    /** The code the phone scans, centred on a page of the sheet. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ReportBugCode() {
        Sheet(SampleSettingsWithApp, page = PAGE_BUG_REPORT)
    }

    /** Before the settings are read (#1053): the sheet's title over its loading line. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Loading() {
        Sheet(SettingsUiState.Loading)
    }

    /** The household's switches, each ending in a switch, with a line under them for how the last one saved. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Household() {
        Sheet(SampleSettingsAdmin, page = householdPage(), actions = householdActions(SaveStatus.Saved))
    }

    /** A switch that did not save: it stays as pressed, and the line offers Retry. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun HouseholdSaveFailed() {
        Sheet(SampleSettingsAdmin, page = householdPage(), actions = householdActions(SaveStatus.Failed))
    }

    /** The notification agents the server offers, each with whether it is on. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Agents() {
        Sheet(SampleSettingsAdmin, page = groupPage(stringResource(R.string.settings_group_notifications)), actions = agentActions())
    }

    /** One agent's page: its switch, and how the press saved. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun AgentPage() {
        Sheet(SampleSettingsAdmin, page = agentPage(ServerAgent.Discord), actions = agentActions(configured = true))
    }

    /** An agent that cannot send until a setting is filled in: its switch is out of reach and the page says where to finish. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun AgentPageNeedsSetup() {
        Sheet(SampleSettingsAdmin, page = agentPage(ServerAgent.Discord), actions = agentActions(configured = false))
    }

    @Composable
    private fun householdPage() = groupPage(stringResource(R.string.tv_settings_group_household))

    private fun householdActions(status: SaveStatus) =
        TvSettingsActions(
            household =
                TvHousehold(
                    settings =
                        ServerGeneralSettings(
                            hideAvailable = true,
                            hideRequested = false,
                            hideBlocklisted = false,
                            specialEpisodes = true,
                        ),
                    status = status,
                    onChange = {},
                    onRetry = {},
                ),
        )

    private fun agentActions(configured: Boolean = true) =
        TvSettingsActions(
            agents =
                TvAgents(
                    state =
                        AgentsUiState.Ready(
                            listOf(
                                AgentSummary(ServerAgent.Discord, enabled = false),
                                AgentSummary(ServerAgent.Email, enabled = true),
                                AgentSummary(ServerAgent.Slack, enabled = null),
                            ),
                        ),
                    page = { agent, nav ->
                        val form =
                            AgentForm(
                                agent,
                                enabled = false,
                                options =
                                    if (configured) {
                                        mapOf(
                                            AgentOption.DiscordWebhookUrl to "https://discord.test/hook",
                                        )
                                    } else {
                                        emptyMap()
                                    },
                            )
                        TvAgentPage(nav, agent, EditorUiState.Ready(draft = form, saved = form, saveStatus = SaveStatus.Saved), {}, {})
                    },
                ),
        )

    private fun TvSettingsActions.withAgents() = TvSettingsActions(household = household, agents = agentActions().agents)

    @Composable
    private fun mediaServerPage() = groupPage(stringResource(R.string.tv_settings_group_media))

    @Composable
    private fun maintenancePage() = groupPage(stringResource(R.string.tv_settings_group_maintenance))

    @Composable
    private fun appPage() = groupPage(stringResource(R.string.settings_group_app))

    @Composable
    private fun Sheet(
        state: SettingsUiState,
        page: String = PAGE_SETTINGS,
        hub: HubUiState? = null,
        jobs: JobsUiState = JobsUiState.Loading,
        actions: TvSettingsActions = TvSettingsActions(),
    ) {
        TvSettingsSheet(
            // Fixed, so a job's "next run" reads the same whichever day the suite runs.
            state = (state as? SettingsUiState.Ready)?.copy(now = FIXED_NOW_MILLIS) ?: state,
            actions = actions,
            onDismissRequest = {},
            hub = hub,
            initialPage = page,
            jobs = jobs,
        )
    }
}
