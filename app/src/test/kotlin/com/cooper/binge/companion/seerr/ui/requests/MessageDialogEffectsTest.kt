package com.cooper.binge.companion.seerr.ui.requests

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEvent
import com.cooper.binge.companion.seerr.ui.users.settings.EditorEventSnackbarEffect
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.binge.designsystem.R as DesR

/** A message the user must read to act is a dialog with one OK, not a snackbar that is cut off or goes away (#1848). */
@RunWith(RobolectricTestRunner::class)
class MessageDialogEffectsTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a half-done decline is a dialog with the next step, dismissed by OK`() {
        val events = MutableSharedFlow<ModerationEvent>(extraBufferCapacity = 1)
        rule.setContent { SeerrTheme { ModerationSnackbarEffect(events, remember { SnackbarHostState() }) } }
        rule.waitForIdle()

        events.tryEmit(ModerationEvent.DeclinedButBlockFailed)
        rule.waitForIdle()

        rule.onNodeWithText(context.getString(R.string.request_declined_block_failed_title)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.request_declined_block_failed_body)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(DesR.string.action_ok)).performClick()
        rule.onNodeWithText(context.getString(R.string.request_declined_block_failed_title)).assertDoesNotExist()
    }

    @Test
    fun `a rejected sign-in on a moderation is a dialog`() {
        val events = MutableSharedFlow<ModerationEvent>(extraBufferCapacity = 1)
        rule.setContent { SeerrTheme { ModerationSnackbarEffect(events, remember { SnackbarHostState() }) } }
        rule.waitForIdle()

        events.tryEmit(ModerationEvent.Failed(SeerrError.Unauthorized))
        rule.waitForIdle()

        rule.onNodeWithText(context.getString(R.string.state_error_unauthorized_title)).assertIsDisplayed()
    }

    @Test
    fun `a server reason of any length reads in full in a dialog`() {
        val events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 1)
        val reason = "Request quota exceeded for this user on this instance, ask an administrator to raise the limit"
        rule.setContent { SeerrTheme { EditorEventSnackbarEffect(events, remember { SnackbarHostState() }) } }
        rule.waitForIdle()

        events.tryEmit(EditorEvent.Failed(SeerrError.Rejected, detail = reason))
        rule.waitForIdle()

        rule.onNodeWithText(context.getString(R.string.state_error_rejected_detail, reason)).assertIsDisplayed()
    }

    @Test
    fun `a failure with nothing to act on has no dialog`() {
        val resources = context.resources
        assertNull(ModerationEvent.Failed(SeerrError.Server).dialogMessage(resources))
        assertNull(ModerationEvent.Declined.dialogMessage(resources))
        assertNull(ModerationEvent.MediaFilesDeletedRequestsKept(removedThisRequest = false).dialogMessage(resources))
    }
}
