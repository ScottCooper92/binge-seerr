package com.cooper.binge.companion.seerr.ui.tv.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.settings.AppSettings
import com.cooper.binge.companion.seerr.ui.settings.ConnectionSummary
import com.cooper.binge.companion.seerr.ui.settings.ServerConfig
import com.cooper.binge.companion.seerr.ui.settings.ServerService
import com.cooper.binge.companion.seerr.ui.settings.ServerSummary
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.settings.SignInKind
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.binge.designsystem.R as DesR

private val CONNECTION = ConnectionSummary(baseUrl = "http://seerr.lan:5055", signInKind = SignInKind.Session, userName = "Scott")
private val SERVER =
    ServerSummary(title = "Seerr", variant = SeerrVariant.Jellyseerr, versionLabel = "2.7.2", updateAvailable = false, commitsBehind = 0)
private val APP = AppSettings(bugReportUrl = "https://example.test/issues/new", shakeToReport = false)

/**
 * The settings sheet under a real D-pad: a group opens as a page and Back returns to the row that opened it, the admin's
 * library scan, the confirm Disconnect asks first on its own page, and the actions on a page that has any.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvSettingsSheetFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var scans = 0
    private var edits = 0
    private var disconnects = 0
    private var dismissals = 0
    private var sharingUsage by mutableStateOf(false)

    @Test
    fun aNonAdminNeverSeesTheMediaServerGroupOrItsScan() {
        setSheet(config = null)

        composeTestRule.onNodeWithText(string(R.string.server_settings_media_server)).assertDoesNotExist()
        composeTestRule.onNodeWithText(string(R.string.tv_settings_start_library_scan)).assertDoesNotExist()
    }

    @Test
    fun anAdminOpensTheMediaServerAndTheScanTakesFocusFirst() {
        setSheet(config = ServerConfig())

        open(string(R.string.server_settings_media_server))

        scanRow().assertIsFocused()
    }

    @Test
    fun pressingTheScanStartsItAndTheOutcomeIsAnnounced() {
        setSheet(config = ServerConfig())
        open(string(R.string.server_settings_media_server))

        press(Key.DirectionCenter)

        assertEquals(1, scans)
        composeTestRule
            .onNodeWithText(string(R.string.tv_settings_scan_started))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    /** The scan's outcome is announced, but the static "change this on the phone" note is not (#1039). */
    @Test
    fun theStaticReadOnlyNoteIsNotALiveRegion() {
        setSheet(config = ServerConfig())
        open(string(R.string.server_settings_media_server))

        composeTestRule
            .onNodeWithText(string(R.string.tv_settings_read_only_note))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    /** Seerr doesn't make instance names unique, so two "Sonarr" rows must each show their own instance (#865). */
    @Test
    fun twoServicesWithOneNameEachShowTheirOwnInstance() {
        val services =
            listOf(
                ServerService(1, "Sonarr", ServiceType.Sonarr, null, "HD-1080p", "/tv", is4k = false, isDefault = false),
                ServerService(2, "Sonarr", ServiceType.Sonarr, null, "SD", "/tv-sd", is4k = false, isDefault = false),
            )
        setSheet(config = ServerConfig(services = services))

        open(string(R.string.settings_group_services))

        composeTestRule.onNodeWithText("HD-1080p · /tv", substring = true).assertExists()
        composeTestRule.onNodeWithText("SD · /tv-sd", substring = true).assertExists()
    }

    @Test
    fun editConnectionRunsAndIsTheFirstThingFocusedOnItsPage() {
        setSheet(config = null)
        open(string(R.string.settings_group_connection))

        composeTestRule.onNode(hasText(string(R.string.settings_edit_connection)) and isFocusable()).assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(1, edits)
    }

    @Test
    fun backGoesUpAPageAndThenClosesTheSheet() {
        setSheet(config = null)
        open(string(R.string.settings_group_connection))

        pressBack()

        groupRow(string(R.string.settings_group_connection)).assertIsFocused()
        assertEquals(0, dismissals)
        pressBack()
        assertEquals(1, dismissals)
    }

    @Test
    fun disconnectIsTheLastRowAndAsksOnAPageOfItsOwn() {
        setSheet(config = null)
        val disconnect = string(R.string.hub_disconnect)

        open(disconnect)
        composeTestRule.onNodeWithText(string(R.string.hub_disconnect_confirm_title)).assertExists()
        composeTestRule.onNode(hasText(string(DesR.string.action_cancel)) and isFocusable()).assertIsFocused()
        assertEquals(0, disconnects)

        pressBack()
        groupRow(disconnect).assertIsFocused()
    }

    @Test
    fun confirmingDisconnectRunsIt() {
        setSheet(config = null)
        open(string(R.string.hub_disconnect))

        composeTestRule.onNode(hasText(string(R.string.tv_settings_disconnect_confirm)) and isFocusable()).requestFocus()
        composeTestRule.waitForIdle()
        press(Key.DirectionCenter)

        assertEquals(1, disconnects)
    }

    /** The switch reports where it stands on its own row, and one press flips it. */
    @Test
    fun aSwitchSaysWhereItStandsAndOnePressFlipsIt() {
        setSheet(config = null, app = APP)
        open(string(R.string.settings_group_app))
        composeTestRule.onNode(hasText(string(R.string.settings_share_usage_data)) and isFocusable()).requestFocus()
        composeTestRule.waitForIdle()
        sharingUsageRow(string(R.string.tv_settings_toggle_off)).assertExists()

        press(Key.DirectionCenter)

        assertTrue(sharingUsage)
        sharingUsageRow(string(R.string.tv_settings_toggle_on)).assertExists()
    }

    @Test
    fun theBugReportOpensItsCodeAndBackReturnsToItsRow() {
        setSheet(config = null, app = APP)
        open(string(R.string.settings_group_app))
        composeTestRule.onNode(hasText(string(R.string.settings_report_bug)) and isFocusable()).requestFocus()
        composeTestRule.waitForIdle()

        press(Key.DirectionCenter)
        composeTestRule.onNodeWithText(string(R.string.tv_settings_report_bug_scan)).assertExists()
        pressBack()

        composeTestRule.onNode(hasText(string(R.string.settings_report_bug)) and isFocusable()).assertIsFocused()
        assertFalse(dismissals > 0)
    }

    private fun setSheet(
        config: ServerConfig?,
        app: AppSettings? = null,
    ) {
        val events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
        composeTestRule.setContent {
            BingeTvTheme {
                TvSettingsSheet(
                    state =
                        SettingsUiState.Ready(
                            connection = CONNECTION,
                            server = SERVER,
                            config = config,
                            app = app?.copy(shareUsageData = sharingUsage),
                        ),
                    actions =
                        TvSettingsActions(
                            onEditConnection = { edits++ },
                            onDisconnect = { disconnects++ },
                            onStartLibraryScan = {
                                scans++
                                events.tryEmit(EditorEvent.Notice(R.string.tv_settings_scan_started))
                            },
                            onToggleShareUsageData = { sharingUsage = it },
                        ),
                    onDismissRequest = { dismissals++ },
                    libraryScanEvents = if (config != null) events else emptyFlow(),
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

    private fun sharingUsageRow(state: String) =
        composeTestRule.onNode(hasText(string(R.string.settings_share_usage_data)) and hasText(state))

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
