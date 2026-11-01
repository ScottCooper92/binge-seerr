package com.cooper.binge.companion.seerr.ui.nav

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.ui.DvrInstanceRoute
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.ServerSettingsPageRoute
import com.cooper.binge.companion.seerr.ui.SettingsRoute
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Two panes: wide enough for the hub beside a section. */
private const val WIDE = "w900dp-h800dp"

private const val AWAIT_SERVICES_MILLIS = 10_000L

/**
 * The demo server through the app's real screens (#1705): the hub, a list read through the cache, a request page, and a
 * write that a later read shows. A pass means a reviewer with no server can use these screens, not only that the
 * endpoints answer.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = WIDE)
class DemoJourneyTest {
    @get:Rule
    internal val host = RealNavHostRule(this)

    @Test
    fun `the hub names the demo and offers the way out`() {
        host.connectDemo()
        host.launch(HomeRoute)

        host.awaitShowing(host.string(R.string.demo_indicator))
        host.awaitShowing(host.string(R.string.hub_demo_exit))
    }

    @Test
    fun `a pending request opens from the list, and approving it changes what the page reads`() {
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.requests_filter_processing))
        host.awaitShowing("Big Buck Bunny")

        host.compose.onNode(filterChip(host.string(R.string.requests_filter_pending))).performClick()
        host.awaitShowing("Sintel")
        // The list crossfades between filters, so a row may be drawn twice for a moment.
        host.compose
            .onAllNodesWithText("Sintel")
            .onLast()
            .performClick()
        host.awaitShowing(host.string(R.string.request_primary_review))
        host.compose
            .onNodeWithText(host.string(R.string.request_primary_review))
            .performScrollTo()
            .performClick()
        host.awaitShowing(host.string(R.string.request_approve))
        host.compose.onNodeWithText(host.string(R.string.request_approve)).performClick()

        // The server took the approval, and the page now reads the request as approved and downloading.
        host.awaitShowing(host.string(R.string.request_approved))
        host.awaitShowing("4.0 GB")
    }

    /**
     * A refused delete walked through the real screens (#1736). The demo refuses once its Radarr is no longer the default; the
     * test un-defaults it through the same route the Default switch calls, then follows what an admin does from there.
     */
    @Test
    fun `a delete refused for want of a default server opens Services in the app, and Back brings Delete files back`() {
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing("Big Buck Bunny")
        turnOffTheDefaultRadarr()
        host.compose
            .onAllNodesWithText(host.string(R.string.hub_section_requests))
            .onLast()
            .performClick()
        host.awaitShowing("Big Buck Bunny")

        host.compose
            .onAllNodesWithText("Big Buck Bunny")
            .onLast()
            .performClick()
        openManageSheet()
        host.compose
            .onAllNodesWithText(host.string(R.string.media_delete_files))
            .onLast()
            .performClick()
        // The confirm dialog's own button reads the same as the row, and is the last one on screen.
        host.awaitShowing(host.string(R.string.media_delete_files_confirm_title, host.string(R.string.media_client_radarr)))
        host.compose
            .onAllNodesWithText(host.string(R.string.media_delete_files))
            .onLast()
            .performClick()
        host.awaitShowing(host.string(R.string.media_files_not_deleted))

        host.compose.onNodeWithText(host.string(R.string.media_files_not_deleted)).performSemanticsAction(SemanticsActions.OnClick)
        awaitOnTop("Services") { it == ServerSettingsPageRoute(ServerSettingsPage.Services) }
        assertFalse("the sheet closed on the way", host.isShowing(host.string(R.string.media_files_not_deleted)))

        host.back()
        openManageSheet()
        host.awaitShowing(host.string(R.string.media_delete_files))
        assertFalse("the stale refusal is gone", host.isShowing(host.string(R.string.media_files_not_deleted)))
    }

    /** What an admin does: Settings, Services, the Radarr, its Default switch off, Save. Seerr would refuse for the only one; the demo does not. */
    private fun turnOffTheDefaultRadarr() {
        // The hub's Settings row names Services too, so Settings is waited for by what only its own page shows.
        host.compose
            .onAllNodes(hasText(host.string(R.string.hub_section_settings)))
            .onLast()
            .performScrollTo()
            .performClick()
        awaitOnTop("Settings") { it == SettingsRoute }
        host.awaitShowing("Demo Radarr")
        tapWhenShown("Demo Radarr")
        awaitOnTop("Services") { it == ServerSettingsPageRoute(ServerSettingsPage.Services) }
        host.awaitShowing("Demo Radarr")
        tapWhenShown("Demo Radarr")
        awaitOnTop("the instance page") { it is DvrInstanceRoute }
        host.awaitShowing(host.string(R.string.server_settings_dvr_default))
        host.compose
            .onAllNodesWithText(host.string(R.string.server_settings_dvr_default))
            .onLast()
            .performClick()
        host.compose
            .onAllNodesWithText(host.string(R.string.user_settings_save))
            .onLast()
            .performClick()
        // What the switch and Save did, read from the server the screens read: Demo Radarr is no longer the default.
        host.compose.waitUntil(timeoutMillis = AWAIT_SERVICES_MILLIS) { !host.api { it.radarrSettings().first().isDefault } }
    }

    /** Taps the first node whose text includes [text], once there is one: a Settings row names its services in a joined line, and an override rule names its Radarr after the instances. */
    private fun tapWhenShown(text: String) {
        host.compose.waitUntil(timeoutMillis = AWAIT_SERVICES_MILLIS) {
            try {
                host.compose
                    .onAllNodesWithText(text, substring = true)
                    .onFirst()
                    .performSemanticsAction(SemanticsActions.OnClick)
                true
            } catch (missing: AssertionError) {
                false
            }
        }
    }

    private fun awaitOnTop(
        what: String,
        matches: (NavKey) -> Boolean,
    ) {
        try {
            host.compose.waitUntil(timeoutMillis = AWAIT_SERVICES_MILLIS) { host.stack().lastOrNull()?.let(matches) == true }
        } catch (timeout: ComposeTimeoutException) {
            throw ComposeTimeoutException("${timeout.message}: waiting for $what; back stack ${host.stack()}")
        }
        host.compose.waitForIdle()
    }

    private fun openManageSheet() {
        host.awaitShowing(host.string(R.string.request_primary_manage))
        // The hub's own Manage row reads the same, so the page's button is the last one.
        host.compose
            .onAllNodesWithText(host.string(R.string.request_primary_manage))
            .onLast()
            .performScrollTo()
            .performClick()
        host.awaitShowing(host.string(R.string.media_delete_files))
    }

    @Test
    fun `the issues section lists the open issues and opens one with its thread`() {
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_issues_desc))
        host.compose
            .onNodeWithText(host.string(R.string.hub_section_issues_desc), substring = true)
            .performScrollTo()
            .performClick()
        host.awaitShowing("Metropolis")
        host.awaitShowing("Night of the Living Dead")

        host.compose
            .onAllNodesWithText("Metropolis")
            .onLast()
            .performClick()
        host.awaitShowing("The subtitles drift")
        host.awaitShowing("Thanks, I will look at the file.")
    }

    @Test
    fun `the blocklist section lists the blocked title`() {
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_blocklist_desc))
        host.compose
            .onNodeWithText(host.string(R.string.hub_section_blocklist_desc), substring = true)
            .performScrollTo()
            .performClick()

        host.awaitShowing("Parasite")
    }

    @Test
    fun `the users section lists the accounts and opens one`() {
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_users_desc))
        host.compose
            .onNodeWithText(host.string(R.string.hub_section_users_desc), substring = true)
            .performScrollTo()
            .performClick()
        host.awaitShowing("Jordan")

        host.compose.onNodeWithText("Sam", substring = true).performClick()
        host.awaitShowing("sam@example.com")
    }

    @Test
    fun `settings lists the server's pages, and offers no second server to edit into the demo's place`() {
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_section_settings_desc))
        host.compose
            .onNodeWithText(host.string(R.string.hub_section_settings_desc), substring = true)
            .performScrollTo()
            .performClick()

        host.awaitShowing(host.string(R.string.settings_about))
        assertFalse(host.isShowing(host.string(R.string.settings_edit_connection)))
    }

    @Test
    fun `exiting the demo asks first, then returns to the address step with nothing kept`() {
        host.connectDemo()
        host.launch(HomeRoute)
        host.awaitShowing(host.string(R.string.hub_demo_exit))
        host.compose
            .onNodeWithText(host.string(R.string.hub_demo_exit))
            .performScrollTo()
            .performClick()
        host.awaitShowing(host.string(R.string.hub_demo_exit_confirm_message))
        host.compose
            .onAllNodesWithText(host.string(R.string.hub_demo_exit))
            .onLast()
            .performClick()

        host.awaitShowing(host.string(R.string.setup_try_demo))
        assertFalse(host.isShowing(host.string(R.string.demo_indicator)))
    }
}

/** The filter chip itself: its label first, then at most a count, and not a row that has "Pending" among its words. */
internal fun filterChip(label: String) =
    SemanticsMatcher("a chip labelled $label") { node ->
        val texts =
            node.config
                .getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .map { it.text.trim('[', ']', ' ') }
        texts.firstOrNull() == label && texts.size <= 2 && node.config.contains(SemanticsActions.OnClick)
    }
