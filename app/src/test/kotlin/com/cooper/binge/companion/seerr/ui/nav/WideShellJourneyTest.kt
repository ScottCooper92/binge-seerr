package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.discover.TitleKind
import com.cooper.binge.companion.seerr.discover.grid.GridPreset
import com.cooper.binge.companion.seerr.ui.DiscoverGridRoute
import com.cooper.binge.companion.seerr.ui.HomeRoute
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
import com.binge.designsystem.R as DesR

/** Two panes, under the landscape-tablet line: an unfolded foldable. */
private const val WIDE = "w900dp-h800dp"

/** A phone. */
private const val PHONE = "w400dp-h800dp"

private const val WAIT_MILLIS = 5_000L

/** A film the demo server knows. */
private const val DEMO_FILM_ID = 10378

/** A film the demo server lists on its Movies hub. */
private const val DEMO_FILM_NAME = "Spirited Away"

/**
 * The tab bar on a window wide enough for two panes, through the real nav host. A section stays on the app's stack at every
 * width: here it is drawn beside the hub inside the Settings tab, so the bar stays, and a fold moves no entry and loses no state.
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

    /**
     * The Pending filter chip, the one node with that text that can be selected: the request rows read the same. It is one
     * page from the first, so a tap crosses one page of the pager and not three.
     */
    private fun chip() =
        host.compose.onNode(
            hasText(host.string(R.string.requests_filter_pending), substring = true) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
        )

    /**
     * Waits for the chip itself. The hub beside it also says "Downloading now" and the rows say "Pending", so [RealNavHostRule.awaitShowing] can pass
     * before the section's chip row has composed, and a click made then lands on nothing.
     */
    private fun awaitChipReady() {
        host.compose.waitUntil(WAIT_MILLIS) {
            host.compose
                .onAllNodes(
                    hasText(host.string(R.string.requests_filter_pending), substring = true) and
                        SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected) and
                        isEnabled(),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        host.compose.waitForIdle()
    }

    /** Waits for the chip to read as selected: the filter comes back through restored state, a frame after the screen shows. */
    private fun awaitChipSelected() = host.await("the Pending chip selected", WAIT_MILLIS) { chipIsSelected() }

    private fun chipIsSelected() =
        host.compose
            .onAllNodes(hasText(host.string(R.string.requests_filter_pending), substring = true) and isSelected())
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun bar() = host.compose.onNodeWithTag("tab_settings")

    @Test
    fun `a section opens beside the hub under the tab bar`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_issues_desc))
        bar().assertIsDisplayed()

        host.compose
            .onNodeWithText(host.string(R.string.hub_section_issues_desc), substring = true)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf(ShellRoute, IssuesRoute) }

        bar().assertIsDisplayed()
        assertEquals("the hub is still on screen beside the section", true, host.isShowing(host.string(R.string.hub_section_requests_desc)))
    }

    @Test
    fun `a link opens its section beside the hub under the bar`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, RequestsRoute)
        host.awaitShowing(host.string(R.string.requests_filter_processing))

        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute, RequestsRoute) }
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

        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute, RequestsRoute) }
        bar().assertIsDisplayed()
        host.awaitShowing(host.string(R.string.hub_section_issues_desc))
    }

    @Test
    fun `a title opened from another tab covers the window while a section is open`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, RequestsRoute)
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute, RequestsRoute) }

        host.compose.onNodeWithTag("tab_search").performClick()
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute) }
        host.push(TitleDetailRoute(TitleKind.Movie, DEMO_FILM_ID))

        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf(ShellRoute, TitleDetailRoute(TitleKind.Movie, DEMO_FILM_ID)) }
        host.compose.onNodeWithTag("tab_settings").assertDoesNotExist()
        host.back()
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute) }
        bar().assertIsDisplayed()
    }

    @Test
    fun `Back on another tab returns to Settings with the section open`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, RequestsRoute)
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute, RequestsRoute) }
        host.compose.onNodeWithTag("tab_search").performClick()
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute) }

        host.back()
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute, RequestsRoute) }
        bar().assertIsDisplayed()
        host.awaitShowing(host.string(R.string.hub_section_issues_desc))
    }

    @Test
    fun `a grid opened from another tab is not hidden by an open section`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, RequestsRoute)
        host.awaitShowing(host.string(R.string.requests_filter_processing))

        host.compose.onNodeWithTag("tab_movies").performClick()
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute) }
        host.compose.onNodeWithText(host.string(R.string.hubs_browse_all_movies)).performClick()

        host.compose.waitUntil(WAIT_MILLIS) { host.stack().let { it.size == 2 && it[0] == ShellRoute && it[1] is DiscoverGridRoute } }
        host.awaitShowing(host.string(R.string.hubs_row_popular_movies))
    }

    @Test
    fun `a section left on another tab does not come back for the next server`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, RequestsRoute)
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute, RequestsRoute) }
        host.compose.onNodeWithTag("tab_search").performClick()
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute) }

        host.disconnect()
        host.compose.waitUntil(WAIT_MILLIS) {
            host.compose.runOnIdle {
                host.activity.tabs
                    .stack(SeerrTab.Settings)
                    .size
            } == 1
        }
        host.connectDemo()
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute) }
        host.compose.onNodeWithTag("tab_settings").performClick()
        host.compose.waitForIdle()

        assertEquals(listOf<NavKey>(ShellRoute), host.stack())
    }

    /** The round Back control over a page pushed above the shell: it covers the window, so no list beside it offers the way back. */
    private fun awaitBackControl() {
        host.compose.waitUntil(WAIT_MILLIS) {
            host.compose
                .onAllNodes(hasContentDescription(host.string(DesR.string.cd_navigate_back)))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun `a title page over the shell shows Back and Back pops it`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, TitleDetailRoute(TitleKind.Movie, DEMO_FILM_ID))
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf(ShellRoute, TitleDetailRoute(TitleKind.Movie, DEMO_FILM_ID)) }

        awaitBackControl()
        host.compose
            .onNodeWithContentDescription(host.string(DesR.string.cd_navigate_back))
            .assertIsDisplayed()
            .performClick()

        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute) }
        bar().assertIsDisplayed()
    }

    @Test
    fun `a title opened from the Movies hub shows Back and Back returns to the hub`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute)
        host.compose.waitUntil(WAIT_MILLIS) {
            host.compose
                .onAllNodes(hasTestTag("tab_movies"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        host.compose.onNodeWithTag("tab_movies").performClick()
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))

        host.compose.onAllNodes(hasText(DEMO_FILM_NAME, substring = true) and hasClickAction())[0].performClick()
        host.compose.waitUntil(WAIT_MILLIS) { host.stack().let { it.size == 2 && it[1] is TitleDetailRoute } }

        awaitBackControl()
        host.compose
            .onNodeWithContentDescription(host.string(DesR.string.cd_navigate_back))
            .assertIsDisplayed()
            .performClick()
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute) }
        host.awaitShowing(host.string(R.string.hubs_browse_all_movies))
    }

    @Test
    fun `a see all grid over the shell shows Back and Back pops it`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        val grid = DiscoverGridRoute(TitleKind.Movie, GridPreset.Popular, null)
        host.launch(HomeRoute, grid)
        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf(ShellRoute, grid) }

        awaitBackControl()
        host.compose
            .onNodeWithContentDescription(host.string(DesR.string.cd_navigate_back))
            .assertIsDisplayed()
            .performClick()

        host.compose.waitUntil(WAIT_MILLIS) { host.stack() == listOf<NavKey>(ShellRoute) }
    }

    @Test
    fun `a fold moves no entry and keeps the section's filter`() {
        NavHostActivity.discoveryEnabled = true
        host.connectDemo()
        host.launch(HomeRoute, RequestsRoute)
        awaitChipReady()
        host.clickUntil("the Pending chip selected after tapping it", WAIT_MILLIS, target = ::chip, reached = ::chipIsSelected)

        host.rotate(PHONE)
        host.awaitShowing(host.string(R.string.requests_filter_pending))
        assertEquals("nothing moved", listOf<NavKey>(ShellRoute, RequestsRoute), host.stack())
        awaitChipSelected()

        host.rotate(WIDE)
        host.awaitShowing(host.string(R.string.hub_section_issues_desc))
        assertEquals("nothing moved", listOf<NavKey>(ShellRoute, RequestsRoute), host.stack())
        awaitChipSelected()
        bar().assertIsDisplayed()
    }
}
