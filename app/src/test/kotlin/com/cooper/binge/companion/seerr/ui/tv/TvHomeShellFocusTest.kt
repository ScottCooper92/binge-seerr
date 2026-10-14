package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val TOP_ROW_CLEARANCE = 120.dp

/**
 * Home's top row under a real D-pad: where focus starts, how it reaches the buttons, that a button opens its page
 * above Home and Back closes it with focus back on the button, and that Issues is only offered when the account can list
 * them. Every destination is a focusable box below the row, the one property of a board the shell reacts to.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvHomeShellFocusTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    @Test
    fun focusStartsInHomeNotTheTopRow() {
        setShell()

        content(TvDestination.Hub).assertIsFocused()
    }

    @Test
    fun upFromHomeReachesATopRowButton() {
        setShell()

        walkToSettingsButton()

        button(R.string.hub_section_settings).assertIsFocused()
    }

    @Test
    fun aButtonOpensItsPageAndBackReturnsFocusToIt() {
        setShell()
        walkToSettingsButton()

        press(Key.Enter)
        content(TvDestination.Settings).assertIsFocused()
        pressBack()

        button(R.string.hub_section_settings).assertIsFocused()
    }

    /** An account the server will not list issues for gets no Issues button (#1054). */
    @Test
    fun withoutIssuesTheTopRowHasNoIssuesButton() {
        setShell(showIssues = false)

        composeTestRule.onAllNodes(hasContentDescription(string(R.string.hub_section_issues))).assertCountEquals(0)
    }

    /** Up from Home lands on whichever button is nearest; the row's end is Settings, so walk right to it. */
    private fun walkToSettingsButton() {
        press(Key.DirectionUp)
        repeat(TvDestination.entries.size) { press(Key.DirectionRight) }
    }

    private fun setShell(showIssues: Boolean = true) {
        composeTestRule.setContent {
            BingeTvTheme {
                var page by rememberSaveable { mutableStateOf(TvDestination.Hub) }
                TvHomeShell(
                    page = page,
                    onOpenPage = { page = it },
                    onClosePage = { page = TvDestination.Hub },
                    showIssues = showIssues,
                ) { destination, _ ->
                    Box(modifier = Modifier.fillMaxSize().padding(top = TOP_ROW_CLEARANCE)) {
                        val claim = remember { FocusRequester() }
                        // A board hosted as an overlay claims first focus itself; this stand-in does the same.
                        LaunchedEffect(Unit) { claim.requestFocus() }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .testTag(tag(destination))
                                    .focusRequester(claim)
                                    .focusable(),
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun tag(destination: TvDestination) = "content-${destination.key}"

    private fun content(destination: TvDestination) = composeTestRule.onNodeWithTag(tag(destination))

    private fun button(labelRes: Int) = composeTestRule.onNode(hasContentDescription(string(labelRes)))

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }

    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }
}
