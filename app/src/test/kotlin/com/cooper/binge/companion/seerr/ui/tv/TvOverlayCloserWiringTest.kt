package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.ui.settings.SettingsUiState
import com.cooper.binge.companion.seerr.ui.tv.settings.TvSettingsActions
import com.cooper.binge.companion.seerr.ui.tv.settings.TvSettingsSheet
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val FIRST = "first-card"
private const val SECOND = "second-card"
private val BELOW_BAR = 240.dp

/**
 * **Settings and the issue sheet close through the shell's closer, and focus lands on what opened them.** Each sheet is
 * the real one, given the closer the shell hands it, in the real shell, and returns to the bar button that opened it. The issue
 * sheet is a top layer, and returns to the board cell the user left: that is `TvHomeShellHandOffTest`'s, on the real board.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvOverlayCloserWiringTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var sheet by mutableStateOf<TvDestination?>(null)

    private fun setShell() {
        composeTestRule.setContent {
            BingeTvTheme {
                TvHomeShell(
                    tab = TvDestination.Hub,
                    sheet = sheet,
                    onSelectTab = {},
                    onOpenSheet = { sheet = it },
                    onCloseSheet = { sheet = null },
                ) { shown, close, closer ->
                    when (shown) {
                        TvDestination.Settings ->
                            TvSettingsSheet(SettingsUiState.Loading, TvSettingsActions(), onDismissRequest = close, closer = closer)
                        else ->
                            // Below the bar, so a move Up has the bar's items above it to find.
                            Column(Modifier.padding(top = BELOW_BAR)) {
                                Focusable(FIRST)
                                Focusable(SECOND)
                            }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun opener(destination: TvDestination) =
        composeTestRule.onNode(hasContentDescription(string(destination.labelRes)) and isFocusable())

    @Test
    fun `the settings sheet closes through the closer and focus returns to the gear`() {
        setShell()
        composeTestRule.onNodeWithTag(FIRST).requestFocus()
        composeTestRule.press(Key.DirectionUp)
        repeat(TvDestination.entries.size) { composeTestRule.press(Key.DirectionRight) }
        opener(TvDestination.Settings).assertIsFocused()

        composeTestRule.press(Key.Enter)
        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()

        assertNull(sheet)
        opener(TvDestination.Settings).assertIsFocused()
    }
}

@Composable
private fun Focusable(tag: String) {
    val requester = remember { FocusRequester() }
    Box(
        Modifier
            .size(48.dp)
            .testTag(tag)
            .focusRequester(requester)
            .focusable(),
    )
}
