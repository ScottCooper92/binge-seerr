package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.settings.AppSettings
import com.cooper.binge.companion.seerr.ui.settings.ConnectionSummary
import com.cooper.binge.companion.seerr.ui.settings.ServerConfig
import com.cooper.binge.companion.seerr.ui.settings.ServerService
import com.cooper.binge.companion.seerr.ui.settings.ServerSummary
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SignInKind
import com.cooper.binge.companion.seerr.ui.settings.SystemInfo
import com.cooper.binge.companion.seerr.ui.settings.server.AgentForm
import com.cooper.binge.companion.seerr.ui.settings.server.AgentOption
import com.cooper.binge.companion.seerr.ui.settings.server.AgentSummary
import com.cooper.binge.companion.seerr.ui.settings.server.AgentsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.JELLYFIN_FULL_SCAN_JOB_ID
import com.cooper.binge.companion.seerr.ui.settings.server.JobInterval
import com.cooper.binge.companion.seerr.ui.settings.server.JobsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.PLEX_FULL_SCAN_JOB_ID
import com.cooper.binge.companion.seerr.ui.settings.server.ServerAgent
import com.cooper.binge.companion.seerr.ui.settings.server.ServerGeneralSettings
import com.cooper.binge.companion.seerr.ui.settings.server.ServerJob
import com.cooper.binge.companion.seerr.ui.users.settings.EditorUiState
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.binge.designsystem.R as DesR

internal val CONNECTION = ConnectionSummary(baseUrl = "http://seerr.lan:5055", signInKind = SignInKind.Session, userName = "Scott")
internal val SERVER =
    ServerSummary(title = "Seerr", variant = SeerrVariant.Jellyseerr, versionLabel = "2.7.2", updateAvailable = false, commitsBehind = 0)
private const val SYNC_JOB_ID = "download-sync"
private const val SYNC_JOB_NAME = "Download Sync"
private const val A_MINUTE_MILLIS = 60_000L
private const val A_FRAME_MILLIS = 16L

internal fun jobsReady(
    scanRunning: Boolean,
    syncRunning: Boolean = false,
    busy: Set<String> = emptySet(),
    failed: Set<String> = emptySet(),
    scanId: String = PLEX_FULL_SCAN_JOB_ID,
) = JobsUiState.Ready(
    jobs =
        listOf(
            ServerJob(scanId, "Full Library Scan", JobInterval.Long, scanRunning, null),
            ServerJob(SYNC_JOB_ID, SYNC_JOB_NAME, JobInterval.Short, syncRunning, null),
        ),
    busyIds = busy,
    failedIds = failed,
)

internal val APP = AppSettings(bugReportUrl = "https://example.test/issues/new", shakeToReport = false)

