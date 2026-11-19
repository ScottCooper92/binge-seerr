package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.IssueDetailRoute
import com.cooper.binge.companion.seerr.ui.IssuesRoute
import com.cooper.binge.companion.seerr.ui.TitleDetailRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A phone: one pane. */
private const val PHONE = "w400dp-h800dp"

/**
 * A title named on the issue page through the real nav host. With the discovery flag on, tapping it opens the title's own page in
 * this app. With it off nothing is pushed: the title is handed off, as it was before discovery. The demo server stands in for a real one.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class TitleOpenJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @After
    fun flagOff() {
        NavHostActivity.discoveryEnabled = false
    }

    private fun openIssueAndTapTitle() {
        host.connectDemo()
        host.launch(HomeRoute, IssuesRoute, IssueDetailRoute(1))
        host.awaitShowing(host.string(R.string.issue_comment_hint))
        // The title is also the page heading, which does nothing: the header row is the one that opens it.
        host.compose.onNode(hasText(ISSUE_TITLE) and hasClickAction()).performClick()
        host.compose.waitForIdle()
    }

    @Test
    fun `with discovery on the issue's title opens its own page`() {
        NavHostActivity.discoveryEnabled = true

        openIssueAndTapTitle()

        host.await("the title's page on the stack") { host.stack().any { it is TitleDetailRoute } }
    }

    @Test
    fun `with discovery off the issue's title pushes no page`() {
        NavHostActivity.discoveryEnabled = false

        openIssueAndTapTitle()

        assertTrue("the title is handed off, not opened here", host.stack().none { it is TitleDetailRoute })
        assertEquals("the issue page stays on top", IssueDetailRoute(1), host.stack().last())
    }

    private companion object {
        /** The demo server's first issue. */
        const val ISSUE_TITLE = "Metropolis"
    }
}
