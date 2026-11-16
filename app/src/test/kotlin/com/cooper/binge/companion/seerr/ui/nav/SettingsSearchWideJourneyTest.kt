package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.IssuesRoute
import com.cooper.binge.companion.seerr.ui.ServerSettingsPageRoute
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
 * The settings search on a window with the hub beside the detail pane, through the real nav host (#1985): the search covers the
 * whole window, a result opens in the right pane in place of what was open, it has no Back arrow, and Back is the hub.
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
    fun `a result replaces the open section beside the hub, has no arrow, and Back shows the default section`() {
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

        host.compose
            .onNodeWithText(host.string(R.string.search_title))
            .performScrollTo()
            .performClick()
        host.awaitShowing(host.string(R.string.search_prompt))
        assertTrue("the search covers the whole window, hub included", !host.isShowing(host.string(R.string.hub_section_requests_desc)))
        host.compose.onNode(hasSetTextAction()).performTextInput("logs")
        host.awaitShowing(host.string(R.string.server_settings_logs))
        host.compose.onNodeWithText(host.string(R.string.server_settings_logs)).performClick()
        host.compose.waitUntil("the Logs page on the back stack", 10_000) { host.stack().lastOrNull() == logs }

        assertEquals("the open section is replaced, not stacked under", listOf(HubRoute, logs), host.stack())
        assertTrue("the hub is beside the page", host.isShowing(host.string(R.string.hub_section_issues_desc)))
        assertEquals("no Back arrow beside the hub", 0, backArrows())

        host.back()

        assertEquals(listOf(HubRoute), host.stack())
        host.awaitShowing(host.string(R.string.requests_filter_processing))
    }
}
