package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.IssueDetailRoute
import com.cooper.binge.companion.seerr.ui.IssuesRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A phone: one pane. */
private const val PHONE = "w400dp-h800dp"

private const val COMMENT = "Checked on the TV as well, same drift."

/**
 * The issue page's floating composer through the real nav host: a comment is typed in the card and sent, and the issue is resolved
 * from the card's chip, and no sheet opens for either. The demo server stands in for a real one.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class IssueComposerJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @Test
    fun `a comment is sent and the issue resolved from the card without opening a sheet`() {
        host.connectDemo()
        host.launch(HomeRoute, IssuesRoute, IssueDetailRoute(1))
        host.awaitShowing(host.string(R.string.issue_comment_hint))
        val resolve = host.string(R.string.issue_action_resolve)
        host.await("the issue's Resolve chip") { host.isShowing(resolve, exactly = true) }

        host.compose.onNode(hasSetTextAction()).performTextInput(COMMENT)
        host.compose.onNodeWithContentDescription(host.string(R.string.issue_comment_send)).performClick()
        host.awaitShowing(COMMENT)

        // The chip asks first, as it always has; the confirmation is a dialog, the only window the page opens.
        host.clickUntilShowing(resolve, host.string(R.string.issue_resolve_confirm_title))
        host.compose.onAllNodesWithText(resolve).onLast().performClick()
        host.await("the Reopen chip once the issue is resolved") {
            host.isShowing(host.string(R.string.issue_action_reopen), exactly = true)
        }
        assertTrue("the thread keeps the comment", host.isShowing(COMMENT))
    }
}
