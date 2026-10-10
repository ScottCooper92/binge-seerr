package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.Text
import com.binge.designsystem.tv.focus.TvArrivalFocusEffect
import com.binge.designsystem.tv.focus.rememberTvArrivalFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val RETRY = "Retry"
private const val EDIT = "Edit connection"
private const val DISCONNECT = "Disconnect"
private const val NOTE_MILLIS = 4_000L
private const val PART_OF_A_NOTE = 1_000L

/**
 * The two pieces every TV board leans on (#1054): the plate's arrival, which lands on the first way out it has, and
 * the transient note, the board's answer to a snackbar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvBoardHelpersTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    @Test
    fun thePlateLandsOnItsPrimaryWayOut() {
        setPlate(primary = RETRY, alternate = EDIT, secondary = DISCONNECT)

        button(RETRY).assertIsFocused()
    }

    @Test
    fun withNoPrimaryThePlateLandsOnTheAlternate() {
        setPlate(primary = null, alternate = EDIT, secondary = DISCONNECT)

        button(EDIT).assertIsFocused()
    }

    @Test
    fun withOnlyASecondaryThePlateLandsOnIt() {
        setPlate(primary = null, alternate = null, secondary = DISCONNECT)

        button(DISCONNECT).assertIsFocused()
    }

    /** A newer event replaces the one showing at once, and the note clears a few seconds after the last. */
    @Test
    fun aNewerEventSupersedesTheNoteAndTheNoteClearsAfterAFewSeconds() {
        val events = MutableSharedFlow<String>(extraBufferCapacity = 1)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            BingeTvTheme { rememberTvTransientEvent(events)?.let { Text(it) } }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        events.tryEmit("Approved")
        composeTestRule.mainClock.advanceTimeBy(PART_OF_A_NOTE)
        composeTestRule.onNodeWithText("Approved").assertExists()

        events.tryEmit("Declined")
        composeTestRule.mainClock.advanceTimeBy(PART_OF_A_NOTE)
        composeTestRule.onNodeWithText("Approved").assertDoesNotExist()
        composeTestRule.onNodeWithText("Declined").assertExists()

        composeTestRule.mainClock.advanceTimeBy(NOTE_MILLIS)
        composeTestRule.onNodeWithText("Declined").assertDoesNotExist()
    }

    private fun setPlate(
        primary: String?,
        alternate: String?,
        secondary: String?,
    ) {
        composeTestRule.setContent {
            BingeTvTheme {
                val arrival = rememberTvArrivalFocus()
                TvArrivalFocusEffect(arrival)
                TvBoardPlate(
                    body = "Can't reach the server",
                    primary = primary?.let { it to {} },
                    alternate = alternate?.let { it to {} },
                    secondary = secondary?.let { it to {} },
                    arrival = arrival,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun button(label: String) = composeTestRule.onNode(hasText(label) and isFocusable())
}
