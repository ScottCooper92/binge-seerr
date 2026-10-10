package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.binge.designsystem.tv.theme.BingeTvTheme
import io.github.scottcooper92.binge.seerr.util.createSeerrKeyboardComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ADDRESS = "Server address"
private const val NAME = "Display name"
private const val EXAMPLE = "http://192.168.1.10:5055"
private const val AGREE = "Allow plain HTTP"

/**
 * The TV form's own inputs under a real D-pad (#1054): the text field's edit, Done and leave rules, the checkbox row,
 * and the choice rows that draw nothing with nothing to offer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvInputsTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardComposeRule()

    private var done = 0
    private val checks = mutableListOf<Boolean>()

    @Test
    fun okOnTheFrameStartsEditingAndDoneHandsFocusBackBeforeGoingOn() {
        setFields()
        frame(ADDRESS).requestFocus()
        composeTestRule.waitForIdle()

        press(Key.DirectionCenter)
        input(ADDRESS).assertIsFocused()
        input(ADDRESS).performImeAction()
        composeTestRule.waitForIdle()

        frame(ADDRESS).assertIsFocused()
        assertEquals(1, done)
    }

    /** One line has no up or down to move the cursor to, so the remote leaves the field rather than being stuck in it. */
    @Test
    fun downWhileEditingLeavesTheFieldForTheOneBelow() {
        setFields()
        frame(ADDRESS).requestFocus()
        composeTestRule.waitForIdle()
        press(Key.DirectionCenter)
        input(ADDRESS).assertIsFocused()

        press(Key.DirectionDown)

        input(ADDRESS).assertIsNotFocused()
        frame(NAME).assertIsFocused()
    }

    @Test
    fun thePlaceholderShowsOnlyWhileTheFieldIsEmpty() {
        var value by mutableStateOf("")
        composeTestRule.setContent {
            BingeTvTheme { TvTextField(value = value, onValueChange = { value = it }, label = ADDRESS, placeholder = EXAMPLE) }
        }
        composeTestRule.onNodeWithText(EXAMPLE).assertExists()

        value = "http://seerr.lan"
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(EXAMPLE).assertDoesNotExist()
    }

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
                    TvTabs(choices = emptyList<Pair<Int, String>>(), selected = null, onSelect = {})
                }
            }
        }

        composeTestRule.onNodeWithText("Root folder").assertDoesNotExist()
        composeTestRule.onAllNodes(isFocusable()).assertCountEquals(0)
    }

    private fun setFields() {
        composeTestRule.setContent {
            BingeTvTheme {
                Column {
                    Field(ADDRESS, onDone = { done++ })
                    Field(NAME)
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Composable
    private fun Field(
        label: String,
        onDone: (() -> Unit)? = null,
    ) {
        var value by remember { mutableStateOf("") }
        TvTextField(value = value, onValueChange = { value = it }, label = label, onDone = onDone)
    }

    private fun setCheckbox(enabled: Boolean) {
        composeTestRule.setContent {
            BingeTvTheme { TvCheckboxRow(label = AGREE, checked = false, onCheckedChange = { checks += it }, enabled = enabled) }
        }
        composeTestRule.waitForIdle()
        checkbox().requestFocus()
        composeTestRule.waitForIdle()
    }

    /** The frame names the field and cannot take text; the input inside it can. */
    private fun frame(label: String) = composeTestRule.onNode(hasContentDescription(label) and isFocusable() and !hasSetTextAction())

    private fun input(label: String) = composeTestRule.onNode(hasContentDescription(label) and hasSetTextAction())

    // A disabled row is a bare focusable, which does not merge its label in, so the label may sit a level below it.
    private fun checkbox() = composeTestRule.onNode((hasText(AGREE) or hasAnyDescendant(hasText(AGREE))) and isFocusable())

    private fun press(key: Key) {
        composeTestRule.onRoot().performKeyInput { pressKey(key) }
        composeTestRule.waitForIdle()
    }
}
