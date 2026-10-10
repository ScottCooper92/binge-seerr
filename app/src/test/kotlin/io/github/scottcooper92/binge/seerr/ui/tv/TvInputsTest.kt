package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TABS = "tabs"
private const val AGREE = "Allow plain HTTP"

/**
 * The TV form's own inputs under a real D-pad (#1054): the checkbox row, and the choice rows that draw nothing with
 * nothing to offer. The text field's edit, Done and leave rules are the design system's, tested there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvInputsTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private val checks = mutableListOf<Boolean>()

    @Test
    fun okOnTheCheckboxRowFlipsIt() {
        setCheckbox(enabled = true)

        press(Key.DirectionCenter)

        assertEquals(listOf(true), checks)
    }

    /** A dimmed row keeps the remote's place: it still takes focus, and OK on it does nothing. */
    @Test
    fun aDisabledCheckboxRowKeepsFocusAndIgnoresOk() {
        setCheckbox(enabled = false)

        checkbox().assertIsFocused()
        press(Key.DirectionCenter)

        assertEquals(emptyList<Boolean>(), checks)
    }

    @Test
    fun choiceRowsWithNothingToOfferDrawNothing() {
        composeTestRule.setContent {
            BingeTvTheme {
                Column {
                    TvOptionGroup(title = "Root folder", choices = emptyList<Pair<Int, String>>(), selected = null, onSelect = {})
                    // The tabs' pill has a width and padding of its own, so an empty one still measures; only the guard gives zero.
                    Box(Modifier.testTag(TABS)) { TvTabs(choices = emptyList<Pair<Int, String>>(), selected = null, onSelect = {}) }
                }
            }
        }

        composeTestRule.onNodeWithText("Root folder").assertDoesNotExist()
        composeTestRule.onAllNodes(isFocusable()).assertCountEquals(0)
        composeTestRule.onNodeWithTag(TABS).assertWidthIsEqualTo(0.dp).assertHeightIsEqualTo(0.dp)
    }

    private fun setCheckbox(enabled: Boolean) {
        composeTestRule.setContent {
            BingeTvTheme { TvCheckboxRow(label = AGREE, checked = false, onCheckedChange = { checks += it }, enabled = enabled) }
        }
        composeTestRule.waitForIdle()
        checkbox().requestFocus()
        composeTestRule.waitForIdle()
    }

    // A disabled row is a bare focusable, which does not merge its label in, so the label may sit a level below it.
    private fun checkbox() = composeTestRule.onNode((hasText(AGREE) or hasAnyDescendant(hasText(AGREE))) and isFocusable())

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }
}
