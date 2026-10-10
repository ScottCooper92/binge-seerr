package io.github.scottcooper92.binge.seerr.ui

import android.content.Context
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The address is the step's only field, so the keyboard's Done does what Continue does. */
@RunWith(RobolectricTestRunner::class)
class SetupAddressImeTest {
    @get:Rule
    val composeTestRule = createSeerrComposeRule()

    private val label get() = ApplicationProvider.getApplicationContext<Context>().getString(R.string.setup_server_url)

    private fun doneWith(serverUrl: String): Int {
        var inspected = 0
        composeTestRule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                SetupScreen(
                    state = SetupUiState.Address(serverUrl = serverUrl, insecure = false, isInspecting = false, error = null),
                    actions = SetupActions({}, { inspected++ }, {}, {}, {}, {}, {}, {}),
                )
            }
        }
        composeTestRule.onNodeWithText(label).performImeAction()
        composeTestRule.waitForIdle()
        return inspected
    }

    @Test
    fun `Done on an address starts the inspect`() {
        assertEquals(1, doneWith("seerr.example.com"))
    }

    @Test
    fun `Done on an empty address does nothing, as Continue is off`() {
        assertEquals(0, doneWith(""))
    }
}
