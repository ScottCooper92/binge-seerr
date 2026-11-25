package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import com.cooper.binge.companion.seerr.ui.SeerrRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import com.cooper.binge.companion.seerr.ui.ThisAppRoute
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR
import com.cooper.binge.companion.seerr.feature.discover.R as DiscoverR

/** A phone: one pane, so the tab bar applies. */
private const val PHONE = "w400dp-h800dp"

/**
 * The Discovery setting through the real nav host: the remote flag is on, and the user flips the preference on This app's page, as
 * they would. The shell changes under the page and the page stays: its switch shows the new state, Back goes to its parent in the new
 * shell, and a recreate restores the same place. Off leaves Settings as the only tab. On brings the tabs back. A cold start of someone
 * who has opted out shows Settings and keeps the link's stack. The demo server stands in for a real one.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class DiscoveryToggleJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @After
    fun flagOff() {
        NavHostActivity.discoveryEnabled = false
    }

    private fun barIsShowing() = host.compose.onNodeWithTag("tab_settings").assertIsDisplayed()

    private fun barIsGone() = host.compose.onAllNodes(hasTestTag("tab_settings")).assertCountEquals(0)

    private fun discoveryRow() = host.compose.onNodeWithText(host.string(R.string.settings_discovery))

    private fun thisAppIsShowing() = host.isShowing(host.string(R.string.settings_discovery_caption))

    /** Opens This app's page above the shell, as the hub's tile does, and waits for it. */
    private fun openThisApp() {
        host.push(ThisAppRoute())
        host.awaitShowing(host.string(R.string.settings_discovery_caption))
    }

    /** Taps the Discovery row once, and waits until the shell under the page is the one the new setting asks for. */
    private fun toggleDiscovery(rootAfter: SeerrRoute) {
        discoveryRow().performClick()
        host.await("the root is $rootAfter") { host.stack().firstOrNull() == rootAfter }
    }

    private fun assertStillOnThisApp(rootAfter: SeerrRoute) {
        assertTrue("This app's page is still showing", thisAppIsShowing())
        assertTrue("the page is still on the stack above the new root: ${host.stack()}", host.stack() == listOf(rootAfter, ThisAppRoute()))
    }

    private fun startOnThisApp() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(DiscoverR.string.hubs_browse_all_movies))
        openThisApp()
    }

    @Test
    fun `turning discovery off keeps the user on This app's page, with the switch off`() {
        startOnThisApp()
        discoveryRow().assertIsOn()

        toggleDiscovery(rootAfter = HubRoute)

        assertStillOnThisApp(HubRoute)
        discoveryRow().assertIsOff()
        barIsGone()
    }

    @Test
    fun `turning it on again keeps the user on the page, with the switch on`() {
        startOnThisApp()
        toggleDiscovery(rootAfter = HubRoute)

        toggleDiscovery(rootAfter = ShellRoute)

        assertStillOnThisApp(ShellRoute)
        discoveryRow().assertIsOn()
    }

    @Test
    fun `Back from the page after turning it off goes to the hub`() {
        startOnThisApp()
        toggleDiscovery(rootAfter = HubRoute)

        host.back()

        host.await("the hub, with This app's page closed") { !thisAppIsShowing() && host.stack() == listOf(HubRoute) }
        barIsGone()
    }

    @Test
    fun `Back from the page after turning it on goes to the Settings tab`() {
        startOnThisApp()
        toggleDiscovery(rootAfter = HubRoute)
        toggleDiscovery(rootAfter = ShellRoute)

        host.back()

        host.await("the shell, with This app's page closed") { !thisAppIsShowing() && host.stack() == listOf(ShellRoute) }
        barIsShowing()
        assertTrue("Settings is the tab showing, so the account tab is selected", host.selectedTab() == SeerrTab.Settings)
    }

    @Test
    fun `a recreate after turning it off restores the page`() {
        startOnThisApp()
        toggleDiscovery(rootAfter = HubRoute)

        host.rotate(PHONE)

        host.awaitShowing(host.string(R.string.settings_discovery_caption))
        assertStillOnThisApp(HubRoute)
        discoveryRow().assertIsOff()
    }

    @Test
    fun `a recreate after turning it on restores the page`() {
        startOnThisApp()
        toggleDiscovery(rootAfter = HubRoute)
        toggleDiscovery(rootAfter = ShellRoute)

        host.rotate(PHONE)

        host.awaitShowing(host.string(R.string.settings_discovery_caption))
        assertStillOnThisApp(ShellRoute)
        discoveryRow().assertIsOn()
    }

    @Test
    fun `a cold start of someone who has opted out shows Settings and keeps the link's stack`() {
        startOnThisApp()
        toggleDiscovery(rootAfter = HubRoute)

        host.launch(HomeRoute, RequestsRoute)

        host.awaitShowing(host.string(AdminR.string.requests_filter_processing))
        barIsGone()
        assertTrue("the link's stack stays: ${host.stack()}", RequestsRoute in host.stack())
    }
}
