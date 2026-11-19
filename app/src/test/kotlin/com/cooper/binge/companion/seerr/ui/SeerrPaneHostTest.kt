package com.cooper.binge.companion.seerr.ui

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.binge.designsystem.LocalIsSinglePaneNav
import com.binge.designsystem.LocalPaneDepth
import com.binge.designsystem.LocalPaneInnerEdge
import com.binge.designsystem.PaneContent
import com.binge.designsystem.PaneEdge
import com.binge.designsystem.paneBackOrNull
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val HUB = "hub"
private const val DEFAULT = "default section"
private const val SETTINGS = "settings"
private const val PAGE = "server settings page"
private const val REQUESTS = "requests"
private const val SUBPAGE = "server settings subpage"
private const val REQUEST = "request"

/** A landscape tablet: past the design system's expanded-rail line, laid out as two panes like any wide window (#2348). */
private const val TABLET = "w1280dp-h800dp"

/** A phone: one pane, so a section on the hub claims Back. */
private const val PHONE = "w400dp-h800dp"

/**
 * [SeerrPaneHost] in a two-pane window, with the pane locals it provides read the way the design system reads them:
 * [LocalPaneInnerEdge] through [PaneContent], the Back arrow through [paneBackOrNull]. [PaneBackLocalsTest] and the
 * gutter frames set those locals by hand, so neither fails if the host stops providing [LocalIsSinglePaneNav] or
 * [LocalPaneDepth] (#823). The entries are stand-ins wrapped as the real ones are, because the real screens take Hilt
 * ViewModels.
 *
 * Two panes on a window under the landscape-tablet line; [TABLET] is the same two panes above it.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w900dp-h800dp")
class SeerrPaneHostTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    /** What one pane composed with: the edge it shares with the pane beside it, and whether it shows its own Back. */
    private data class Pane(
        val innerEdge: PaneEdge?,
        val showsBack: Boolean,
    )

    private val panes = mutableMapOf<String, Pane>()
    private var exits = 0
    private lateinit var dispatcher: OnBackPressedDispatcher

    private fun show(vararg keys: NavKey): NavBackStack<NavKey> {
        lateinit var backStack: NavBackStack<NavKey>
        rule.setContent {
            backStack = remember { NavBackStack(mutableStateListOf(*keys)) }
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            SeerrPaneHost(backStack, connected = true, onExit = { exits++ }) {
                entry<HubRoute>(
                    metadata =
                        ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPaneContent { Record(DEFAULT) } }),
                ) { PaneContent(innerEdge = PaneEdge.End) { Record(HUB) } }
                entry<RequestsRoute>(metadata = DetailPane + SectionPane) { DetailPaneContent { Record(REQUESTS) } }
                entry<SettingsRoute>(metadata = DetailPane + SectionPane) { DetailPaneContent { Record(SETTINGS) } }
                entry<RequestDetailRoute>(metadata = DetailPane) { DetailPaneContent { Record(REQUEST) } }
                entry<ServerSettingsPageRoute>(metadata = DetailPane) { route ->
                    DetailPaneContent { Record(if (route.page == ServerSettingsPage.General) PAGE else SUBPAGE) }
                }
            }
        }
        rule.waitForIdle()
        return backStack
    }

    @Composable
    private fun Record(name: String) {
        val pane = Pane(LocalPaneInnerEdge.current, paneBackOrNull {} != null)
        SideEffect { panes[name] = pane }
    }

    @Test
    fun `beside the hub, the section shares its start edge and the hub its end edge`() {
        show(HubRoute, SettingsRoute)

        assertEquals(PaneEdge.End, panes.getValue(HUB).innerEdge)
        assertEquals(PaneEdge.Start, panes.getValue(SETTINGS).innerEdge)
    }

    @Test
    fun `beside the hub, the default section shares its start edge and shows no Back arrow`() {
        show(HubRoute)

        assertEquals(Pane(PaneEdge.Start, showsBack = false), panes.getValue(DEFAULT))
    }

    @Test
    fun `beside the hub, a section opened from it shows no Back arrow`() {
        show(HubRoute, SettingsRoute)

        assertEquals(Pane(PaneEdge.Start, showsBack = false), panes.getValue(SETTINGS))
    }

    @Test
    fun `beside the hub, a screen stacked above a section keeps its Back arrow`() {
        show(HubRoute, SettingsRoute, ServerSettingsPageRoute(ServerSettingsPage.General))

        assertEquals(Pane(PaneEdge.Start, showsBack = true), panes.getValue(PAGE))
    }

    /** The Delete files row leaves a request for Settings > Services (#1736): the page keeps its Back arrow, and Back returns to the request. */
    @Test
    fun `beside the hub, the Services page opened from a request keeps its Back arrow, and Back returns to the request`() {
        val backStack = show(HubRoute, RequestsRoute, RequestDetailRoute(1), ServerSettingsPageRoute(ServerSettingsPage.Services))

        assertEquals(Pane(PaneEdge.Start, showsBack = true), panes.getValue(SUBPAGE))

        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.waitForIdle()

        assertEquals(listOf<NavKey>(HubRoute, RequestsRoute, RequestDetailRoute(1)), backStack.toList())
        assertEquals(Pane(PaneEdge.Start, showsBack = true), panes.getValue(REQUEST))
    }

    /** An expanded window is the same two panes: nothing is put on the stack beside the hub, and the default section is its placeholder (#2348). */
    @Test
    @Config(qualifiers = TABLET)
    fun `at an expanded width the hub alone has nothing on the stack beside it`() {
        val backStack = show(HubRoute)

        assertEquals(listOf<NavKey>(HubRoute), backStack.toList())
    }

    /**
     * Two Backs in one frame, as a quick double tap or a key repeat sends them: the second reaches the scene from before
     * the first, which still claims Back, and once popped an empty stack (#1760). The root stays, and the app leaves once.
     */
    @Test
    @Config(qualifiers = PHONE)
    fun `two Backs in one frame from a section leave the hub, and the app leaves once`() {
        val backStack = show(HubRoute, RequestsRoute)

        rule.runOnUiThread {
            dispatcher.onBackPressed()
            dispatcher.onBackPressed()
        }
        rule.waitForIdle()

        assertEquals(listOf<NavKey>(HubRoute), backStack.toList())
        assertEquals(1, exits)

        rule.runOnUiThread { dispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals("the app was asked to leave twice", 1, exits)
        assertEquals(listOf<NavKey>(HubRoute), backStack.toList())
    }

    /** At a settled root the scene claims no Back: a predictive gesture is the system's, and the app is not asked too. */
    @Test
    @Config(qualifiers = PHONE)
    fun `a predictive Back at the root is left to the system and asks the app nothing`() {
        val backStack = show(HubRoute)

        rule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }
        rule.waitForIdle()

        assertEquals(0, exits)
        assertEquals(listOf<NavKey>(HubRoute), backStack.toList())
    }

    /** A gesture from a section that completes while a second Back arrives: the hub stays, and the app leaves once. */
    @Test
    @Config(qualifiers = PHONE)
    fun `a predictive Back from a section with a second Back behind it leaves the app once`() {
        val backStack = show(HubRoute, RequestsRoute)

        rule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        rule.waitForIdle()
        rule.runOnUiThread {
            dispatcher.onBackPressed()
            dispatcher.onBackPressed()
            dispatcher.onBackPressed()
        }
        rule.waitForIdle()

        assertEquals(listOf<NavKey>(HubRoute), backStack.toList())
        assertEquals(1, exits)
    }
}
