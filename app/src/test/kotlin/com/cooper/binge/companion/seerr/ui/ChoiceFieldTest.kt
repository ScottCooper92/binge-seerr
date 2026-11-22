package com.cooper.binge.companion.seerr.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TITLE = "Quality profile"
private val CHOICES = (1..5).map { it to "Profile $it" }

@RunWith(RobolectricTestRunner::class)
class ChoiceFieldTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    @Test
    fun `the field names the current choice and nothing else`() {
        setField(selected = 3)

        composeTestRule.onNodeWithText(TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText("Profile 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Profile 1").assertDoesNotExist()
    }

    @Test
    fun `an unset choice reads as unknown rather than blank`() {
        setField(selected = null)

        composeTestRule.onNodeWithText(string(R.string.settings_value_unknown)).assertIsDisplayed()
    }

    @Test
    fun `tapping an enabled field reports the tap`() {
        var taps = 0
        setField(selected = 3, onClick = { taps++ })

        composeTestRule.onNodeWithText("Profile 3").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `a disabled field does not report a tap`() {
        var taps = 0
        setField(selected = 3, enabled = false, onClick = { taps++ })

        composeTestRule.onNodeWithText("Profile 3").performClick()

        assertEquals(0, taps)
    }

    private fun setField(
        selected: Int?,
        enabled: Boolean = true,
        onClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            BingeExpressiveTheme {
                ChoiceField(
                    icon = Icons.Filled.HighQuality,
                    title = TITLE,
                    choices = CHOICES,
                    selected = selected,
                    enabled = enabled,
                    onClick = onClick,
                )
            }
        }
    }
}
