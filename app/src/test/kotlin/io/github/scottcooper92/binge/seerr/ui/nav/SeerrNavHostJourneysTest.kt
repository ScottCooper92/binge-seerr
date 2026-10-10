package io.github.scottcooper92.binge.seerr.ui.nav

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.HomeRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.binge.designsystem.R as DesR

/** Two panes: wide enough for the hub beside a section, under the landscape-tablet line. */
private const val WIDE = "w900dp-h800dp"

/** A landscape tablet, where the hub, a section and what it opened sit side by side (#1110). */
private const val TABLET = "w1280dp-h800dp"
private const val NARROW = "w400dp-h800dp"
private const val EMPTY_PAGE = """{"pageInfo":{"pages":0,"results":0},"results":[]}"""
private const val ANA = "Ana"
private const val BO = "Bo"
private const val USERS_PAGE =
    """{"pageInfo":{"pages":1,"results":2},"results":[{"id":8,"displayName":"Ana","permissions":32},
       {"id":9,"displayName":"Bo","permissions":32}]}"""

/** How long the server holds a re-check before dropping it: long enough to watch many frames of it. */
private const val RECHECK_MILLIS = 500L
private const val RECHECK_TIMEOUT_MILLIS = 15_000L
private const val FRAME_PAUSE_MILLIS = 5L
private const val AWAIT_MILLIS = 10_000L

