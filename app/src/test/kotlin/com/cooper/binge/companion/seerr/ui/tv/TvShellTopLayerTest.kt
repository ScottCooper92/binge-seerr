package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.binge.designsystem.tv.nav.LocalTvTopInset
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val CARD = "card"
private const val LOWER_CARD = "lower-card"
private const val GRID_CELL = "grid-cell"
private const val PAGE = "page"

/**
 * A title's own page sits over everything, the bar included. It reads no top inset while the pages beneath read the bar's,
 * it keeps focus inside itself, and when it closes focus goes back to what opened it: a card on the board, or a cell of the
 * see-all page it was opened from.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvShellTopLayerTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private var pageOpen by mutableStateOf(false)
    private var seeAllOpen by mutableStateOf(false)
    private var boardInset = Dp.Unspecified
    private var pageInset = Dp.Unspecified

    private fun setShell() {
        composeTestRule.setContent {
            BingeTvTheme {
                TvHomeShell(
                    tab = TvDestination.Hub,
                    sheet = null,
                    onSelectTab = {},
                    onOpenSheet = {},
                    onCloseSheet = {},
                    overlay = if (seeAllOpen) ({ Focusable(GRID_CELL, focusOnArrival = true) }) else null,
                    topLayer =
                        if (pageOpen) {
                            {
                                pageInset = LocalTvTopInset.current
                                BackHandler { pageOpen = false }
                                // Below the bar, so a move Up has the bar's items above it to find.
                                Box(Modifier.padding(top = 240.dp)) { Focusable(PAGE, focusOnArrival = true) }
                            }
                        } else {
                            null
                        },
                ) { shown, _ ->
                    if (shown == TvDestination.Hub) {
                        boardInset = LocalTvTopInset.current
                        Column {
                            Focusable(CARD)
                            // Below the page's own item, so a move Down out of the page has somewhere beneath it to land.
                            Box(Modifier.padding(top = 360.dp)) { Focusable(LOWER_CARD) }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `the page over the bar reads no top inset while the board beneath reads the bar's`() {
        setShell()
        pageOpen = true
        composeTestRule.waitForIdle()

        assertEquals(0.dp, pageInset)
        assertTrue("the board reads no inset under the bar", boardInset > 0.dp)
    }

    @Test
    fun `the page keeps focus, so no move out of it reaches the board or the bar beneath`() {
        setShell()
        pageOpen = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PAGE).assertIsFocused()

        listOf(Key.DirectionDown, Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight).forEach { key ->
            composeTestRule.press(key)
            composeTestRule.onNodeWithTag(PAGE).assertIsFocused()
        }
    }

    @Test
    fun `Back closes the page and focus returns to the card that opened it`() {
        setShell()
        composeTestRule.onNodeWithTag(CARD).requestFocus()
        pageOpen = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PAGE).assertIsFocused()

        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()

        composeTestRule.onNodeWithTag(CARD).assertIsFocused()
    }

    /** Opened from a see-all page, closing returns to that page, not to the board under it. */
    @Test
    fun `a page opened over a see-all page hands focus back to the see-all page`() {
        setShell()
        seeAllOpen = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(GRID_CELL).assertIsFocused()
        pageOpen = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PAGE).assertIsFocused()

        composeTestRule.pressBack()
        composeTestRule.settleShellHandOff()

        composeTestRule.onNodeWithTag(GRID_CELL).assertIsFocused()
    }
}

@Composable
private fun Focusable(
    tag: String,
    focusOnArrival: Boolean = false,
) {
    val requester = remember { FocusRequester() }
    Box(
        Modifier
            .size(48.dp)
            .testTag(tag)
            .focusRequester(requester)
            .focusable(),
    )
    if (focusOnArrival) LaunchedEffect(Unit) { requester.requestFocus() }
}
