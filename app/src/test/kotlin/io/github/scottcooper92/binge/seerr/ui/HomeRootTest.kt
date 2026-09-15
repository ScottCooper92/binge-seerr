package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRootTest {
    private fun backStack(vararg keys: NavKey) = NavBackStack(mutableStateListOf(*keys))

    @Test
    fun `a connection puts the hub at the root`() {
        val stack = backStack(HomeRoute)
        stack.settleHome(connected = true)
        assertEquals(listOf(HubRoute), stack.toList())
    }

    /** A notification's link roots its stack on HomeRoute; the section and detail it opened stay put. */
    @Test
    fun `the routes above the root stay where they are`() {
        val stack = backStack(HomeRoute, RequestsRoute, RequestDetailRoute(7))
        stack.settleHome(connected = true)
        assertEquals(listOf(HubRoute, RequestsRoute, RequestDetailRoute(7)), stack.toList())
    }

    @Test
    fun `a disconnect puts home back at the root`() {
        val stack = backStack(HubRoute)
        stack.settleHome(connected = false)
        assertEquals(listOf(HomeRoute), stack.toList())
    }

    /**
     * Unresolved is home, not "leave it alone": the hub is the list pane, so a spinner left there
     * renders beside the detail placeholder. A saved server restores the hub at the root before the
     * answer arrives, which is why this is the case a user meets rather than a frame.
     */
    @Test
    fun `an unresolved connection puts home at the root`() {
        val restored = backStack(HubRoute)
        restored.settleHome(connected = null)
        assertEquals(listOf(HomeRoute), restored.toList())

        val alreadyHome = backStack(HomeRoute)
        alreadyHome.settleHome(connected = null)
        assertEquals(listOf(HomeRoute), alreadyHome.toList())
    }

    /** Only the root moves, so a link's section and detail survive the connecting state too. */
    @Test
    fun `an unresolved connection leaves the routes above the root`() {
        val stack = backStack(HubRoute, RequestsRoute, RequestDetailRoute(7))
        stack.settleHome(connected = null)
        assertEquals(listOf(HomeRoute, RequestsRoute, RequestDetailRoute(7)), stack.toList())
    }

    @Test
    fun `a stack without a home root is left alone`() {
        val empty = backStack()
        empty.settleHome(connected = true)
        assertEquals(emptyList<NavKey>(), empty.toList())

        val other = backStack(EditConnectionRoute)
        other.settleHome(connected = false)
        assertEquals(listOf(EditConnectionRoute), other.toList())
    }
}
