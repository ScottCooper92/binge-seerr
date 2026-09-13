package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.binge.designsystem.tv.nav.LocalTvContentInset
import com.binge.designsystem.tv.theme.BingeTvTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PAST_SETTLE_MILLIS = CONTENT_SETTLE_MILLIS + 100L

/**
 * The rail beside the boards under a real D-pad: where focus starts, how it reaches the rail and comes
 * back, that a walk down the rail changes the content only once it settles, and the Back hierarchy.
 * Every destination is a full-bleed focusable box, the one property of a board the shell reacts to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-television-xhdpi", application = android.app.Application::class)
class TvShellScaffoldFocusTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val railItem = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Test
    fun focusStartsInTheContentNotTheRail() {
        setShell()

        content(TvDestination.Hub).assertIsFocused()
    }

    @Test
    fun leftFromTheContentLandsOnTheSelectedRailItem() {
        setShell()

        pressLeft()

        railItemAt(0).assertIsFocused()
    }

    @Test
    fun walkingDownTheRailSelectsAndTheContentFollowsOnceSettled() {
        setShell()
        pressLeft()

        pressDown()

        railItemAt(1).assertIsFocused()
        composeTestRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)
        composeTestRule.waitForIdle()
        content(TvDestination.Requests).assertExists()
    }

    @Test
    fun backWithFocusInTheContentMovesItToTheRail() {
        setShell()

        pressBack()

        railItemAt(0).assertIsFocused()
    }

    @Test
    fun backOnTheRailOffHomeGoesHome() {
        setShell()
        pressLeft()
        pressDown()
        composeTestRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)
        composeTestRule.waitForIdle()
        content(TvDestination.Requests).assertExists()

        pressBack()
        composeTestRule.mainClock.advanceTimeBy(PAST_SETTLE_MILLIS)
        composeTestRule.waitForIdle()

        content(TvDestination.Hub).assertExists()
    }

    private fun setShell() {
        composeTestRule.setContent {
            BingeTvTheme {
                var selected by remember { mutableStateOf(TvDestination.Hub) }
                TvShellScaffold(selected = selected, onSelect = { selected = it }) { destination ->
                    // Cleared of the rail as every board is, so ← has the rail to land on.
                    Box(modifier = Modifier.fillMaxSize().padding(start = LocalTvContentInset.current)) {
                        Box(modifier = Modifier.fillMaxSize().testTag(tag(destination)).focusable())
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun tag(destination: TvDestination) = "content-${destination.key}"

    private fun content(destination: TvDestination) = composeTestRule.onNodeWithTag(tag(destination))

    private fun railItemAt(index: Int) = composeTestRule.onAllNodes(railItem)[index]

    private fun pressLeft() = press(Key.DirectionLeft)

    private fun pressDown() = press(Key.DirectionDown)

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }
}
