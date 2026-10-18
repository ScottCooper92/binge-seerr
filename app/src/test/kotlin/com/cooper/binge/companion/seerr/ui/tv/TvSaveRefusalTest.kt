package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.SaveStatus
import com.binge.designsystem.tv.component.TvSaveStatus
import com.binge.designsystem.tv.theme.BingeTvTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TvSaveRefusalTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun resolved(
        refusal: SeerrError?,
        status: SaveStatus = SaveStatus.Saved,
    ): SaveStatus {
        var result: SaveStatus = SaveStatus.Idle
        composeTestRule.setContent {
            BingeTvTheme {
                result = tvSaveStatus(status, refusal)
                TvSaveStatus(status = result, onRetry = {})
            }
        }
        composeTestRule.waitForIdle()
        return result
    }

    @Test
    fun `a refusal takes over the line with the reason the phone's snackbar uses, and offers no Retry`() {
        val reason = ApplicationProvider.getApplicationContext<android.app.Application>().getString(R.string.state_error_rejected_message)

        val status = resolved(SeerrError.Rejected)

        assertEquals(SaveStatus.Refused(reason), status)
        composeTestRule.onNodeWithText(reason).assertExists()
        composeTestRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun `with no refusal the line is the status it was`() {
        assertEquals(SaveStatus.Saved, resolved(refusal = null))
    }
}
