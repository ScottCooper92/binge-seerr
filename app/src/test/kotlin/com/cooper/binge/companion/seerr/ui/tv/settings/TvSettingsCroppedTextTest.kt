package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.hub.BingeStatus
import com.cooper.binge.companion.seerr.ui.settings.ServerConfig
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SystemInfo
import com.cooper.binge.companion.seerr.ui.settings.server.AgentSummary
import com.cooper.binge.companion.seerr.ui.settings.server.AgentsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.JobInterval
import com.cooper.binge.companion.seerr.ui.settings.server.JobsUiState
import com.cooper.binge.companion.seerr.ui.settings.server.PLEX_FULL_SCAN_JOB_ID
import com.cooper.binge.companion.seerr.ui.settings.server.ServerAgent
import com.cooper.binge.companion.seerr.ui.settings.server.ServerGeneralSettings
import com.cooper.binge.companion.seerr.ui.settings.server.ServerJob
import com.cooper.binge.companion.seerr.util.assertNoCroppedText
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val SYNC_JOB_ID = "download-sync"
private const val A_FEW_HOURS_MILLIS = 3 * 60 * 60_000L

/**
 * No row on any Settings page is cropped at 1×: a detail is one short line that fits, or it is not there. The first
 * pass found the "This app" rows reading "Found something wrong with this app? S…" on a real television, which nobody
 * can read, so the guard looks at what the layout cut rather than at the strings. It runs in English and in Spanish,
 * whose strings are the longer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class TvSettingsCroppedText {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    @Test
    fun theFirstPageFits() = fits(null)

    @Test
    fun theMediaServerPageFits() = fits(R.string.tv_settings_group_media)

    @Test
    fun theHouseholdPageFits() = fits(R.string.tv_settings_group_household)

    @Test
    fun aHouseholdChangeThatDidNotSaveFits() = fits(R.string.tv_settings_group_household, status = SaveStatus.Failed)

    @Test
    fun aScanTheServerRefusedFits() = fits(R.string.tv_settings_group_media, jobs = jobs(failed = setOf(PLEX_FULL_SCAN_JOB_ID)))

    @Test
    fun aScanThatIsRunningFits() = fits(R.string.tv_settings_group_media, jobs = jobs(scanRunning = true))

    @Test
    fun theMaintenancePageFits() = fits(R.string.tv_settings_group_maintenance)

    @Test
    fun aJobThatIsRunningOrDueFits() = fits(R.string.tv_settings_group_maintenance, jobs = jobs(syncRunning = true))

    @Test
    fun aJobTheServerRefusedFits() = fits(R.string.tv_settings_group_maintenance, jobs = jobs(failed = setOf(SYNC_JOB_ID)))

    @Test
    fun aJobBeingPressedFits() = fits(R.string.tv_settings_group_maintenance, jobs = jobs(busy = setOf(SYNC_JOB_ID)))

    @Test
    fun theNotificationsPageFits() = fits(R.string.settings_group_notifications)

    @Test
    fun thisAppPageFits() = fits(R.string.settings_group_app)

    @Test
    fun thisAppPageWithoutBingeFits() = fits(R.string.settings_group_app, binge = BingeStatus.NotInstalled)

    /** Opens [group] from the first page, or stays on the first page when it is null, and asserts that nothing on it is cropped. */
    protected fun fits(
        group: Int?,
        jobs: JobsUiState = jobs(),
        status: SaveStatus = SaveStatus.Idle,
        binge: BingeStatus = BingeStatus.Connected,
    ) {
        setSheet(jobs, status, binge)
        if (group != null) press(string(group))
        composeTestRule.assertNoCroppedText(group?.let { string(it) } ?: "the first Settings page")
    }

    private fun jobs(
        scanRunning: Boolean = false,
        syncRunning: Boolean = false,
        busy: Set<String> = emptySet(),
        failed: Set<String> = emptySet(),
    ) = JobsUiState.Ready(
        jobs =
            listOf(
                ServerJob(PLEX_FULL_SCAN_JOB_ID, "Full Library Scan", JobInterval.Long, scanRunning, null),
                ServerJob(SYNC_JOB_ID, "Download Sync", JobInterval.Short, syncRunning, System.currentTimeMillis() + A_FEW_HOURS_MILLIS),
            ),
        busyIds = busy,
        failedIds = failed,
    )

    private fun setSheet(
        jobs: JobsUiState,
        status: SaveStatus,
        binge: BingeStatus,
    ) {
        composeTestRule.setContent {
            BingeTvTheme {
                TvSettingsSheet(
                    state =
                        SettingsUiState.Ready(
                            connection = CONNECTION,
                            server = SERVER,
                            config = ServerConfig(system = SystemInfo(jobs = emptyList())),
                            app = APP.copy(binge = binge),
                        ),
                    actions =
                        TvSettingsActions(
                            household =
                                TvHousehold(
                                    settings = ServerGeneralSettings(),
                                    status = status,
                                    onChange = {},
                                    onRetry = {},
                                ),
                            agents =
                                TvAgents(
                                    state = AgentsUiState.Ready(listOf(AgentSummary(ServerAgent.Discord, enabled = false))),
                                    page = { _, _ -> },
                                ),
                        ),
                    onDismissRequest = {},
                    jobs = jobs,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun press(title: String) {
        composeTestRule.onNode(hasText(title) and isFocusable()).requestFocus()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
    }
}

/** [TvSettingsCroppedText] in English. */
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvSettingsCroppedTextTest : TvSettingsCroppedText()

/** [TvSettingsCroppedText] in Spanish. */
@Config(qualifiers = "es-w960dp-h540dp-television-xhdpi")
class TvSettingsCroppedTextSpanishTest : TvSettingsCroppedText()
