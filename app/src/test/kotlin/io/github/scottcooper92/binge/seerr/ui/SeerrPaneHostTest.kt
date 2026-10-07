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

/**
 * [SeerrPaneHost] in a two-pane window, with the pane locals it provides read the way the design system reads them:
 * [LocalPaneInnerEdge] through [PaneContent], the Back arrow through [paneBackOrNull]. [PaneBackLocalsTest] and the
 * gutter frames set those locals by hand, so neither fails if the host stops providing [LocalIsSinglePaneNav] or
 * [LocalPaneDepth] (#823). The entries are stand-ins wrapped as the real ones are, because the real screens take Hilt
 * ViewModels.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w1000dp-h800dp")
class SeerrPaneHostTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    /** What one pane composed with: the edge it shares with the pane beside it, and whether it shows its own Back. */
    private data class Pane(
        val innerEdge: PaneEdge?,
        val showsBack: Boolean,
    )

    private val panes = mutableMapOf<String, Pane>()

    private fun show(vararg keys: NavKey) {
        rule.setContent {
            val backStack = remember { NavBackStack(mutableStateListOf(*keys)) }
            SeerrPaneHost(backStack, connected = true) { _, _ ->
                entry<HubRoute>(
                    metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPaneContent { Record(DEFAULT) } }),
                ) { PaneContent(innerEdge = PaneEdge.End) { Record(HUB) } }
                entry<SettingsRoute>(metadata = DetailPane) { DetailPaneContent { Record(SETTINGS) } }
                entry<ServerSettingsPageRoute>(metadata = DetailPane) { DetailPaneContent { Record(PAGE) } }
            }
        }
        rule.waitForIdle()
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
}
