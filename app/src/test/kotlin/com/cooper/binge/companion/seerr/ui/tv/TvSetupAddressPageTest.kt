package com.cooper.binge.companion.seerr.ui.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithContentDescription
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

        button(R.string.setup_try_demo).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("demo"), calls)
    }

    @Test
    fun continueRunsTheEntrysInspect() {
        setPage()

        composeTestRule.onNodeWithContentDescription(string(R.string.setup_continue)).performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("inspect"), calls)
    }

    @Test
    fun scanAQrStartsTheEntrysHandOffAndLeavesTheForm() {
        setPage()

        button(R.string.tv_setup_send_from_phone).performClick()
        composeTestRule.waitForIdle()

        // The code page it opens asks for a code of its own as well, so the first call is the button's.
        assertEquals("handOff", calls.first())
        composeTestRule.onNodeWithContentDescription(string(R.string.setup_continue)).assertDoesNotExist()
    }

    private fun setPage() {
        val state = SetupUiState.Address("http://seerr.lan:5055", insecure = false, isInspecting = false, error = null, canTryDemo = true)
        composeTestRule.setContent { BingeTvTheme { TvSetupScreen(state = state, actions = actions) } }
        composeTestRule.waitForIdle()
    }

    private fun button(label: Int) =
        composeTestRule.onNode((hasText(string(label)) or hasAnyDescendant(hasText(string(label)))) and isFocusable())
}
