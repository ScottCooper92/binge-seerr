package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A phone: one pane, so the tab bar applies. */
private const val PHONE = "w400dp-h800dp"

private const val WAIT_MILLIS = 5_000L

/**
 * The tab bar through the real nav host: the shell is the root, what a tab opens covers it, and Back brings it back. The
 * demo server stands in for a real one.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class DiscoveryShellJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @After
    fun flagOff() {
        NavHostActivity.discoveryEnabled = false
    }

    private fun bar() = host.compose.onNodeWithTag("tab_settings")

    private fun barIsGone() = host.compose.onAllNodes(hasTestTag("tab_settings")).assertCountEquals(0)

    @Test
    fun `with the flag on the hub opens under the tab bar and a section covers it until Back`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_requests))
        bar().assertIsDisplayed()
        assertEquals(listOf<NavKey>(ShellRoute), host.stack())

        host.compose
            .onNodeWithText(host.string(R.string.hub_section_requests_desc), substring = true)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf(ShellRoute, RequestsRoute) }
        barIsGone()

        host.back()
        host.awaitShowing(host.string(R.string.hub_section_requests))
        bar().assertIsDisplayed()
        assertEquals(listOf<NavKey>(ShellRoute), host.stack())
        assertFalse("Back from a section stays in the app", host.activity.isFinishing)
        host.back()
        assertTrue("Back from the shell leaves the app", host.activity.isFinishing)
    }

    @Test
    fun `a link opened over the shell lands in Settings and Back walks the link to the tab bar`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, RequestsRoute)
        host.awaitShowing(host.string(R.string.requests_filter_processing))
        assertEquals(listOf(ShellRoute, RequestsRoute), host.stack())
        barIsGone()

        host.back()
        host.awaitShowing(host.string(R.string.hub_section_requests))
        bar().assertIsDisplayed()
    }

    @Test
    fun `back from another tab returns to the start tab and only back from that leaves the app`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_requests))

        host.compose.onNodeWithTag("tab_search").performClick()
        host.awaitShowing(host.string(R.string.title_search_placeholder))
        assertEquals(listOf<NavKey>(ShellRoute), host.stack())

        host.back()
        host.awaitShowing(host.string(R.string.hub_section_requests))
        assertFalse("Back from another tab stays in the app", host.activity.isFinishing)
        bar().assertIsDisplayed()

        host.back()
        assertTrue("Back from the start tab leaves the app", host.activity.isFinishing)
    }

    @Test
    fun `with the flag off the hub is the root and there is no bar`() {
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_requests))

        barIsGone()
        assertEquals(listOf<NavKey>(HubRoute), host.stack())
    }
}
