package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.Text
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val NOTE_MILLIS = 4_000L
private const val PART_OF_A_NOTE = 1_000L

/**
 * The transient note every TV board leans on (#1054), the board's answer to a snackbar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvBoardHelpersTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

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

    /** A frame's note shows from the first frame, clears like any other, and gives way to a newer event (#1053). */
    @Test
    fun anInitialNoteShowsAtOnceAndClearsLikeTheFirstEvent() {
        val events = MutableSharedFlow<String>(extraBufferCapacity = 1)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            BingeTvTheme { rememberTvTransientEvent(events, initial = "Approved")?.let { Text(it) } }
        }
        composeTestRule.onNodeWithText("Approved").assertExists()

        composeTestRule.mainClock.advanceTimeBy(NOTE_MILLIS + PART_OF_A_NOTE)
        composeTestRule.onNodeWithText("Approved").assertDoesNotExist()

        events.tryEmit("Declined")
        composeTestRule.mainClock.advanceTimeBy(PART_OF_A_NOTE)
        composeTestRule.onNodeWithText("Declined").assertExists()
    }

    @Test
    fun aNewerEventSupersedesAnInitialNote() {
        val events = MutableSharedFlow<String>(extraBufferCapacity = 1)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            BingeTvTheme { rememberTvTransientEvent(events, initial = "Approved")?.let { Text(it) } }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        events.tryEmit("Declined")
        composeTestRule.mainClock.advanceTimeBy(PART_OF_A_NOTE)
        composeTestRule.onNodeWithText("Approved").assertDoesNotExist()
        composeTestRule.onNodeWithText("Declined").assertExists()
    }
}
