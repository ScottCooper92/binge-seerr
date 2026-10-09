package io.github.scottcooper92.binge.seerr.ui.nav

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.HomeRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val WIDE = "w1000dp-h800dp"
private const val NARROW = "w400dp-h800dp"
private const val EMPTY_PAGE = """{"pageInfo":{"pages":0,"results":0},"results":[]}"""

/** How long the server holds a re-check before dropping it: long enough to watch many frames of it. */
private const val RECHECK_MILLIS = 500L
private const val RECHECK_TIMEOUT_MILLIS = 15_000L
private const val FRAME_PAUSE_MILLIS = 5L

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
