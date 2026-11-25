package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.IssuesRoute
import com.cooper.binge.companion.seerr.ui.SettingsSearchRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

/** An expanded window: a landscape tablet. It is the same two panes as any wide window (#2348). */
private const val TABLET = "w1280dp-h800dp"
private const val EMPTY_PAGE = """{"pageInfo":{"pages":0,"results":0},"results":[]}"""

/**
 * The settings search on a landscape tablet, through the real nav host (#2343): with a section open it takes the one content pane
 * beside the hub, as any section does, and the section it covered is back when the search closes.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = TABLET)
class SettingsSearchTabletJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @Test
    fun `the search takes the content pane beside the hub, and Back brings the section back`() {
        host.connect()
        host.seerr.serve("GET /api/v1/request", EMPTY_PAGE)
        host.seerr.serve("GET /api/v1/issue", EMPTY_PAGE)
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_requests_desc))
        host.compose
            .onNodeWithText(host.string(R.string.hub_section_issues_desc), substring = true)
            .performScrollTo()
            .performClick()
        host.awaitShowing(host.string(AdminR.string.issues_filter_resolved))
        assertEquals(listOf(HubRoute, IssuesRoute), host.stack())

        val searchTitle = host.string(com.cooper.homelab.core.uiandroid.R.string.search_title)
        host.compose.onNodeWithText(searchTitle).performClick()
        host.awaitShowing(host.string(com.cooper.homelab.core.uiandroid.R.string.search_prompt))

        assertEquals(listOf(HubRoute, IssuesRoute, SettingsSearchRoute), host.stack())
        assertTrue("the hub stays beside the search", host.isShowing(host.string(R.string.hub_section_requests_desc)))
        assertTrue("the section it covered is not drawn", !host.isShowing(host.string(AdminR.string.issues_filter_resolved)))

        host.back()

        assertEquals("Back is the section that was open", listOf(HubRoute, IssuesRoute), host.stack())
        host.awaitShowing(host.string(AdminR.string.issues_filter_resolved))
        assertTrue("the hub is still beside it", host.isShowing(host.string(R.string.hub_section_requests_desc)))
    }
}
