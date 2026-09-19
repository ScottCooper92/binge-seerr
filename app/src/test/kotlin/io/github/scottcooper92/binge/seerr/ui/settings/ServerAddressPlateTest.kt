package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The plate that lets a user read their server's address across to a television instead of typing
 * it there from memory (#323) — asserting the address itself renders, which the screenshot suite
 * cannot: a baseline confirms the frame looks the same, not that the right value reached it.
 */
@RunWith(RobolectricTestRunner::class)
class ServerAddressPlateTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `renders the connected server's address`() {
        rule.setContent {
            ServerAddressPlate(baseUrl = "http://seerr.lan:5055")
        }

        rule.onNodeWithText("http://seerr.lan:5055").assertExists()
    }
}
