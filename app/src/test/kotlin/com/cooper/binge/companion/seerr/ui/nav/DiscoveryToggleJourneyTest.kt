package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A phone: one pane, so the tab bar applies. */
private const val PHONE = "w400dp-h800dp"

/**
 * The Discovery setting through the real nav host: the remote flag is on, and the user flips the preference on This app's page, as
 * they would. Off leaves Settings as the only screen. On brings the tabs back on the start tab. A cold start of someone who has opted
 * out shows Settings and keeps the link's stack. The demo server stands in for a real one.
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

    /** From the Settings hub (the tab, or the only screen) to This app's page, then taps the Discovery row once. */
    private fun toggleDiscovery() {
        host.clickUntilShowing(host.string(R.string.hub_section_this_app), host.string(R.string.settings_discovery))
        host.compose.onNodeWithText(host.string(R.string.settings_discovery)).performClick()
        host.compose.waitForIdle()
    }

    @Test
    fun `turning discovery off from This app leaves Settings as the only screen`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
        host.openSettingsTab()
        host.awaitShowing(host.string(R.string.hub_section_this_app))

        toggleDiscovery()

        host.awaitShowing(host.string(R.string.hub_section_requests))
        barIsGone()
        assertTrue("nothing from discovery mode is left on the stack: ${host.stack()}", host.stack().size == 1)
    }

    @Test
    fun `turning it on again brings the tabs back on the start tab`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
        host.openSettingsTab()
        host.awaitShowing(host.string(R.string.hub_section_this_app))
        toggleDiscovery()
        host.awaitShowing(host.string(R.string.hub_section_requests))
        barIsGone()

        toggleDiscovery()

        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
        barIsShowing()
        assertTrue("the start tab, Movies, is showing and nothing is open over the shell: ${host.stack()}", host.stack().size == 1)
    }

    @Test
    fun `a cold start of someone who has opted out shows Settings and keeps the link's stack`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
        host.openSettingsTab()
        host.awaitShowing(host.string(R.string.hub_section_this_app))
        toggleDiscovery()
        host.awaitShowing(host.string(R.string.hub_section_requests))

        host.launch(HomeRoute, RequestsRoute)

        host.awaitShowing(host.string(R.string.requests_filter_processing))
        barIsGone()
        assertTrue("the link's stack stays: ${host.stack()}", RequestsRoute in host.stack())
    }
}
