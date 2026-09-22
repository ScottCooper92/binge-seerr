package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TITLE = "Quality profile"
private val CHOICES = (1..5).map { it to "Profile $it" }

/**
 * The row+sheet shape #336 asks for: the row names one choice and stays one line regardless of how
 * many [CHOICES] carries; the sheet, opened on tap, is where the rest live. The last two cover the
 * constraint [ChoicePicker]'s KDoc carried over from #172 — a pick made mid-save must not land, and
 * an already-open sheet must stop taking picks the moment a save starts.
 */
@RunWith(RobolectricTestRunner::class)
class ChoiceRowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val unknown get() =
        ApplicationProvider.getApplicationContext<android.content.Context>().getString(
            R.string.settings_value_unknown,
        )

    @Test
    fun `the row names the current choice and nothing else`() {
        setRow(selected = 3)

        composeTestRule.onNodeWithText(TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText("Profile 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Profile 1").assertDoesNotExist()
    }

    @Test
    fun `an unset choice reads as unknown rather than blank`() {
        setRow(selected = null)

        composeTestRule.onNodeWithText(unknown).assertIsDisplayed()
    }

    @Test
    fun `tapping the row opens a sheet with every choice`() {
        setRow(selected = 3)

        composeTestRule.onNodeWithText("Profile 3").performClick()

        // The row underneath still names "Profile 3", so that one choice shows up twice; the rest
        // only exist inside the sheet the tap opened.
        CHOICES.filter { it.second != "Profile 3" }.forEach { (_, label) -> composeTestRule.onNodeWithText(label).assertIsDisplayed() }
        composeTestRule.onAllNodesWithText("Profile 3").assertCountEquals(2)
    }

    @Test
    fun `picking a choice in the sheet reports it and dismisses`() {
        val picked = mutableListOf<Int>()
        setRow(selected = 3, onSelect = { picked += it })

        composeTestRule.onNodeWithText("Profile 3").performClick()
        composeTestRule.onNodeWithText("Profile 1").performClick()

        assertEquals(listOf(1), picked)
        composeTestRule.onNodeWithText("Profile 2").assertDoesNotExist()
    }

    @Test
    fun `a disabled row cannot be tapped open`() {
        val picked = mutableListOf<Int>()
        setRow(selected = 3, enabled = false, onSelect = { picked += it })

        composeTestRule.onNodeWithText("Profile 3").performClick()

        composeTestRule.onNodeWithText("Profile 1").assertDoesNotExist()
        assertTrue(picked.isEmpty())
    }

    @Test
    fun `an open sheet closes the instant a save in flight disables the row`() {
        val picked = mutableListOf<Int>()
        composeTestRule.setContent {
            BingeExpressiveTheme {
                var enabled by remember { mutableStateOf(true) }
                Column {
                    Button(onClick = { enabled = false }) { Text("start saving") }
                    ChoiceRow(title = TITLE, choices = CHOICES, selected = 3, onSelect = { picked += it }, enabled = enabled)
                }
            }
        }
        composeTestRule.onNodeWithText("Profile 3").performClick()
        composeTestRule.onNodeWithText("Profile 1").assertIsDisplayed()

        composeTestRule.onNodeWithText("start saving").performClick()

        composeTestRule.onNodeWithText("Profile 1").assertDoesNotExist()
    }

    private fun setRow(
        selected: Int?,
        enabled: Boolean = true,
        onSelect: (Int) -> Unit = {},
    ) {
        composeTestRule.setContent {
            BingeExpressiveTheme {
                ChoiceRow(title = TITLE, choices = CHOICES, selected = selected, onSelect = onSelect, enabled = enabled)
            }
        }
    }
}
