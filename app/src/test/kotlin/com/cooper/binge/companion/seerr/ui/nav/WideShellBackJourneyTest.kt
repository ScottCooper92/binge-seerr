package com.cooper.binge.companion.seerr.ui.nav

import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.ui.DvrInstanceRoute
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.IssueDetailRoute
import com.cooper.binge.companion.seerr.ui.IssuesRoute
import com.cooper.binge.companion.seerr.ui.RequestDetailRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import com.cooper.binge.companion.seerr.ui.ServerSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.SettingsRoute
import com.cooper.binge.companion.seerr.ui.SettingsSearchRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import com.cooper.binge.companion.seerr.ui.settings.ServiceType
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
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

/** A wide window: the hub beside one content pane. */
private const val TWO_PANES = "w900dp-h800dp"

/** An expanded window: a landscape tablet. It is the same two panes as any wide window (#2348). */
private const val EXPANDED = "w1280dp-h800dp"
private const val WAIT_MILLIS = 5_000L

/**
 * Back on a wide window, with the Settings tab selected (#2346). What is open beside the hub closes first, innermost first, then the
 * open section goes back to the default, and only from the bare hub does Back leave for the start tab. The tab shell used to take
 * every Back while Settings was selected, so the open request, search or page was skipped and the app went to Movies.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = TWO_PANES)
class WideShellBackJourneyTest {
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
        awaitStack(listOf(ShellRoute, *above))
        assertEquals("the Settings tab is selected", SeerrTab.Settings, tab())
    }

    private fun tab(): SeerrTab = host.compose.runOnIdle { host.activity.tabs.selected }

    private fun awaitStack(expected: List<NavKey>) =
        host.await("the app stack to be $expected", WAIT_MILLIS, null) { host.stack() == expected }

    private fun backTo(vararg expected: NavKey) {
        host.back()
        awaitStack(expected.toList())
        assertEquals("Back closed what was open and stayed on Settings", SeerrTab.Settings, tab())
    }

    private fun backToStartTab() {
        host.back()
        host.await("the start tab to show", WAIT_MILLIS, null) { tab() == SeerrTab.Movies }
    }

    /** Requests is the default section, so with nothing open in it the hub is bare and Back leaves for the start tab. */
    private fun requestDetail() {
        open(RequestsRoute, RequestDetailRoute(1))

        backTo(ShellRoute, RequestsRoute)
        backToStartTab()
    }

    /** Another section: once what it opened is closed, Back returns the default section, and only then the start tab. */
    private fun issueDetail(bare: List<NavKey>) {
        open(IssuesRoute, IssueDetailRoute(1))

        backTo(ShellRoute, IssuesRoute)
        backTo(*bare.toTypedArray())
        backToStartTab()
    }

    private fun search(bare: List<NavKey>) {
        open(IssuesRoute, SettingsSearchRoute)

        backTo(ShellRoute, IssuesRoute)
        backTo(*bare.toTypedArray())
        backToStartTab()
    }

    private fun nestedPage(bare: List<NavKey>) {
        val page = ServerSettingsPageRoute(ServerSettingsPage.Services)
        open(SettingsRoute, page, DvrInstanceRoute(ServiceType.Radarr, null))

        backTo(ShellRoute, SettingsRoute, page)
        backTo(ShellRoute, SettingsRoute)
        backTo(*bare.toTypedArray())
        backToStartTab()
    }

    @Test
    fun `two panes - Back closes the open request, then leaves for the start tab`() = requestDetail()

    @Test
    @Config(qualifiers = EXPANDED)
    fun `expanded - Back closes the open request, then leaves for the start tab`() = requestDetail()

    @Test
    fun `two panes - Back closes an item, then the section, then leaves for the start tab`() = issueDetail(listOf(ShellRoute))

    @Test
    @Config(qualifiers = EXPANDED)
    fun `expanded - Back closes an item, then the section, then leaves for the start tab`() = issueDetail(listOf(ShellRoute))

    @Test
    fun `two panes - Back closes the settings search and brings the section back`() = search(listOf(ShellRoute))

    @Test
    @Config(qualifiers = EXPANDED)
    fun `expanded - Back closes the settings search and brings the section back`() = search(listOf(ShellRoute))

    @Test
    fun `two panes - Back unwinds a page two levels deep one level at a time`() = nestedPage(listOf(ShellRoute))

    @Test
    @Config(qualifiers = EXPANDED)
    fun `expanded - Back unwinds a page two levels deep one level at a time`() = nestedPage(listOf(ShellRoute))
}
