package io.github.scottcooper92.binge.seerr.ui.state

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A failed save stays on screen until it is answered, and a Retry that fails at once puts it back (#1077). */
@RunWith(RobolectricTestRunner::class)
class SaveFailedSnackbarTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var failed by mutableStateOf(false)
    private var retries = 0

    private fun show() =
        rule.setContent {
            val host = remember { SnackbarHostState() }
            SaveFailedSnackbar(failed, host, message = "Couldn't save", retryLabel = "Retry", onRetry = { retries++ })
            SnackbarHost(host)
        }

    @Test
    fun `a failed save is shown, and a retry that fails at once shows it again`() {
        show()
        rule.onNodeWithText("Couldn't save").assertDoesNotExist()

        failed = true
        rule.waitForIdle()
        rule.onNodeWithText("Couldn't save").assertExists()

        // The retry fails at once, so `failed` never leaves true and the effect is not restarted.
        rule.onNodeWithText("Retry").performClick()
        rule.waitForIdle()
        assertEquals(1, retries)
        rule.onNodeWithText("Couldn't save").assertExists()

        failed = false
        rule.waitForIdle()
        rule.onNodeWithText("Couldn't save").assertDoesNotExist()
    }
}
