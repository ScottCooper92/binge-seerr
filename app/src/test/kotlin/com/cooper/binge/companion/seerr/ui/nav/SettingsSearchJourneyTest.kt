package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.ServerSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.SettingsSearchRoute
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val NARROW = "w400dp-h800dp"
private const val EMPTY_PAGE = """{"pageInfo":{"pages":0,"results":0},"results":[]}"""

/**
 * The settings search through the real [com.cooper.binge.companion.seerr.ui.SeerrNavHost], with the app's own entries and
 * ViewModels: the field on the hub opens it, a result opens its page above the hub, and Back from there is the hub, not the search.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = NARROW)
class SettingsSearchJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    private fun openSearchFromHub() {
        host.connect()
        host.seerr.serve("GET /api/v1/request", EMPTY_PAGE)
        host.seerr.serve("GET /api/v1/issue", EMPTY_PAGE)
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_requests_desc))
        host.compose
            .onNodeWithText(host.string(R.string.search_title))
            .performScrollTo()
            .performClick()
        host.awaitShowing(host.string(R.string.search_prompt))
        assertEquals(SettingsSearchRoute, host.stack().last())
    }

    @Test
    fun `a result opens its page above the hub, and Back from the page is the hub`() {
        openSearchFromHub()

        host.compose.onNode(hasSetTextAction()).performTextInput("logs")
        host.awaitShowing(host.string(R.string.server_settings_logs))
        host.compose.onNodeWithText(host.string(R.string.server_settings_logs)).performClick()
        host.compose.waitUntil("the Logs page on the back stack", 10_000) {
            host.stack().lastOrNull() == ServerSettingsPageRoute(ServerSettingsPage.Logs)
        }

        assertEquals(listOf(HubRoute, ServerSettingsPageRoute(ServerSettingsPage.Logs)), host.stack())
        host.back()
        assertEquals(listOf(HubRoute), host.stack())
        assertTrue(host.isShowing(host.string(R.string.hub_section_requests_desc)))
    }

    @Test
    fun `Back from the search leaves it, and the hub is what is left`() {
        openSearchFromHub()

        host.back()

        assertEquals(listOf(HubRoute), host.stack())
        assertFalse(host.isShowing(host.string(R.string.search_prompt)))
    }
}
