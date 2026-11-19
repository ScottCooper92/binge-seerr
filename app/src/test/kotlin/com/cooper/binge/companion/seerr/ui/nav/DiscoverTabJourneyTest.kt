package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A tablet-width window: a smallest width of 600dp or more. */
private const val TABLET = "w900dp-h800dp"

/** A phone. */
private const val PHONE = "w400dp-h800dp"

/**
 * The Discover tab through the real nav host: it is in the bar on a tablet-width window only, it hosts the grid with no Back
 * arrow, and a window that stops being that wide leaves it for the start tab.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = TABLET)
class DiscoverTabJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @After
    fun flagOff() {
        NavHostActivity.discoveryEnabled = false
    }

    @Test
    fun `the Discover tab hosts the grid under the bar`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_row_recently_added))

        host.compose.onNodeWithTag("tab_discover").performClick()

        host.awaitShowing(host.string(R.string.grid_filters_button))
        host.compose.onNodeWithTag("tab_discover").assertIsDisplayed()
        assertEquals("the tab opens nothing on the app's stack", listOf<NavKey>(ShellRoute), host.stack())
    }

    @Test
    fun `a window that is no longer tablet width takes the tab out and lands on the start tab`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hubs_row_recently_added))
        host.compose.onNodeWithTag("tab_discover").performClick()
        host.awaitShowing(host.string(R.string.grid_filters_button))

        host.rotate(PHONE)

        host.compose.onAllNodes(hasTestTag("tab_discover")).assertCountEquals(0)
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
    }
}
