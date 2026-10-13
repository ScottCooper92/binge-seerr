package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.ui.test.onNodeWithText
import androidx.tv.material3.Text
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a television page's last action is doing, and how its answer settles it (#1510). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvActionRunTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private val events = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    private var attempts = 0
    private var closed = 0
    private lateinit var run: TvActionRun

    private fun show(initial: SaveStatus = SaveStatus.Idle) {
        composeTestRule.setContent {
            BingeTvTheme {
                run = rememberTvActionRun(events, initial) { settle(failed = !it) }
                run.OnSaved { closed++ }
                Text(run.status.toString())
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun answer(ok: Boolean) {
        events.tryEmit(ok)
        composeTestRule.waitForIdle()
    }

    @Test
    fun anActionSavesThenIsSavedAndClosesOnlyAfterItIsRead() {
        show()

        composeTestRule.runOnIdle { run.run { attempts++ } }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Saving").assertExists()

        composeTestRule.mainClock.autoAdvance = false
        events.tryEmit(true)
        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.onNodeWithText("Saved").assertExists()
        assertEquals(0, closed)

        composeTestRule.mainClock.advanceTimeBy(TV_SAVED_HOLD_MILLIS + 100)
        assertEquals(1, closed)
        composeTestRule.onNodeWithText("Idle").assertExists()
    }

    @Test
    fun aFailureStaysUntilTheUserActsAndTryAgainRunsTheSameAttempt() {
        show()
        composeTestRule.runOnIdle { run.run { attempts++ } }
        answer(ok = false)

        composeTestRule.mainClock.advanceTimeBy(60_000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Failed").assertExists()
        assertEquals(0, closed)

        composeTestRule.runOnIdle { run.again() }
        composeTestRule.waitForIdle()
        assertEquals(2, attempts)
        composeTestRule.onNodeWithText("Saving").assertExists()

        answer(ok = true)
        composeTestRule.mainClock.advanceTimeBy(TV_SAVED_HOLD_MILLIS + 200)
        assertEquals(1, closed)
    }

    @Test
    fun anAnswerWithNothingRunningIsSomeoneElsesAndIsIgnored() {
        show()

        answer(ok = false)

        composeTestRule.onNodeWithText("Idle").assertExists()
    }

    @Test
    fun aStatusShownOnArrivalIsKeptForAFrame() {
        show(initial = SaveStatus.Failed)

        composeTestRule.onNodeWithText("Failed").assertExists()
    }

    @Test
    fun aFailureMayNameTheAttemptTryAgainRuns() {
        show()
        var blocked = 0
        composeTestRule.runOnIdle { run.run { attempts++ } }

        composeTestRule.runOnIdle { run.settle(failed = true, retry = { blocked++ }) }
        composeTestRule.runOnIdle { run.again() }

        assertEquals(1, attempts)
        assertEquals(1, blocked)
    }
}
