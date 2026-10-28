package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.SetupActions
import com.cooper.binge.companion.seerr.ui.SetupUiState
import com.cooper.binge.companion.seerr.util.createSeerrKeyboardAndroidComposeRule
import com.cooper.binge.companion.seerr.util.string
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The typed address page runs the actions the entry passed in (#1718). A rebuilt copy once dropped Try the demo (#1716),
 * so each button here is pressed through [TvSetupScreen] and must reach the entry's own action.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w960dp-h540dp-television-xhdpi")
class TvSetupAddressPageTest {
    @get:Rule
    val composeTestRule = createSeerrKeyboardAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()

    private val actions =
        SetupActions(
            onEditAddress = { calls += "edit" },
            onInspect = { calls += "inspect" },
            onChangeServer = {},
            onEditForm = {},
            onConnect = {},
            onPlexLaunched = {},
            onCancelLink = {},
            onRequestPasswordReset = {},
            onStartHandOff = { calls += "handOff" },
            onTryDemo = { calls += "demo" },
        )

    @Test
    fun tryTheDemoRunsTheEntrysDemo() {
        setPage()

        // The demo is a tab, and its page has the one button that starts it: the same words, so the button is the second.
        composeTestRule.onAllNodesWithText(string(R.string.setup_try_demo))[0].performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText(string(R.string.setup_try_demo))[1].performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("demo"), calls)
    }

    @Test
    fun continueRunsTheEntrysInspect() {
        setPage()

        button(R.string.setup_continue).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("inspect"), calls)
    }

    @Test
    fun theCodeTabStartsTheEntrysHandOffAndTheAddressTabLeavesIt() {
        setPage(offerHandOff = true)

        // The code tab asks for a code as it appears, and has no address field.
        assertEquals("handOff", calls.first())
        button(R.string.setup_continue).assertDoesNotExist()

        composeTestRule.onNodeWithText(string(R.string.tv_setup_type_address)).performClick()
        composeTestRule.waitForIdle()

        button(R.string.setup_continue).assertExists()
    }

    private fun setPage(offerHandOff: Boolean = false) {
        val state = SetupUiState.Address("http://seerr.lan:5055", insecure = false, isInspecting = false, error = null, canTryDemo = true)
        composeTestRule.setContent { BingeTvTheme { TvSetupScreen(state = state, actions = actions, offerHandOff = offerHandOff) } }
        composeTestRule.waitForIdle()
    }

    private fun button(label: Int) =
        composeTestRule.onNode((hasText(string(label)) or hasAnyDescendant(hasText(string(label)))) and isFocusable())
}
