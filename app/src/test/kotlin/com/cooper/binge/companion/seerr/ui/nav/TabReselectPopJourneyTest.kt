package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.IssueDetailRoute
import com.cooper.binge.companion.seerr.ui.IssuesRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import com.cooper.binge.companion.seerr.ui.TitleDetailRoute
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A wide window: the hub beside one content pane, so the bar stays up while something is open. */
private const val TWO_PANES = "w900dp-h800dp"
private const val WAIT_MILLIS = 5_000L

/** The film of request 1 in the demo: a request opens as a sheet over its title's page, so this is where an open request lives. */
private const val REQUEST_FILM_ID = 45745

/**
 * Tapping the tab that is already showing, with screens opened on top of its root (#2356). On a phone what a tab opens takes the
 * whole window and hides the bar, so a reselect only happens at a root there; on a wide window the bar stays up beside the pane.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = TWO_PANES)
class TabReselectPopJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @After
    fun flagOff() {
        NavHostActivity.discoveryEnabled = false
    }

    private fun open(vararg above: NavKey) {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, *above)
        host.await("the app stack to be open", WAIT_MILLIS, null) { host.stack() == listOf(ShellRoute, *above) }
        assertEquals(SeerrTab.Settings, host.compose.runOnIdle { host.activity.tabs.selected })
    }

    private fun reselectSettings() {
        host.compose
            .onNodeWithTag(SeerrTab.Settings.testTag)
            .assertIsDisplayed()
            .performClick()
    }

    @Test
    fun `reselecting the tab closes an open request and goes back to the hub`() {
        open(RequestsRoute, TitleDetailRoute(TitleKind.Movie, REQUEST_FILM_ID, openRequest = 1))

        reselectSettings()

        host.await("the app stack to be back at the hub", WAIT_MILLIS, null) { host.stack() == listOf(ShellRoute) }
    }

    @Test
    fun `reselecting the tab closes an open section and its item`() {
        open(IssuesRoute, IssueDetailRoute(1))

        reselectSettings()

        host.await("the app stack to be back at the hub", WAIT_MILLIS, null) { host.stack() == listOf(ShellRoute) }
    }
}
