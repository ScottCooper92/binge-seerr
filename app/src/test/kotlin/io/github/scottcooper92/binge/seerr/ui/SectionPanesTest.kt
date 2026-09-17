package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import io.github.scottcooper92.binge.seerr.ui.hub.HubSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SectionPanesTest {
    private fun backStack(vararg keys: NavKey) = NavBackStack(mutableStateListOf(*keys))

    /** Every section has a route, and that route names the section back. A new section fails here first. */
    @Test
    fun `every section round-trips through its route`() {
        HubSection.entries.forEach { section ->
            assertEquals(section, section.route().hubSection())
        }
    }

    @Test
    fun `a route that is not a section names none`() {
        assertNull(HubRoute.hubSection())
        assertNull(RequestDetailRoute(7).hubSection())
        assertNull(EditConnectionRoute.hubSection())
    }

    @Test
    fun `opening a section from the hub pushes it`() {
        val stack = backStack(HubRoute)
        stack.openSection(HubSection.Issues, defaultShowing = false)
        assertEquals(listOf(HubRoute, IssuesRoute), stack.toList())
    }

    /** Back from the new section returns to the hub, not to the section or the detail before it. */
    @Test
    fun `opening a section replaces the open one and everything it stacked`() {
        val stack = backStack(HubRoute, RequestsRoute, RequestDetailRoute(7))
        stack.openSection(HubSection.Users, defaultShowing = false)
        assertEquals(listOf(HubRoute, UsersRoute), stack.toList())
    }

    /** On a narrow window the hub is alone on screen, so even the default section is a push. */
    @Test
    fun `the default section is pushed when nothing stands in for it`() {
        val stack = backStack(HubRoute)
        stack.openSection(DefaultSection, defaultShowing = false)
        assertEquals(listOf(HubRoute, DefaultSection.route()), stack.toList())
    }

    @Test
    fun `beside the hub, opening the default section clears the pane back to it`() {
        val stack = backStack(HubRoute, IssuesRoute, IssueDetailRoute(3))
        stack.openSection(DefaultSection, defaultShowing = true)
        assertEquals(listOf(HubRoute), stack.toList())

        stack.openSection(DefaultSection, defaultShowing = true)
        assertEquals(listOf(HubRoute), stack.toList())
    }

    @Test
    fun `beside the hub, any other section is pushed`() {
        val stack = backStack(HubRoute)
        stack.openSection(HubSection.Settings, defaultShowing = true)
        assertEquals(listOf(HubRoute, SettingsRoute), stack.toList())
    }

    @Test
    fun `the hub marks the section on the stack, even under what it opened`() {
        val stack = backStack(HubRoute, UsersRoute, UserDetailRoute(9), UserSettingsRoute(9))
        assertEquals(HubSection.Users, stack.selectedSection(defaultShowing = true))
        assertEquals(HubSection.Users, stack.selectedSection(defaultShowing = false))
    }

    @Test
    fun `the default section is marked only while it stands in beside the hub`() {
        assertEquals(DefaultSection, backStack(HubRoute).selectedSection(defaultShowing = true))
        assertNull(backStack(HubRoute).selectedSection(defaultShowing = false))
        // The account card opens a user with no section under it: the pane shows that, not the default.
        assertNull(backStack(HubRoute, UserDetailRoute(1)).selectedSection(defaultShowing = true))
    }

    @Test
    fun `pane depth counts what is stacked above the hub`() {
        assertEquals(1, backStack(HubRoute, RequestsRoute).paneDepth())
        assertEquals(1, backStack(HubRoute, UserDetailRoute(1)).paneDepth())
        assertEquals(2, backStack(HubRoute, UsersRoute, UserDetailRoute(1)).paneDepth())
        assertEquals(2, backStack(HubRoute, UserDetailRoute(1), RequestDetailRoute(7)).paneDepth())
    }

    @Test
    fun `pane depth is the whole stack when the hub is not on it`() {
        assertEquals(1, backStack(HomeRoute).paneDepth())
    }

    /** The issue's own table (#335), checked directly against the two inputs the rule takes. */
    @Test
    fun `the back-arrow rule matches the issue's table`() {
        // [Hub, Requests]: depth 1, hidden — matches today.
        assertEquals(false, paneShowsBack(hubBeside = true, paneDepth = 1))
        // [Hub, UserDetail] opened from the hub: depth 1, hidden — the fix.
        assertEquals(false, paneShowsBack(hubBeside = true, paneDepth = 1))
        // [Hub, Users, UserDetail]: depth 2, shown — correct today.
        assertEquals(true, paneShowsBack(hubBeside = true, paneDepth = 2))
        // [Hub, UserDetail, RequestDetail]: depth 2, shown — correct.
        assertEquals(true, paneShowsBack(hubBeside = true, paneDepth = 2))
        // Narrow window, anything: shown, hubBeside false, regardless of depth.
        assertEquals(true, paneShowsBack(hubBeside = false, paneDepth = 1))
        assertEquals(true, paneShowsBack(hubBeside = false, paneDepth = 4))
    }

    /**
     * The section rule this replaces was `!hubBeside` alone, which only ever agreed with the general
     * one because a section beside the hub is always exactly the one entry above it — never stacked
     * deeper. This is that assumption, checked directly.
     */
    @Test
    fun `a section beside the hub is always at pane depth 1`() {
        assertEquals(1, backStack(HubRoute, RequestsRoute).paneDepth())
        assertEquals(1, backStack(HubRoute, SettingsRoute).paneDepth())
    }
}
