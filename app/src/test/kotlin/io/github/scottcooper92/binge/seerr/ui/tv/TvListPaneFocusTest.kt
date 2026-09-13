package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ROW_SERVER = "Server"
private const val ROW_DISCONNECT = "Disconnect"
private const val OPTION_DISCONNECT = "Disconnect now"

/**
 * The list/pane board under a real D-pad: entry lands on the described row whatever was asked for, → reaches
 * the pane's option, ← returns to the row the option belongs to, and OK on an option commits it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-television-xhdpi", application = android.app.Application::class)
class TvListPaneFocusTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val committed = mutableListOf<String>()
    private var selected = 0

    @Test
    fun entryIsRedirectedToTheDescribedRow() {
        setBoard(describedKey = "disconnect")

        row(ROW_SERVER).requestFocus()
        composeTestRule.waitForIdle()

        row(ROW_DISCONNECT).assertIsFocused()
    }

    @Test
    fun rightReachesTheOptionAndLeftReturnsToItsRow() {
        setBoard(describedKey = "disconnect")
        row(ROW_DISCONNECT).requestFocus()
        composeTestRule.waitForIdle()

        pressRight()
        option().assertIsFocused()
        pressLeft()

        row(ROW_DISCONNECT).assertIsFocused()
        assertEquals(listOf("disconnect"), committed.distinct())
    }

    @Test
    fun okOnAnOptionCommitsIt() {
        setBoard(describedKey = "disconnect")
        row(ROW_DISCONNECT).requestFocus()
        composeTestRule.waitForIdle()
        pressRight()

        pressOk()

        assertEquals(1, selected)
    }

    private fun setBoard(describedKey: String) {
        composeTestRule.setContent {
            var focusedKey by remember { mutableStateOf<String?>(describedKey) }
            BingeTvTheme {
                TvListPaneBoard(
                    title = "Settings",
                    groups =
                        listOf(
                            TvPaneGroup(
                                title = "Connection",
                                rows =
                                    listOf(
                                        TvPaneRow(key = "server", label = ROW_SERVER, body = "http://seerr.lan:5055"),
                                        TvPaneRow(
                                            key = "disconnect",
                                            label = ROW_DISCONNECT,
                                            body = "Removes the saved sign-in.",
                                            options = listOf(TvPaneOption(label = OPTION_DISCONNECT, onSelect = { selected++ })),
                                        ),
                                    ),
                            ),
                        ),
                    focusedKey = focusedKey,
                    onFocusRow = {
                        committed += it
                        focusedKey = it
                    },
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun row(label: String) = composeTestRule.onNode(hasText(label) and isFocusable())

    private fun option() = composeTestRule.onNode(hasText(OPTION_DISCONNECT) and isFocusable())

    private fun pressRight() = press(Key.DirectionRight)

    private fun pressLeft() = press(Key.DirectionLeft)

    private fun pressOk() = press(Key.DirectionCenter)

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }
}
