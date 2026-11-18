package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.IssuesRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import com.cooper.binge.companion.seerr.ui.TitleDetailRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Two panes, under the landscape-tablet line: an unfolded foldable. */
private const val WIDE = "w900dp-h800dp"

/** A phone. */
private const val PHONE = "w400dp-h800dp"

private const val WAIT_MILLIS = 5_000L

/** A film the demo server knows. */
private const val DEMO_FILM_ID = 10378

/**
 * The tab bar on a window wide enough for two panes, through the real nav host. Settings keeps its hub beside the section inside
 * the tab, so the bar stays while a section is open, and a fold carries the open section across.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = WIDE)
class WideShellJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @After
    fun flagOff() {
        NavHostActivity.discoveryEnabled = false
    }

    private fun bar() = host.compose.onNodeWithTag("tab_settings")

    @Test
    fun `a section opens beside the hub under the tab bar and sits on the Settings stack`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_issues_desc))
        bar().assertIsDisplayed()

        host.compose
            .onNodeWithText(host.string(R.string.hub_section_issues_desc), substring = true)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        host.compose.waitUntil(WAIT_MILLIS) { host.settingsStack() == listOf(HubRoute, IssuesRoute) }

        assertEquals("the app's stack is only the shell", listOf<NavKey>(ShellRoute), host.stack())
        bar().assertIsDisplayed()
        assertEquals("the hub is still on screen beside the section", true, host.isShowing(host.string(R.string.hub_section_requests_desc)))
    }

    @Test
    fun `a link opens its section beside the hub under the bar`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, RequestsRoute)
        host.compose.waitUntil(WAIT_MILLIS) { host.settingsStack() == listOf(HubRoute, RequestsRoute) }

        assertEquals(listOf<NavKey>(ShellRoute), host.stack())
        bar().assertIsDisplayed()
        host.awaitShowing(host.string(R.string.hub_section_issues_desc))
    }

    @Test
    fun `a link over a stack of the same size still lands beside the hub`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, TitleDetailRoute(TitleKind.Movie, DEMO_FILM_ID))
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf(ShellRoute, TitleDetailRoute(TitleKind.Movie, DEMO_FILM_ID)) }

        host.openLink(HomeRoute, RequestsRoute)

        host.compose.waitUntil(WAIT_MILLIS) { host.settingsStack() == listOf(HubRoute, RequestsRoute) }
        assertEquals(listOf<NavKey>(ShellRoute), host.stack())
        bar().assertIsDisplayed()
    }

    @Test
    fun `a section left on Settings by a fold on another tab comes across when Settings is selected`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, RequestsRoute)
        host.compose.waitUntil(WAIT_MILLIS) { host.settingsStack() == listOf(HubRoute, RequestsRoute) }
        host.compose.onNodeWithTag("tab_tv").performClick()
        host.compose.waitForIdle()

        host.rotate(PHONE)
        assertEquals("nothing moves while another tab shows", listOf<NavKey>(HubRoute, RequestsRoute), host.settingsStack())
        host.compose.onNodeWithTag("tab_settings").performClick()

        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf(ShellRoute, RequestsRoute) }
        assertEquals(listOf<NavKey>(HubRoute), host.settingsStack())
    }

    @Test
    fun `folding carries the open section from the Settings tab to a whole window and back`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, RequestsRoute)
        host.compose.waitUntil(WAIT_MILLIS) { host.settingsStack() == listOf(HubRoute, RequestsRoute) }

        host.rotate(PHONE)
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf(ShellRoute, RequestsRoute) }
        assertEquals(listOf<NavKey>(HubRoute), host.settingsStack())

        host.rotate(WIDE)
        host.compose.waitUntil(WAIT_MILLIS) { host.settingsStack() == listOf(HubRoute, RequestsRoute) }
        assertEquals(listOf<NavKey>(ShellRoute), host.stack())
        bar().assertIsDisplayed()
    }
}
