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
        assertNull(HomeRoute.hubSection())
        assertNull(RequestDetailRoute(7).hubSection())
        assertNull(EditConnectionRoute.hubSection())
    }

    @Test
    fun `opening a section from the hub pushes it`() {
        val stack = backStack(HomeRoute)
        stack.openSection(HubSection.Requests)
        assertEquals(listOf(HomeRoute, RequestsRoute), stack.toList())
    }

    /** The whole point beside the hub: Back returns to the hub, not to the section before this one. */
    @Test
    fun `opening a second section replaces the first`() {
        val stack = backStack(HomeRoute)
        stack.openSection(HubSection.Requests)
        stack.openSection(HubSection.Issues)
        stack.openSection(HubSection.Users)
        assertEquals(listOf(HomeRoute, UsersRoute), stack.toList())
    }

    /** A section with no screen of its own is still a section, so it replaces and is replaced. */
    @Test
    fun `the placeholder section replaces like any other`() {
        val stack = backStack(HomeRoute)
        stack.openSection(HubSection.Requests)
        stack.openSection(HubSection.Blocklist)
        assertEquals(listOf(HomeRoute, BlocklistRoute), stack.toList())
    }
}
