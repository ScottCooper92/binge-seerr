package io.github.scottcooper92.binge.seerr.ui.tv.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.ui.settings.ConnectionSummary
import io.github.scottcooper92.binge.seerr.ui.settings.ServerConfig
import io.github.scottcooper92.binge.seerr.ui.settings.ServerSummary
import io.github.scottcooper92.binge.seerr.ui.settings.SettingsUiState
import io.github.scottcooper92.binge.seerr.ui.settings.SignInKind
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private val CONNECTION = ConnectionSummary(baseUrl = "http://seerr.lan:5055", signInKind = SignInKind.Session, userName = "Scott")
private val SERVER =
    ServerSummary(title = "Seerr", variant = SeerrVariant.Jellyseerr, versionLabel = "2.7.2", updateAvailable = false, commitsBehind = 0)

/**
 * The media server row's library scan: admin-only, and running the same job the phone Jobs page runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvSettingsBoardFocusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var scans = 0

    @Test
    fun aNonAdminNeverSeesTheScanOption() {
        setBoard(config = null)

        composeTestRule.onNodeWithText(string(R.string.tv_settings_start_library_scan)).assertDoesNotExist()
    }

    @Test
    fun anAdminSeesTheMediaServerRowWithItsScanOption() {
        setBoard(config = ServerConfig(), initialFocusedKey = KEY_MEDIA_SERVER)

        plexRow().assertExists()
        option().assertExists()
    }

    @Test
    fun selectingTheOptionStartsTheScanAndShowsItStarted() {
        // Entry is redirected to the described row by the board's own onEnter (root CLAUDE.md's
        // documented trap), so the test asserts the routed target's key rather than requestFocus()ing
        // a node and trusting it stays put.
        setBoard(config = ServerConfig(), initialFocusedKey = KEY_MEDIA_SERVER)
        plexRow().requestFocus()
        composeTestRule.waitForIdle()

        pressRight()
        option().assertIsFocused()
        pressOk()

        assertEquals(1, scans)
        composeTestRule.onNodeWithText(string(R.string.tv_settings_scan_started)).assertExists()
    }

    private fun setBoard(
        config: ServerConfig?,
        initialFocusedKey: String? = null,
    ) {
        val events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
        composeTestRule.setContent {
            BingeTvTheme {
                TvSettingsBoard(
                    state = SettingsUiState.Ready(connection = CONNECTION, server = SERVER, config = config),
                    onEditConnection = {},
                    onDisconnect = {},
                    onStartLibraryScan = {
                        scans++
                        events.tryEmit(EditorEvent.Notice(R.string.tv_settings_scan_started))
                    },
                    libraryScanEvents = if (config != null) events else emptyFlow(),
                    initialFocusedKey = initialFocusedKey,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun plexRow() = composeTestRule.onNode(hasText(string(R.string.user_origin_plex)) and isFocusable())

    private fun option() = composeTestRule.onNode(hasText(string(R.string.tv_settings_start_library_scan)) and isFocusable())

    private fun pressRight() = press(Key.DirectionRight)

    private fun pressOk() = press(Key.DirectionCenter)

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)
}
