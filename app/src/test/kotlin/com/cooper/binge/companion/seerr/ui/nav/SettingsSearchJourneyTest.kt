package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.ServerSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.SettingsSearchRoute
import com.cooper.binge.companion.seerr.ui.UserSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsPage
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
import com.binge.designsystem.R as DesR

private const val NARROW = "w400dp-h800dp"
private const val EMPTY_PAGE = """{"pageInfo":{"pages":0,"results":0},"results":[]}"""

/**
 * The settings search through the real [com.cooper.binge.companion.seerr.ui.SeerrNavHost], with the app's own entries and
 * ViewModels: the field on the hub opens it, a result opens its page above the search, and Back from there is the search as it was.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = NARROW)
class SettingsSearchJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    private fun backArrows() =
        host.compose
            .onAllNodesWithContentDescription(host.string(DesR.string.cd_navigate_back))
            .fetchSemanticsNodes()
            .size

    private fun openSearchFromHub(userPages: Boolean = false) {
        host.connect()
        host.seerr.serve("GET /api/v1/request", EMPTY_PAGE)
        host.seerr.serve("GET /api/v1/issue", EMPTY_PAGE)
        if (userPages) {
            host.seerr.serve("GET /api/v1/user/1", """{"id":1,"displayName":"Sam","permissions":2,"userType":1}""")
            host.seerr.serve("GET /api/v1/user/1/settings/main", """{"username":"Sam"}""")
        }
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_requests_desc))
        host.compose
            .onNodeWithText(host.string(com.cooper.homelab.core.uiandroid.R.string.search_title))
            .performScrollTo()
            .performClick()
        host.awaitShowing(host.string(com.cooper.homelab.core.uiandroid.R.string.search_prompt))
        assertEquals(SettingsSearchRoute, host.stack().last())
    }

    @Test
    fun `a result opens its page above the search, and Back from the page is the search as it was`() {
        openSearchFromHub()

        host.compose.onNode(hasSetTextAction()).performTextInput("logs")
        host.awaitShowing(host.string(R.string.server_settings_logs))
        host.compose.onNodeWithText(host.string(R.string.server_settings_logs)).performClick()
        host.compose.waitUntil("the Logs page on the back stack", 10_000) {
            host.stack().lastOrNull() == ServerSettingsPageRoute(ServerSettingsPage.Logs)
        }

        assertEquals(listOf(HubRoute, SettingsSearchRoute, ServerSettingsPageRoute(ServerSettingsPage.Logs)), host.stack())
        host.back()
        assertEquals(listOf(HubRoute, SettingsSearchRoute), host.stack())
        host.awaitShowing(host.string(R.string.server_settings_logs))
        assertTrue("the query is kept", host.isShowing("logs"))

        host.back()

        assertEquals(listOf(HubRoute), host.stack())
        assertTrue(host.isShowing(host.string(R.string.hub_section_requests_desc)))
    }

    @Test
    fun `a result in the viewer's own account opens their page, and Back is the search`() {
        openSearchFromHub(userPages = true)

        host.compose.onNode(hasSetTextAction()).performTextInput("language")
        host.awaitShowing(host.string(R.string.settings_display_language))
        // Two entries share the words: the server's general page, and the viewer's own account. Theirs is the second.
        host.compose.onAllNodesWithText(host.string(R.string.settings_display_language))[1].performClick()
        val own = UserSettingsPageRoute(1, UserSettingsPage.General, "account.language")
        host.compose.waitUntil("the viewer's own General page on the back stack", 10_000) { host.stack().lastOrNull() == own }

        assertEquals(listOf(HubRoute, SettingsSearchRoute, own), host.stack())
        host.awaitShowing(host.string(R.string.user_settings_display_name))
        assertEquals("the page has its Back", 1, backArrows())
        // The page opened scrolled to a row, and its bar must not have slid away with that scroll: Back is on screen at full size.
        host.compose
            .onNodeWithContentDescription(host.string(DesR.string.cd_navigate_back))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)

        host.back()

        assertEquals(listOf(HubRoute, SettingsSearchRoute), host.stack())
        host.awaitShowing(host.string(R.string.settings_display_language))
        assertTrue("the query is kept", host.isShowing("language"))
    }

    @Test
    fun `a result opened is remembered, and Disconnect forgets it`() {
        openSearchFromHub()
        host.compose.onNode(hasSetTextAction()).performTextInput("logs")
        host.awaitShowing(host.string(R.string.server_settings_logs))
        host.compose.onNodeWithText(host.string(R.string.server_settings_logs)).performClick()
        host.compose.waitUntil("the Logs page on the back stack", 10_000) {
            host.stack().lastOrNull() == ServerSettingsPageRoute(ServerSettingsPage.Logs)
        }

        // Remembering is written off the main thread, after the page has opened, so the list arrives a moment later.
        host.compose.waitUntil("the opened result remembered", 10_000) { host.recentSearches() == listOf("server.Logs|") }

        host.disconnect()

        assertEquals(emptyList<String>(), host.recentSearches())
    }

    @Test
    fun `Back from the search leaves it, and the hub is what is left`() {
        openSearchFromHub()

        host.back()

        assertEquals(listOf(HubRoute), host.stack())
        assertFalse(host.isShowing(host.string(com.cooper.homelab.core.uiandroid.R.string.search_prompt)))
    }
}
