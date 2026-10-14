package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.binge.designsystem.SaveStatus
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrTvSpanishScreenPreviews
import com.cooper.binge.companion.seerr.ui.FIXED_NOW_MILLIS
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.hub.HubUiState
import com.cooper.binge.companion.seerr.ui.hub.previewAdminOverview
import com.cooper.binge.companion.seerr.ui.hub.previewReady
import com.cooper.binge.companion.seerr.ui.settings.ServerConfig
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
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

/**
 * The television settings sheet: its list of groups with Disconnect last, then a page for each kind of group: the connection and what
 * can be done to it, the confirm Disconnect asks on its own page, the server's status, the admin's media server with its
 * library scan, this app's own group and the code the bug report shows.
 */
class TvSettingsScreenshotTest {
    /** The sheet's first page: the groups, then Disconnect after them all, in the error colour. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Root() {
        Sheet(SampleSettings)
    }

    /** The connection's page: its read-outs, then Edit connection, the one thing a television can do about it. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Connection() {
        Sheet(SampleSettings, page = groupPage(stringResource(R.string.settings_group_connection)))
    }

    /** Disconnect asks first, on a page of its own: what it does, then Disconnect and Cancel. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun DisconnectConfirm() {
        Sheet(SampleSettings, page = PAGE_DISCONNECT)
    }

    /** An admin's media server page: the library scan row, over the note that the rest is read here and changed on the phone. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun MediaServer() {
        Sheet(SampleSettingsAdmin, page = mediaServerPage(), jobs = jobsReady())
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

    /** The System group, once the jobs list is in: each job a row that opens its own page. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun SystemJobs() {
        Sheet(
            SampleSettingsAdmin.copy(config = ServerConfig(system = SystemInfo(jobs = emptyList()))),
            page = groupPage(stringResource(R.string.settings_group_system)),
            jobs = jobsReady(syncRunning = true),
        )
    }

    /** One job's page: what it is, when it runs next, and Run now. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun JobPage() {
        Sheet(SampleSettingsAdmin, page = jobPage(SYNC_JOB_ID), jobs = jobsReady())
    }

    /** A running job's page: Cancel in place of Run now. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun JobPageRunning() {
        Sheet(SampleSettingsAdmin, page = jobPage(SYNC_JOB_ID), jobs = jobsReady(syncRunning = true))
    }

    /** A refused run: said on the page, with Run now there to try again. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun JobPageFailed() {
        Sheet(SampleSettingsAdmin, page = jobPage(SYNC_JOB_ID), jobs = jobsReady(failed = setOf(SYNC_JOB_ID)))
    }

    /** What the home board used to carry at its head: whether the server answers, and an update where there is one. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ServerStatus() {
        Sheet(SampleSettings, page = serverPage(), hub = previewReady(overview = previewAdminOverview()))
    }

    /** The status rows in Spanish: each count carries its own words, in the translator's order (#1042). */
    @PreviewTest
    @SeerrTvSpanishScreenPreviews
    @Composable
    fun ServerStatusSpanish() {
        Sheet(SampleSettings, page = serverPage(), hub = previewReady(overview = previewAdminOverview()))
    }

    /** Binge missing: its row is the one to press, opening its Play Store listing. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ServerStatusBingeMissing() {
        Sheet(
            SampleSettings,
            page = serverPage(),
            hub = previewReady(overview = previewAdminOverview(), bingeStatus = BingeStatus.NotInstalled),
        )
    }

    /** This app's own group: the bug report row and the two switches, each saying where it stands. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ReportBug() {
        Sheet(SampleSettingsWithApp, page = groupPage(stringResource(R.string.settings_group_app)))
    }

    /** The code the phone scans, on a page of the sheet. */
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

    /** The household's switches, each saying On or Off in place, with a line under them for how the last one saved. */
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

    /** One agent's page: its switch to press, and how the press saved. */
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

    @Composable
    private fun mediaServerPage() = groupPage(stringResource(R.string.server_settings_media_server))

    @Composable
    private fun serverPage() = groupPage(stringResource(R.string.tv_settings_group_server))

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
