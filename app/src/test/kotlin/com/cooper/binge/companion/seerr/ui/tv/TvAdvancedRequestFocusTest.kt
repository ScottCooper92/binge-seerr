package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.AdvancedRequestError
import com.cooper.binge.companion.seerr.ui.AdvancedRequestUiState
import com.cooper.binge.companion.seerr.ui.Choice
import com.cooper.binge.companion.seerr.ui.DestinationChoices
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import com.cooper.binge.companion.seerr.util.isFocusableLeaf
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The advanced picker under a real D-pad, on the television qualifier `TvSetupFocusTest` records the need
 * for. The walk is the whole subject: server rows, profile rows, folder rows, then Request and Close, with OK
 * committing exactly the row it lands on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvAdvancedRequestFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private val servers = mutableListOf<Int>()
    private val profiles = mutableListOf<Int>()
    private val folders = mutableListOf<String>()
    private var submitted = 0
    private var openedSetup = 0
    private var closed = 0

    private val actions =
        TvAdvancedRequestActions(
            onSelectServer = { servers += it },
            onSelectProfile = { profiles += it },
            onSelectRootFolder = { folders += it },
            onSubmit = { submitted++ },
            onOpenSetup = { openedSetup++ },
            onClose = { closed++ },
        )

    @Test
    fun thePickerLandsOnTheChosenServer() {
        setScreen(ready())

        row("Radarr").assertIsFocused()
        row("Radarr").assertIsSelected()
    }

    @Test
    fun okOnAnotherServerCommitsIt() {
        setScreen(ready())

        pressDown()
        row("Radarr 4K").assertIsFocused()
        pressOk()

        assertEquals(listOf(2), servers)
    }

    @Test
    fun okOnTheChosenServerIsANoOp() {
        setScreen(ready())

        pressOk()

        assertTrue("re-pressing the chosen server must not reload its choices", servers.isEmpty())
    }

    @Test
    fun theWalkReachesAProfileAndOkCommitsIt() {
        setScreen(ready())

        pressDown()
        pressDown()
        row("HD-1080p").assertIsFocused()
        pressDown()
        row("Ultra-HD").assertIsFocused()
        pressOk()

        assertEquals(listOf(20), profiles)
    }

    @Test
    fun theWalkReachesAFolderAndOkCommitsIt() {
        setScreen(ready())

        repeat(times = 5) { pressDown() }
        row("/data/media/kids").assertIsFocused()
        pressOk()

        assertEquals(listOf("/data/media/kids"), folders)
    }

    @Test
    fun theWalkEndsOnRequestAndOkSubmits() {
        setScreen(ready())

        repeat(times = 6) { pressDown() }
        button(R.string.advanced_submit).assertIsFocused()
        pressOk()

        assertEquals(1, submitted)
    }

    @Test
    fun downFromRequestReachesClose() {
        setScreen(ready())

        repeat(times = 7) { pressDown() }
        button(R.string.advanced_close).assertIsFocused()
        pressOk()

        assertEquals(1, closed)
    }

    @Test
    fun whileTheChoicesLoadTheWalkSkipsStraightToRequest() {
        setScreen(ready(isLoadingChoices = true))

        pressDown()
        pressDown()
        button(R.string.advanced_submit).assertIsFocused()
        pressOk()

        assertEquals("Request is disabled until the choices arrive", 0, submitted)
    }

    @Test
    fun notConnectedLandsOnOpenSetup() {
        setScreen(AdvancedRequestUiState.Failed(AdvancedRequestError.NotConnected))

        button(R.string.advanced_open_setup).assertIsFocused()
        pressOk()

        assertEquals(1, openedSetup)
    }

    @Test
    fun anyOtherFailureLandsOnClose() {
        setScreen(AdvancedRequestUiState.Failed(AdvancedRequestError.Unreachable))

        button(R.string.advanced_close).assertIsFocused()
        pressOk()

        assertEquals(1, closed)
    }

    /**
     * The arrival guard on the real root: the picker opens with no server rows yet, the user presses Down and focus lands on
     * a button, and then the choices arrive. The arrival's target attaches late, and it must not pull focus back from where the
     * user went. Without `TvUserMovesHost` around the shell nothing notes the press and the arrival claims. The first press only
     * gives focus a place to start, with nothing focused for the host to see it through; the second is the one that is noted.
     */
    @Test
    fun aDirectionPressBeforeTheChoicesArriveIsNotUndoneWhenTheyDo() {
        var state by mutableStateOf<AdvancedRequestUiState>(ready(withChoices = false))
        composeTestRule.setContent { TvAdvancedRequestShell(state = state, actions = actions) }
        composeTestRule.waitForIdle()

        pressDown()
        pressDown()
        composeTestRule.onNode(isFocused()).assertExists()
        state = ready()
        composeTestRule.waitForIdle()

        row("Radarr").assertIsNotFocused()
    }

    private fun setScreen(state: AdvancedRequestUiState) {
        composeTestRule.setContent {
            BingeTvTheme { TvAdvancedRequestScreen(state = state, actions = actions) }
        }
        composeTestRule.waitForIdle()
    }

    private fun row(label: String) = composeTestRule.onNode(hasText(label) and isFocusableLeaf())

    // A disabled button is a focusable surface over its own text node rather than one merged node, so the
    // label may sit a level below the node that carries focus.
    private fun button(label: Int) =
        composeTestRule.onNode(
            (hasText(string(label)) or hasAnyDescendant(hasText(string(label)))) and isFocusableLeaf(),
        )

    private fun pressDown() {
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.waitForIdle()
    }

    private fun pressOk() {
        composeTestRule.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        composeTestRule.waitForIdle()
    }

    private fun ready(
        isLoadingChoices: Boolean = false,
        withChoices: Boolean = true,
    ) = AdvancedRequestUiState.Ready(
        destination =
            DestinationChoices(
                servers = if (withChoices) listOf(Choice(1, "Radarr"), Choice(2, "Radarr 4K")) else emptyList(),
                serverId = 1.takeIf { withChoices },
                profiles = if (withChoices) listOf(Choice(10, "HD-1080p"), Choice(20, "Ultra-HD")) else emptyList(),
                profileId = 10.takeIf { withChoices },
                rootFolders = if (withChoices) listOf("/data/media/movies", "/data/media/kids") else emptyList(),
                rootFolder = "/data/media/movies".takeIf { withChoices },
                loadingChoices = isLoadingChoices,
            ),
        isSubmitting = false,
        error = null,
    )
}
