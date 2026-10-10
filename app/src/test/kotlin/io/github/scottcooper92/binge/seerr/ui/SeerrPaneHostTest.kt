package io.github.scottcooper92.binge.seerr.ui

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
import io.github.scottcooper92.binge.seerr.ui.settings.server.ServerSettingsPage
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
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

/** A landscape tablet: past the design system's expanded-rail line, so the host lays out three panes (#1110). */
private const val TABLET = "w1280dp-h800dp"

/**
 * [SeerrPaneHost] in a two-pane window, with the pane locals it provides read the way the design system reads them:
 * [LocalPaneInnerEdge] through [PaneContent], the Back arrow through [paneBackOrNull]. [PaneBackLocalsTest] and the
 * gutter frames set those locals by hand, so neither fails if the host stops providing [LocalIsSinglePaneNav] or
 * [LocalPaneDepth] (#823). The entries are stand-ins wrapped as the real ones are, because the real screens take Hilt
 * ViewModels.
 *
 * Two panes on a window under the landscape-tablet line; [TABLET] for the three panes above it.
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

    private fun show(vararg keys: NavKey): NavBackStack<NavKey> {
        lateinit var backStack: NavBackStack<NavKey>
        rule.setContent {
            backStack = remember { NavBackStack(mutableStateListOf(*keys)) }
            SeerrPaneHost(backStack, connected = true, defaultSection = { DetailPaneContent { Record(DEFAULT) } }) {
                entry<HubRoute>(
                    metadata =
                        ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPaneContent { Record(DEFAULT) } }) + ThreePaneHub,
                ) { PaneContent(innerEdge = PaneEdge.End) { Record(HUB) } }
                entry<RequestsRoute>(metadata = DetailPane + ThreePaneSection) { DetailPaneContent { Record(REQUESTS) } }
                entry<SettingsRoute>(metadata = DetailPane + ThreePaneSection) { DetailPaneContent { Record(SETTINGS) } }
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

    /** The default section goes on the stack, so its list keeps its state when it opens something; Back still leaves (#815). */
    @Test
    @Config(qualifiers = TABLET)
    fun `in three panes, the hub alone gets the default section on the stack beside it`() {
        val backStack = show(HubRoute)

        assertEquals(listOf<NavKey>(HubRoute, RequestsRoute), backStack.toList())
        assertEquals(Pane(PaneEdge.Start, showsBack = false), panes.getValue(REQUESTS))
    }

    @Test
    @Config(qualifiers = TABLET)
    fun `in three panes, neither the section nor what it opened shows a Back arrow`() {
        show(HubRoute, SettingsRoute, ServerSettingsPageRoute(ServerSettingsPage.General))

        assertEquals(PaneEdge.End, panes.getValue(HUB).innerEdge)
        assertEquals(Pane(PaneEdge.Start, showsBack = false), panes.getValue(SETTINGS))
        assertEquals(Pane(PaneEdge.Start, showsBack = false), panes.getValue(PAGE))
    }

    @Test
    @Config(qualifiers = TABLET)
    fun `in three panes, a screen stacked above the open item keeps its Back arrow, and the section still has none`() {
        show(
            HubRoute,
            SettingsRoute,
            ServerSettingsPageRoute(ServerSettingsPage.General),
            ServerSettingsPageRoute(ServerSettingsPage.Users),
        )

        assertEquals(Pane(PaneEdge.Start, showsBack = false), panes.getValue(SETTINGS))
        assertEquals(Pane(PaneEdge.Start, showsBack = true), panes.getValue(SUBPAGE))
    }
}
