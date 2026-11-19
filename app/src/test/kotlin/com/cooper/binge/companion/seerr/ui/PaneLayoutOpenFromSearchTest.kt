package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a settings search result does to the stack in each kind of window: it opens above the search, so Back is the search. */
class PaneLayoutOpenFromSearchTest {
    private val page = ServerSettingsPageRoute(ServerSettingsPage.Logs)

    private fun stackOf(vararg keys: NavKey) = NavBackStack(mutableStateListOf(*keys))

    private fun layout(hubBeside: Boolean) = PaneLayout(hubBeside = { hubBeside }, showBack = { true })

    private fun NavBackStack<NavKey>.after(layout: PaneLayout): List<NavKey> {
        layout.openFromSearch(this, page)
        return toList()
    }

    @Test
    fun `with the hub beside the pane, a result opens above the search and what was open stays`() {
        val twoPanes = layout(hubBeside = true)

        assertEquals(listOf(HubRoute, SettingsSearchRoute, page), stackOf(HubRoute, SettingsSearchRoute).after(twoPanes))
        assertEquals(
            listOf(HubRoute, RequestsRoute, SettingsSearchRoute, page),
            stackOf(HubRoute, RequestsRoute, SettingsSearchRoute).after(twoPanes),
        )
    }

    @Test
    fun `in one pane, a result opens above the search too`() {
        assertEquals(listOf(HubRoute, SettingsSearchRoute, page), stackOf(HubRoute, SettingsSearchRoute).after(layout(hubBeside = false)))
    }

    @Test
    fun `a result already open is not stacked on itself`() {
        val stack = stackOf(HubRoute, SettingsSearchRoute, page)

        assertEquals(listOf(HubRoute, SettingsSearchRoute, page), stack.after(layout(hubBeside = true)))
    }
}