/**
 * The settings sheet under a real D-pad: the summary and the groups in order, a group opening as a page that Back leaves
 * for the row that opened it, the admin's library scan and jobs, the switches, Binge, and the confirm Disconnect asks first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvSettingsSheetFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private val runs = mutableListOf<String>()
    private val cancels = mutableListOf<String>()
    private var jobsState by mutableStateOf<JobsUiState>(jobsReady(scanRunning = false))
    private var bingeOpens = 0
    private var listingOpens = 0
    private var connectOpens = 0
    private var binge by mutableStateOf(BingeStatus.NotInstalled)
    private var disconnects = 0
    private var dismissals = 0
    private var sharingUsage by mutableStateOf(false)
    private var household by mutableStateOf<ServerGeneralSettings?>(null)
    private var householdStatus by mutableStateOf(SaveStatus.Idle as SaveStatus)
    private var retries = 0
    private var agents by mutableStateOf<AgentsUiState>(AgentsUiState.Ready(emptyList()))
    private var agentForm by mutableStateOf(
        AgentForm(
            ServerAgent.Discord,
            enabled = false,
            options =
                mapOf(
                    AgentOption.DiscordWebhookUrl to "https://x.test",
                ),
        ),
    )
    private val agentEnabled = mutableListOf<Boolean>()

    @Test
    fun theSummaryLeadsTheFirstPageAndTakesFirstFocus() {
        setSheet(config = null)

        composeTestRule
            .onNode(hasText(CONNECTION.baseUrl, substring = true) and isFocusable())
            .assertIsFocused()
            .assert(hasText(string(R.string.tv_settings_signed_in_as, "Scott")))
    }

    @Test
    fun anAdminsGroupsComeInOrderAndDisconnectIsLast() {
        household = ServerGeneralSettings()
        agents = AgentsUiState.Ready(listOf(AgentSummary(ServerAgent.Discord, enabled = false)))
        setSheet(config = ServerConfig(system = SystemInfo(jobs = emptyList())), app = APP)

        val order =
            listOf(
                string(R.string.tv_settings_group_media),
                string(R.string.tv_settings_group_maintenance),
                string(R.string.tv_settings_group_household),
                string(R.string.settings_group_notifications),
                string(R.string.settings_group_app),
                string(R.string.hub_disconnect),
            )
        val tops = order.map { groupRow(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun aNonAdminNeverSeesTheMediaServerGroupOrItsScan() {
        setSheet(config = null)

        composeTestRule.onNodeWithText(string(R.string.tv_settings_group_media)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.tv_settings_start_library_scan)).assertDoesNotExist()
    }

    @Test
    fun anAdminOpensTheMediaServerAndTheScanTakesFocusFirst() {
        setSheet(config = ServerConfig())

        open(string(R.string.tv_settings_group_media))

        scanRow().assertIsFocused()
    }

    @Test
    fun pressingTheScanStartsIt() {
        setSheet(config = ServerConfig())
        open(string(R.string.tv_settings_group_media))

        press(Key.DirectionCenter)

        assertEquals(listOf(PLEX_FULL_SCAN_JOB_ID), runs)
        assertEquals(emptyList<String>(), cancels)
    }

    /** #1546: a Jellyfin or Emby server registers its scan as `jellyfin-full-scan`, and the row runs the id the server lists. */
    @Test
    fun aJellyfinServersScanRunsUnderItsOwnId() {
        jobsState = jobsReady(scanRunning = false, scanId = JELLYFIN_FULL_SCAN_JOB_ID)
        setSheet(config = ServerConfig())
        open(string(R.string.tv_settings_group_media))

        press(Key.DirectionCenter)

        assertEquals(listOf(JELLYFIN_FULL_SCAN_JOB_ID), runs)
    }

    /** A scan that is running offers Cancel in the same row, and pressing it cancels. */
    @Test
    fun aRunningScanOffersCancelInsteadAndPressingItCancels() {
        jobsState = jobsReady(scanRunning = true)
        setSheet(config = ServerConfig())
        open(string(R.string.tv_settings_group_media))

        composeTestRule.onNodeWithText(string(R.string.tv_settings_start_library_scan)).assertDoesNotExist()
        press(Key.DirectionCenter)

        assertEquals(listOf(PLEX_FULL_SCAN_JOB_ID), cancels)
        assertEquals(emptyList<String>(), runs)
    }

    /**
     * A call on its way and a refused one are said on the row itself and stay until the job's next attempt, as a live region:
     * neither passes as a note that clears on a timer (#1513).
     */
    @Test
    fun theScansStateIsSaidInPlaceAndStaysUntilItsNextAttempt() {
        setSheet(config = ServerConfig())
        open(string(R.string.tv_settings_group_media))

        jobsState = jobsReady(scanRunning = false, busy = setOf(PLEX_FULL_SCAN_JOB_ID))
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(string(R.string.tv_settings_job_working))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        jobsState = jobsReady(scanRunning = false, failed = setOf(PLEX_FULL_SCAN_JOB_ID))
        composeTestRule.mainClock.advanceTimeBy(A_MINUTE_MILLIS)
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(string(R.string.tv_settings_job_failed_short))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    /**
     * The services are one card of read-outs after the scan: each instance by its own name and markers, so two "Sonarr" rows
     * each show (#865), and nothing of where it files a request.
     */
    @Test
    fun theServicesAreOneCardOfNamesAfterTheScan() {
        val services =
            listOf(
                ServerService(1, "Sonarr", ServiceType.Sonarr, null, "HD-1080p", "/tv", is4k = false, isDefault = true),
                ServerService(2, "Sonarr", ServiceType.Sonarr, null, "SD", "/tv-sd", is4k = true, isDefault = false),
            )
        setSheet(config = ServerConfig(services = services))
        open(string(R.string.tv_settings_group_media))

        val card = composeTestRule.onNode(hasText(string(R.string.settings_group_services)) and isFocusable())
        card.assert(hasText("Sonarr · ${string(R.string.settings_service_default)}"))
        card.assert(hasText("Sonarr · ${string(R.string.settings_service_4k)}"))
        card.assertHasNoClickAction()
        composeTestRule.onNodeWithText("HD-1080p", substring = true).assertDoesNotExist()
    }

    /** OK on a job runs it: the row says it is working for a moment, then that it is done, until the sheet closes. */
    @Test
    fun pressingAJobRunsItAndItsRowSaysWorkingThenDone() {
        setSheet(config = ServerConfig(system = SystemInfo(jobs = emptyList())))
        open(string(R.string.tv_settings_group_maintenance))
        groupRow(SYNC_JOB_NAME).requestFocus()
        composeTestRule.mainClock.autoAdvance = false

        press(Key.DirectionCenter)
        composeTestRule.mainClock.advanceTimeByFrame()

        assertEquals(listOf(SYNC_JOB_ID), runs)
        jobRow(string(R.string.tv_settings_job_working)).assertExists()
        composeTestRule.mainClock.advanceTimeBy(JOB_MIN_WORKING_MILLIS + A_FRAME_MILLIS)
        jobRow(string(R.string.tv_settings_job_done)).assertExists()
    }

    /** OK on a running job stops it, and once it has stopped its row says so. */
    @Test
    fun pressingARunningJobStopsIt() {
        jobsState = jobsReady(scanRunning = false, syncRunning = true)
        setSheet(config = ServerConfig(system = SystemInfo(jobs = emptyList())))
        open(string(R.string.tv_settings_group_maintenance))
        groupRow(SYNC_JOB_NAME).requestFocus()

        press(Key.DirectionCenter)
        jobsState = jobsReady(scanRunning = false, syncRunning = false)
        composeTestRule.mainClock.advanceTimeBy(JOB_MIN_WORKING_MILLIS + A_FRAME_MILLIS)
        composeTestRule.waitForIdle()

        assertEquals(listOf(SYNC_JOB_ID), cancels)
        jobRow(string(R.string.tv_settings_job_stopped)).assertExists()
    }

    @Test
    fun aRefusedJobSaysToTryAgain() {
        jobsState = jobsReady(scanRunning = false, failed = setOf(SYNC_JOB_ID))
        setSheet(config = ServerConfig(system = SystemInfo(jobs = emptyList())))
        open(string(R.string.tv_settings_group_maintenance))

        jobRow(string(R.string.tv_settings_job_failed_short)).assertExists()
    }

    @Test
    fun backGoesUpAPageAndThenClosesTheSheet() {
        setSheet(config = null, app = APP)
        open(string(R.string.settings_group_app))

        pressBack()

        groupRow(string(R.string.settings_group_app)).assertIsFocused()
        assertEquals(0, dismissals)
        pressBack()
        assertEquals(1, dismissals)
    }

    @Test
    fun disconnectIsTheLastRowAndAsksFirst() {
        setSheet(config = null)
        val disconnect = string(R.string.hub_disconnect)

        open(disconnect)
        composeTestRule.onNodeWithText(string(R.string.hub_disconnect_confirm_title)).assertExists()
        val cancel = composeTestRule.onNode(hasText(string(DesR.string.action_cancel)) and isFocusable())
        cancel.assertIsFocused()
        assertEquals(0, disconnects)

        cancel.performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(string(R.string.hub_disconnect_confirm_title)).assertDoesNotExist()
        assertEquals(0, disconnects)
        assertEquals(0, dismissals)
    }

    @Test
    fun confirmingDisconnectRunsIt() {
        setSheet(config = null)
        open(string(R.string.hub_disconnect))

        val confirm = composeTestRule.onNode(hasText(string(R.string.tv_settings_disconnect_confirm)) and isFocusable())
        confirm.requestFocus()
        composeTestRule.waitForIdle()
        // The confirm is a dialog, a window of its own, so the key goes to its node rather than the activity's root.
        confirm.performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()

        assertEquals(1, disconnects)
    }

    /** A switch is a switch to a screen reader and says where it stands; one press flips it. */
    @Test
    fun aSwitchSaysWhereItStandsAndOnePressFlipsIt() {
        setSheet(config = null, app = APP)
        open(string(R.string.settings_group_app))
        val row = composeTestRule.onNode(hasText(string(R.string.settings_share_usage_data)) and isFocusable())
        row.requestFocus()
        composeTestRule.waitForIdle()
        row.assert(isSwitch(ToggleableState.Off))

        press(Key.DirectionCenter)

        assertTrue(sharingUsage)
        row.assert(isSwitch(ToggleableState.On))
    }

    /** Binge leads this app's page: open it when connected, its Companions screen when not, else its Play Store listing. */
    @Test
    fun bingeLeadsThisAppAndOpensBingeItsCompanionsOrItsListing() {
        binge = BingeStatus.Connected
        setSheet(config = null, app = APP)
        open(string(R.string.settings_group_app))

        bingeRow().assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(1, bingeOpens)

        binge = BingeStatus.NotConnected
        composeTestRule.waitForIdle()
        bingeRow().requestFocus()
        press(Key.DirectionCenter)
        assertEquals(1, connectOpens)

        binge = BingeStatus.NotInstalled
        composeTestRule.waitForIdle()
        bingeRow().requestFocus()
        press(Key.DirectionCenter)
        assertEquals(1, listingOpens)
    }

    /** The bug report's code holds focus, and Back, not a Close row, is the way back to the row that opened it. */
    @Test
    fun theBugReportsCodeHoldsFocusAndBackReturnsToItsRow() {
        setSheet(config = null, app = APP)
        open(string(R.string.settings_group_app))
        composeTestRule.onNode(hasText(string(R.string.settings_report_bug)) and isFocusable()).requestFocus()
        composeTestRule.waitForIdle()

        press(Key.DirectionCenter)
        composeTestRule.onNodeWithText(string(R.string.tv_settings_report_bug_scan)).assertExists()
        composeTestRule.onNodeWithContentDescription(string(R.string.tv_settings_report_bug_code_description)).assertIsFocused()
        composeTestRule.onNodeWithText(string(R.string.tv_sheet_close)).assertDoesNotExist()
        pressBack()

        composeTestRule.onNode(hasText(string(R.string.settings_report_bug)) and isFocusable()).assertIsFocused()
        assertFalse(dismissals > 0)
    }

    @Test
    fun pressingAHouseholdSwitchFlipsItAndSavesAsItIsMade() {
        household = ServerGeneralSettings(hideAvailable = false)
        setSheet(config = ServerConfig())
        open(string(R.string.tv_settings_group_household))

        val row = composeTestRule.onNode(hasText(string(R.string.settings_hide_available)) and isFocusable())
        row.requestFocus()
        press(Key.DirectionCenter)

        assertEquals(true, household?.hideAvailable)
        row.assert(isSwitch(ToggleableState.On))
    }

    @Test
    fun aSwitchTheServerLacksIsNotOffered() {
        household = ServerGeneralSettings(hideRequested = null, specialEpisodes = null, hideBlocklisted = null)
        setSheet(config = ServerConfig())
        open(string(R.string.tv_settings_group_household))

        composeTestRule.onNodeWithText(string(R.string.server_settings_hide_requested)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.server_settings_special_episodes)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.server_settings_partial_requests)).assertExists()
    }

    @Test
    fun aFailedHouseholdSaveIsSaidInPlaceWithRetry() {
        household = ServerGeneralSettings()
        householdStatus = SaveStatus.Failed
        setSheet(config = ServerConfig())
        open(string(R.string.tv_settings_group_household))

        composeTestRule.onNodeWithText(string(DesR.string.save_status_failed)).assertExists()
        composeTestRule.onNodeWithText(string(DesR.string.save_status_retry)).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun aNonAdminHasNeitherHouseholdNorAgents() {
        setSheet(config = null)

        composeTestRule.onNodeWithText(string(R.string.tv_settings_group_household)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.settings_group_notifications)).assertDoesNotExist()
    }

    @Test
    fun anAgentOpensItsPageAndItsSwitchTurnsItOn() {
        agents = AgentsUiState.Ready(listOf(AgentSummary(ServerAgent.Discord, enabled = false)))
        setSheet(config = ServerConfig())
        open(string(R.string.settings_group_notifications))
        open(string(R.string.settings_agent_discord))

        val toggle = composeTestRule.onNode(hasText(string(R.string.settings_agent_discord)) and isFocusable())
        toggle.assertIsFocused().assert(isSwitch(ToggleableState.Off))
        press(Key.DirectionCenter)

        assertEquals(listOf(true), agentEnabled)
        pressBack()
        groupRow(string(R.string.settings_agent_discord)).assertIsFocused()
    }

    @Test
    fun anAgentThatCannotSendYetCannotBeSwitchedOnFromTheTv() {
        agents = AgentsUiState.Ready(listOf(AgentSummary(ServerAgent.Discord, enabled = false)))
        agentForm = AgentForm(ServerAgent.Discord, enabled = false)
        setSheet(config = ServerConfig())
        open(string(R.string.settings_group_notifications))
        open(string(R.string.settings_agent_discord))

        composeTestRule.onNodeWithText(string(R.string.tv_settings_agent_setup_first)).assertExists()
        composeTestRule.onNode(hasText(string(R.string.settings_agent_discord)) and isFocusable()).assertDoesNotExist()
    }

    private fun setSheet(
        config: ServerConfig?,
        app: AppSettings? = null,
    ) {
        composeTestRule.setContent {
            BingeTvTheme {
                TvSettingsSheet(
                    state =
                        SettingsUiState.Ready(
                            connection = CONNECTION,
                            server = SERVER,
                            config = config,
                            app = app?.copy(shareUsageData = sharingUsage, binge = binge),
                        ),
                    actions =
                        TvSettingsActions(
                            onOpenBinge = { bingeOpens++ },
                            onOpenBingeListing = { listingOpens++ },
                            onConnectBinge = { connectOpens++ },
                            onDisconnect = { disconnects++ },
                            jobs = TvJobActions(onRun = { runs += it }, onCancel = { cancels += it }),
                            onToggleShareUsageData = { sharingUsage = it },
                            household =
                                household?.takeIf { config != null }?.let {
                                    TvHousehold(
                                        settings = it,
                                        status = householdStatus,
                                        onChange = { change -> household = change(it) },
                                        onRetry = { retries++ },
                                    )
                                },
                            agents =
                                if (config != null) {
                                    TvAgents(
                                        state = agents,
                                        page = { agent, nav ->
                                            TvAgentPage(
                                                nav = nav,
                                                agent = agent,
                                                state = EditorUiState.Ready(draft = agentForm, saved = agentForm),
                                                onSetEnabled = { agentEnabled += it },
                                                onRetry = {},
                                            )
                                        },
                                    )
                                } else {
                                    null
                                },
                        ),
                    onDismissRequest = { dismissals++ },
                    jobs = if (config != null) jobsState else JobsUiState.Loading,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /** Presses the row named [title] on the current page. */
    private fun open(title: String) {
        groupRow(title).requestFocus()
        composeTestRule.waitForIdle()
        press(Key.DirectionCenter)
    }

    private fun groupRow(title: String) = composeTestRule.onNode(hasText(title) and isFocusable())

    private fun jobRow(line: String) = composeTestRule.onNode(hasText(SYNC_JOB_NAME) and hasText(line) and isFocusable())

    private fun isSwitch(state: ToggleableState) =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch) and
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)

    private fun bingeRow() = composeTestRule.onNode(hasText(string(R.string.settings_binge)) and isFocusable())

    private fun scanRow() = composeTestRule.onNode(hasText(string(R.string.tv_settings_start_library_scan)) and isFocusable())

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }
}
