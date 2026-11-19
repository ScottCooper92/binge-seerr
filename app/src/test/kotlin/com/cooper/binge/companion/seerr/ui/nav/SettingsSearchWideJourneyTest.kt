package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.IssuesRoute
import com.cooper.binge.companion.seerr.ui.ServerSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.SettingsSearchRoute
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.binge.designsystem.R as DesR

/** Two panes: wide enough for the hub beside a section, under the landscape-tablet line. */
private const val WIDE = "w900dp-h800dp"
private const val EMPTY_PAGE = """{"pageInfo":{"pages":0,"results":0},"results":[]}"""

/**
 * The settings search on a window with the hub beside the detail pane, through the real nav host (#2343): the search takes the
 * content pane, a result opens its page above it in the same pane with its own Back arrow, the hub stays beside both, the search
 * stays on the stack, and Back returns to the search with its query. A second Back is what was open before the search.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = WIDE)
class SettingsSearchWideJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    private val logs = ServerSettingsPageRoute(ServerSettingsPage.Logs)

    private fun backArrows() =
        host.compose
            .onAllNodesWithContentDescription(host.string(DesR.string.cd_navigate_back))
            .fetchSemanticsNodes()
            .size

    @Test
    fun `a result opens above the search, and Back returns to the search with its query, then to the open section`() {
        host.connect()
        host.seerr.serve("GET /api/v1/request", EMPTY_PAGE)
        host.seerr.serve("GET /api/v1/issue", EMPTY_PAGE)
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_requests_desc))
        host.compose
            .onNodeWithText(host.string(R.string.hub_section_issues_desc), substring = true)
            .performScrollTo()
            .performClick()
        host.awaitShowing(host.string(R.string.issues_filter_resolved))
        assertEquals(listOf(HubRoute, IssuesRoute), host.stack())

        val searchTitle = host.string(com.cooper.homelab.core.uiandroid.R.string.search_title)
        host.compose
            .onNode(hasScrollAction().and(hasAnyDescendant(hasText(searchTitle))))
            .performTouchInput { swipeDown(startY = centerY, endY = bottom) }
        host.compose.onNodeWithText(searchTitle).performClick()
        host.awaitShowing(host.string(com.cooper.homelab.core.uiandroid.R.string.search_prompt))
        assertTrue("the hub stays beside the search", host.isShowing(host.string(R.string.hub_section_requests_desc)))
        host.compose.onNode(hasSetTextAction()).performTextInput("logs")
        host.awaitShowing(host.string(R.string.server_settings_logs))
        host.compose.onNodeWithText(host.string(R.string.server_settings_logs)).performClick()
        host.compose.waitUntil("the Logs page on the back stack", 10_000) { host.stack().lastOrNull() == logs }

        assertEquals("the search stays under the page", listOf(HubRoute, IssuesRoute, SettingsSearchRoute, logs), host.stack())
        assertEquals("the page has its own Back, to the search", 1, backArrows())
        assertTrue("the hub stays beside the page", host.isShowing(host.string(R.string.hub_section_issues_desc)))

        host.back()

        assertEquals("Back is the search, beside the hub, with its query", listOf(HubRoute, IssuesRoute, SettingsSearchRoute), host.stack())
        host.awaitShowing(host.string(R.string.server_settings_logs))
        assertTrue("the hub stays beside the search", host.isShowing(host.string(R.string.hub_section_issues_desc)))
        assertTrue("the query is kept", host.isShowing("logs"))

        host.back()

        assertEquals("Back from the search is what was open", listOf(HubRoute, IssuesRoute), host.stack())
        host.awaitShowing(host.string(R.string.issues_filter_resolved))
    }
}
