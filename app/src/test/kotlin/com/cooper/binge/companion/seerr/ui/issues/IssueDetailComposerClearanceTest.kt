package com.cooper.binge.companion.seerr.ui.issues

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.cooper.binge.companion.seerr.theme.SeerrTheme
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The thread ends above the floating comment card, so its last comment can scroll fully clear of the card and its fade. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h640dp-xhdpi")
class IssueDetailComposerClearanceTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun longThread(): IssueDetail {
        val base = commonIssueDetail()
        val replies =
            (1..REPLIES).map { index ->
                base.comments.first().copy(id = FIRST_REPLY_ID + index, message = "Reply number $index")
            }
        return base.copy(comments = replies)
    }

    private fun assertLastReplyClearsComposer() {
        rule.setContent {
            SeerrTheme {
                IssueDetailScreen(
                    state = IssueDetailUiState.Ready(detail = longThread()),
                    events = emptyFlow(),
                    actions = noIssueDetailActions(),
                )
            }
        }
        val last = rule.onNodeWithText("Reply number $REPLIES")
        // To the end of the thread: scrolling the reply into view would stop at the screen's edge, which is under the card.
        repeat(SWIPES) {
            rule.onRoot().performTouchInput { swipe(Offset(centerX, height * SWIPE_START), Offset(centerX, 0f)) }
        }
        rule.waitForIdle()

        val lastBottom = last.getBoundsInRoot().bottom
        val composerTop = rule.onNode(hasSetTextAction()).getBoundsInRoot().top
        assertTrue("The last reply ends at $lastBottom, under the composer that starts at $composerTop", lastBottom <= composerTop)
    }

    @Test
    fun `the last reply scrolls fully above the composer`() {
        assertLastReplyClearsComposer()
    }

    @Test
    @Config(qualifiers = "w411dp-h640dp-xhdpi", fontScale = 2f)
    fun `it still does at a font scale of 2`() {
        assertLastReplyClearsComposer()
    }

    private companion object {
        const val REPLIES = 12
        const val SWIPES = 10
        const val SWIPE_START = 0.4f
        const val FIRST_REPLY_ID = 200
    }
}