/**
 * Journeys through the real [io.github.scottcooper92.binge.seerr.ui.SeerrNavHost], with the app's own
 * entries, ViewModels and stores, where the pane tests use stand-ins. These are the ones that have
 * broken before: a rotation that lost the screen beside the hub (#815), and a retry that flashed the
 * dashboard (#873, #983).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = WIDE)
class SeerrNavHostJourneysTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    private val hub get() = host.string(R.string.hub_section_requests_desc)

    private fun connectToLists() {
        host.connect()
        host.seerr.serve("GET /api/v1/request", EMPTY_PAGE)
        host.seerr.serve("GET /api/v1/issue", EMPTY_PAGE)
    }

    /**
     * Opens a section from the hub on a wide window, then rotates to a narrow one and back. [row] is the
     * section's description on the hub, and [screen] a label only that section's screen shows.
     */
    private fun assertRotationKeeps(
        row: String,
        screen: String,
    ) {
        connectToLists()
        host.launch(HomeRoute)
        host.awaitShowing(hub)

        host.compose
            .onNodeWithText(row, substring = true)
            .performScrollTo()
            .performClick()
        host.awaitShowing(screen)
        assertTrue("the hub left when the section opened beside it", host.isShowing(hub))

        host.rotate(NARROW)
        host.awaitShowing(screen)
        assertFalse("one pane showed the hub, not the section that was open", host.isShowing(hub))

        host.rotate(WIDE)
        host.awaitShowing(screen)
        assertTrue("the hub did not come back beside the section", host.isShowing(hub))
    }

    /** Requests is the section a wide window shows by default, which is what made it the one lost (#815). */
    @Test
    fun `a rotation keeps Requests open beside the hub, and the pair comes back`() =
        assertRotationKeeps(row = hub, screen = host.string(R.string.requests_filter_processing))

    @Test
    fun `a rotation keeps Issues open beside the hub, and the pair comes back`() =
        assertRotationKeeps(
            row = host.string(R.string.hub_section_issues_desc),
            screen = host.string(R.string.issues_filter_resolved),
        )

    /**
     * The three panes with the app's own entries: the default section is beside the hub from the start, what a section
     * opens takes the third pane in place of what was there, and Back closes it rather than walking back through
     * every item opened (#1110).
     */
    @Test
    @Config(qualifiers = TABLET)
    fun `on a landscape tablet, opening from a list replaces the open item, and Back closes it`() {
        connectToUsers()
        host.launch(HomeRoute)
        host.awaitShowing(hub)
        host.awaitShowing(host.string(R.string.requests_filter_processing))
        assertTrue("the third pane did not say nothing was open", host.isShowing(nothingOpen))

        host.compose
            .onNodeWithText(host.string(R.string.hub_section_users_desc), substring = true)
            .performScrollTo()
            .performClick()
        host.awaitShowing(ANA)
        openUser(ANA)
        assertTrue("the list left the screen", host.isShowing(BO))
        assertNoBackArrow()

        openUser(BO)
        assertEquals("Ana was left on screen under Bo", 1, host.count(ANA))
        assertNoBackArrow()

        host.back()
        host.awaitShowing(nothingOpen)
        assertEquals("Back went to Ana rather than closing Bo", 1, host.count(ANA))
        assertEquals("Bo's page stayed after Back", 1, host.count(BO))
        assertTrue("the hub left the screen", host.isShowing(hub))
    }

    /** A rotation to two panes shows the hub beside the open item, and back to three restores the list between them. */
    @Test
    @Config(qualifiers = TABLET)
    fun `a rotation from three panes to two and back keeps the open item`() {
        connectToUsers()
        host.launch(HomeRoute)
        host.awaitShowing(hub)
        host.compose
            .onNodeWithText(host.string(R.string.hub_section_users_desc), substring = true)
            .performScrollTo()
            .performClick()
        host.awaitShowing(BO)
        openUser(ANA)

        host.rotate(WIDE)
        host.compose.waitUntil("Ana alone beside the hub", AWAIT_MILLIS) { host.isShowing(ANA) && !host.isShowing(BO) }
        assertTrue("two panes lost the hub", host.isShowing(hub))

        host.rotate(TABLET)
        host.compose.waitUntil("the list back between the hub and Ana", AWAIT_MILLIS) { host.isShowing(BO) && host.count(ANA) > 1 }
    }

    private val nothingOpen get() = host.string(R.string.three_pane_nothing_open_title)

    /** The lists, and two users whose pages read in full, so a user's page shows their name. */
    private fun connectToUsers() {
        connectToLists()
        host.seerr.serve("GET /api/v1/user", USERS_PAGE)
        listOf(8 to ANA, 9 to BO).forEach { (id, name) ->
            host.seerr.serve("GET /api/v1/user/$id", """{"id":$id,"displayName":"$name","permissions":32,"userType":3}""")
            host.seerr.serve("GET /api/v1/user/$id/quota", """{"movie":{"days":7,"limit":0},"tv":{"days":7,"limit":0}}""")
            host.seerr.serve("GET /api/v1/user/$id/watch_data", """{"playCount":0,"recentlyWatched":[]}""")
            host.seerr.serve("GET /api/v1/user/$id/watchlist", """{"page":1,"totalPages":1,"totalResults":0,"results":[]}""")
            host.seerr.serve("GET /api/v1/user/$id/requests", EMPTY_PAGE)
        }
    }

    /** Taps [name]'s row in the list, the first node reading it, and waits for their page in the third pane. */
    private fun openUser(name: String) {
        host.compose.onAllNodesWithText(name, substring = true)[0].performClick()
        host.compose.waitUntil("$name's page in the third pane", AWAIT_MILLIS) { !host.isShowing(nothingOpen) && host.count(name) > 1 }
    }

    /** Neither the section nor what it opened offers Back, since neither leaves the screen. */
    private fun assertNoBackArrow() =
        assertTrue(
            "a Back arrow in the three panes",
            host.compose
                .onAllNodesWithContentDescription(host.string(DesR.string.cd_navigate_back))
                .fetchSemanticsNodes()
                .isEmpty(),
        )

    /**
     * A cold start over a hub the app remembers, with the server gone: the problem page. Retry re-checks,
     * and the server holds that re-check for [RECHECK_MILLIS] before dropping it (#873).
     */
    @Test
    @Config(qualifiers = NARROW)
    fun `a retry holds the problem page, busy, and never shows the dashboard while it runs`() {
        host.connect()
        host.launch(HomeRoute)
        host.awaitShowing(hub)
        host.seerr.unreachable()
        host.launch(HomeRoute)

        assertRetryHolds(problem = host.string(R.string.hub_unreachable_headline)) {
            host.seerr.unreachable(afterMillis = RECHECK_MILLIS)
        }
    }

    /**
     * The server answers, but the dashboard can't load: `auth/me` answers with nothing the app can read. The
     * re-check's probe passes, and the reload re-emits the remembered overview before it fails again, held here for
     * [RECHECK_MILLIS]. Neither is an answer, so the problem page stays (#983).
     */
    @Test
    @Config(qualifiers = NARROW)
    fun `a retry from could-not-load holds the problem page while the reload runs`() {
        host.connect()
        host.launch(HomeRoute)
        host.awaitShowing(hub)
        host.seerr.serve("GET /api/v1/auth/me", "")
        host.launch(HomeRoute)

        assertRetryHolds(problem = host.string(R.string.hub_couldnt_load_headline)) {
            host.seerr.serveFrom("GET /api/v1/auth/me", delayMillis = RECHECK_MILLIS) { "" }
        }
    }

    /**
     * From the problem page naming [problem], [slow] makes the next re-check take a while, then Retry is tapped. On a
     * narrow window, where the hub is alone: beside it, the section's own Retry button and spinner would answer too.
     * Every frame from the tap until the page offers Retry again is checked: each one shows the problem page and
     * not the dashboard, and some show the button busy.
     */
    private fun assertRetryHolds(
        problem: String,
        slow: () -> Unit,
    ) {
        val retry = host.string(R.string.hub_retry)
        host.awaitShowing(problem)
        host.awaitShowing(retry)
        slow()
        // The button's label exactly: the could-not-load body says it is "retrying automatically".

        host.compose.mainClock.autoAdvance = false
        host.compose.onNodeWithText(retry).performClick()
        var sawBusy = false
        val deadline = System.currentTimeMillis() + RECHECK_TIMEOUT_MILLIS
        while (!(sawBusy && host.isShowing(retry, exactly = true))) {
            check(System.currentTimeMillis() < deadline) { "the re-check never finished (busy seen: $sawBusy)" }
            host.compose.mainClock.advanceTimeByFrame()
            assertFalse("the dashboard showed while the re-check ran", host.isShowing(hub))
            assertTrue("the problem page left while the re-check ran", host.isShowing(problem))
            sawBusy = sawBusy || (!host.isShowing(retry, exactly = true) && isSpinning())
            Thread.sleep(FRAME_PAUSE_MILLIS)
        }
    }

    private fun isSpinning() =
        host.compose
            .onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .fetchSemanticsNodes()
            .isNotEmpty()
}
