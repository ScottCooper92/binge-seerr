package com.cooper.binge.companion.seerr.ui.nav

import android.os.Looper
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.ui.EditConnectionRoute
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.RequestDetailRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import com.cooper.binge.companion.seerr.ui.SettingsRoute
import com.cooper.binge.companion.seerr.ui.users.settings.ADMIN
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val PHONE = "w400dp-h800dp"
private const val TABLET = "w1280dp-h800dp-land"
private const val EMPTY_PAGE = """{"pageInfo":{"pages":0,"results":0},"results":[]}"""
private const val SESSION = "connect.sid=s3ss10n; Path=/"
private const val AWAIT_MILLIS = 10_000L

/** Frames to run with the clock held, enough for the screen leaving to recompose and none to finish leaving. */
private const val HELD_FRAMES = 3

/**
 * The journeys behind the empty-stack crash (#1760), through the real host: a demo, a moderation, leaving the demo,
 * signing in for real, and Back twice in one frame; and Edit connection seeing a second sign-in land as it leaves. An
 * apply observer records the smallest stack any frame could have read, so a stack emptied and refilled in between counts.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = PHONE)
class BackStackNeverEmptyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    private var smallest = Int.MAX_VALUE
    private var observer: ObserverHandle? = null

    @After
    fun stopWatching() {
        observer?.dispose()
    }

    /** Starts recording the stack's size at every applied change, from the stack the launch built. */
    private fun watchStack() {
        val stack = host.activity.backStack
        smallest = stack.size
        observer = Snapshot.registerApplyObserver { _, _ -> smallest = minOf(smallest, stack.size) }
    }

    private fun assertStack(vararg expected: NavKey) = assertEquals(expected.toList(), host.stack())

    /**
     * Waits for the back stack to be [expected]. A screen's text can be on the page before its route is pushed (the hub
     * shows a request's status, and so does Requests), so a wait on text alone can return while the stack still holds
     * only the screen before (#2676).
     */
    private fun awaitStack(vararg expected: NavKey) =
        host.await("the back stack to be ${expected.toList()}") { host.stack() == expected.toList() }

    @Test
    fun `a demo, a moderation, leaving the demo and a local sign-in never empty the stack`() =
        demoToSignIn(R.string.setup_mode_local, wide = false)

    @Test
    fun `a demo, a moderation, leaving the demo and a Jellyfin sign-in never empty the stack`() =
        demoToSignIn(R.string.setup_mode_jellyfin, wide = false)

    /** An expanded window is the same two panes as a medium one, so the journey and its stacks are the same (#2348). */
    @Test
    @Config(qualifiers = TABLET)
    fun `on an expanded window, the same journey never empties the stack and Back at the root leaves`() =
        demoToSignIn(R.string.setup_mode_local, wide = true)

    /**
     * The journey, with [mode] for the sign-in. [wide] has the hub beside the pane, where Requests is the default section: Back
     * from it is the system's and leaves (#815), so the walk back to the hub is a phone's.
     */
    private fun demoToSignIn(
        mode: Int,
        wide: Boolean,
    ) {
        val hubAlone = listOf<NavKey>(HubRoute)
        host.connectDemo()
        host.launch(HomeRoute)
        watchStack()
        host.awaitShowing(host.string(R.string.hub_demo_exit))
        assertEquals(hubAlone, host.stack())

        openSection(R.string.hub_section_requests_desc)
        awaitStack(HubRoute, RequestsRoute)
        host.awaitShowing(host.string(R.string.requests_filter_processing))
        approveTheFirstPending()
        assertTrue("the request page was not on top", host.stack().last() is RequestDetailRoute)
        if (!wide) {
            host.back()
            assertStack(HubRoute, RequestsRoute)
            host.back()
            assertStack(HubRoute)
        }

        openSection(R.string.hub_section_settings_desc)
        awaitStack(HubRoute, SettingsRoute)
        host.awaitShowing(host.string(R.string.settings_about))
        exitTheDemo()
        assertStack(HomeRoute)

        signIn(mode)
        assertEquals(hubAlone, host.stack())

        // Two Backs in one frame: the first takes Settings, the second finds the root and leaves, once.
        openSection(R.string.hub_section_settings_desc)
        awaitStack(HubRoute, SettingsRoute)
        host.awaitShowing(host.string(R.string.settings_about))
        host.back(times = 2)
        assertEquals(hubAlone, host.stack())
        assertTrue("Back at the root did not leave", host.activity.isFinishing)
        host.back()
        assertEquals("the app was asked to leave more than once", 1, host.activity.exits)
        assertTrue("the stack was empty for a frame ($smallest)", smallest >= 1)
    }

    /**
     * Edit connection, opened from the hub's problem page, signs in and leaves. A second save lands while it is still on
     * its way out, so its guard sees a second sign-in: that must not take the hub as well.
     */
    @Test
    fun `a second sign-in landing while Edit connection leaves takes nothing else off the stack`() {
        host.connect()
        host.seerr.serve("GET /api/v1/request", EMPTY_PAGE)
        host.seerr.serve("GET /api/v1/issue", EMPTY_PAGE)
        // The server answers, but not with a user the hub can read: its problem page, which offers Edit connection.
        host.seerr.serve("GET /api/v1/auth/me", "")
        host.launch(HomeRoute)
        watchStack()
        host.awaitShowing(host.string(R.string.hub_couldnt_load_headline))
        host.compose
            .onNodeWithText(host.string(R.string.settings_edit_connection))
            .performClick()
        awaitStack(HubRoute, EditConnectionRoute)
        host.awaitShowing(host.string(R.string.setup_sign_in))

        host.seerr.viewer(id = 1, permissions = ADMIN)
        fillSignIn(R.string.setup_mode_local)
        host.compose.mainClock.autoAdvance = false
        host.compose.onNodeWithText(host.string(R.string.setup_sign_in)).performClick()
        awaitFrames(AWAIT_MILLIS) { host.stack() == listOf<NavKey>(HubRoute) }

        // Held mid-leave: Edit connection is still composed, and the connection changes under it.
        host.save(SeerrCredentials(serverUrl(), SeerrAuth.ApiKey("an0ther")))
        shadowOf(Looper.getMainLooper()).idle()
        repeat(HELD_FRAMES) { host.compose.mainClock.advanceTimeByFrame() }
        host.compose.mainClock.autoAdvance = true
        host.compose.waitForIdle()

        assertStack(HubRoute)
        assertTrue("the stack was empty for a frame ($smallest)", smallest >= 1)
        host.back()
        assertTrue("Back at the root did not leave", host.activity.isFinishing)
    }

    private fun serverUrl(): String =
        host.seerr.server
            .url("/")
            .toString()

    private fun openSection(description: Int) {
        host.compose
            .onNodeWithText(host.string(description), substring = true)
            .performScrollTo()
            .performClick()
        host.compose.waitForIdle()
    }

    /** The demo's pending request, approved from its page (#1705), as the crash's breadcrumbs had it. */
    private fun approveTheFirstPending() {
        val pending = filterChip(host.string(R.string.requests_filter_pending))
        // The hub beside the section also says "Downloading now", so the wait before this can pass before the chip row has composed.
        host.await("the Pending chip to be composed") {
            host.compose
                .onAllNodes(pending)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        host.compose.onNode(pending).performClick()
        host.clickUntilShowing("Sintel", host.string(R.string.request_primary_review))
        host.compose
            .onNodeWithText(host.string(R.string.request_primary_review))
            .performScrollTo()
            .performClick()
        host.awaitShowing(host.string(R.string.request_approve))
        host.compose.onNodeWithText(host.string(R.string.request_approve)).performClick()
        host.awaitShowing(host.string(R.string.request_approved))
    }

    /** Settings' Exit the demo, then the confirm's: Settings leaves, and setup takes the root. */
    private fun exitTheDemo() {
        val exit = host.string(R.string.hub_demo_exit)
        // The button is a row of a lazy list, composed before it is placed: until the list scrolls to it its bounds are zero.
        // Scrolling places it, flush against the viewport's edge, where a touch and the visibility check are borderline (#2002). So
        // the click is the button's own action, which does not depend on where it sits.
        val button = host.compose.onAllNodesWithText(exit).onLast()
        button.performScrollTo()
        host.compose.waitForIdle()
        button.performSemanticsAction(SemanticsActions.OnClick)
        host.awaitShowing(host.string(R.string.hub_demo_exit_confirm_message))
        host.compose
            .onAllNodesWithText(exit)
            .onLast()
            .performClick()
        host.awaitShowing(host.string(R.string.setup_try_demo))
    }

    /** Setup's two steps against the scripted server, signing in with [mode], until the hub is up. */
    private fun signIn(mode: Int) {
        host.seerr.start()
        host.seerr.viewer(id = 1, permissions = ADMIN)
        host.seerr.serve("GET /api/v1/request", EMPTY_PAGE)
        host.seerr.serve("GET /api/v1/issue", EMPTY_PAGE)
        host.compose.onNode(hasSetTextAction()).performTextInput(serverUrl())
        host.compose.onNodeWithText(host.string(R.string.setup_continue)).performClick()
        host.awaitShowing(host.string(R.string.setup_sign_in))
        fillSignIn(mode)
        host.compose.onNodeWithText(host.string(R.string.setup_sign_in)).performClick()
        host.awaitShowing(host.string(R.string.hub_section_requests_desc))
    }

    /** Picks [mode]'s chip and fills its two fields; both of the modes here answer with a session. */
    private fun fillSignIn(mode: Int) {
        host.seerr.serve("POST /api/v1/auth/local", """{"id":1}""", setCookie = SESSION)
        host.seerr.serve("POST /api/v1/auth/jellyfin", """{"id":1}""", setCookie = SESSION)
        host.compose.onNodeWithText(host.string(mode)).performClick()
        host.compose.waitForIdle()
        val fields = host.compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("admin@example.com")
        fields[1].performTextInput("pw")
        host.compose.waitForIdle()
    }

    /** Runs frames by hand, in real time, until [done]: the clock is held so what is leaving stays composed. */
    private fun awaitFrames(
        timeoutMillis: Long,
        done: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!done()) {
            check(System.currentTimeMillis() < deadline) { "never settled: ${host.stack()}" }
            shadowOf(Looper.getMainLooper()).idle()
            host.compose.mainClock.advanceTimeByFrame()
            Thread.sleep(FRAME_SLEEP_MILLIS)
        }
    }
}

private const val FRAME_SLEEP_MILLIS = 5L
