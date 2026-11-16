package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a settings search result does to the stack in each kind of window (#1985): it opens in the right pane, and Back is the hub. */
class PaneLayoutOpenFromSearchTest {
    private val page = ServerSettingsPageRoute(ServerSettingsPage.Logs)

    private fun stackOf(vararg keys: NavKey) = NavBackStack(mutableStateListOf(*keys))

    private fun layout(
        hubBeside: Boolean,
        threePane: Boolean = false,
    ) = PaneLayout(hubBeside = { hubBeside }, showBack = { true }, threePane = { threePane })

    private fun NavBackStack<NavKey>.after(layout: PaneLayout): List<NavKey> {
        layout.openFromSearch(this, page)
        return toList()
    }

    @Test
    fun `with the hub beside the pane, a result replaces what is open, so Back is the hub`() {
        val twoPanes = layout(hubBeside = true)

        assertEquals(listOf(HubRoute, page), stackOf(HubRoute).after(twoPanes))
        assertEquals(listOf(HubRoute, page), stackOf(HubRoute, RequestsRoute).after(twoPanes))
        assertEquals(listOf(HubRoute, page), stackOf(HubRoute, RequestsRoute, RequestDetailRoute(7)).after(twoPanes))
    }

    @Test
    fun `in three panes, a result opens in the right pane beside the open section`() {
        val threePanes = layout(hubBeside = true, threePane = true)

        assertEquals(listOf(HubRoute, RequestsRoute, page), stackOf(HubRoute, RequestsRoute).after(threePanes))
        assertEquals(listOf(HubRoute, RequestsRoute, page), stackOf(HubRoute, RequestsRoute, RequestDetailRoute(7)).after(threePanes))
        assertEquals(listOf(HubRoute, page), stackOf(HubRoute).after(threePanes))
    }

    @Test
    fun `in one pane, a result stacks above the hub, so Back is the hub`() {
        val onePane = layout(hubBeside = false)

        assertEquals(listOf(HubRoute, page), stackOf(HubRoute).after(onePane))
    }

    @Test
    fun `a result already open is not stacked on itself`() {
        val twoPanes = layout(hubBeside = true)
        val stack = stackOf(HubRoute, page)

        assertEquals(listOf(HubRoute, page), stack.after(twoPanes))
    }
}
