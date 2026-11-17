package com.cooper.binge.companion.seerr.ui.shell

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.HubRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import com.cooper.binge.companion.seerr.ui.settleHome
import org.junit.Assert.assertEquals
import org.junit.Test

/** The root route follows the connection and, with the shell on, is the tab bar and not the hub. */
class ShellRootSwapTest {
    private fun stack(vararg routes: NavKey) = NavBackStack<NavKey>(*routes)

    @Test
    fun `a connected phone with the shell on gets the tab bar at the root`() {
        val stack = stack(HomeRoute)
        stack.settleHome(connected = true, shell = true)

        assertEquals(listOf<NavKey>(ShellRoute), stack.toList())
    }

    @Test
    fun `with the shell off the root is the hub as it always was`() {
        val stack = stack(HomeRoute)
        stack.settleHome(connected = true, shell = false)

        assertEquals(listOf<NavKey>(HubRoute), stack.toList())
    }

    @Test
    fun `the flag arriving after the hub is up swaps the root and keeps what is above it`() {
        val stack = stack(HubRoute, RequestsRoute)
        stack.settleHome(connected = true, shell = true)

        assertEquals(listOf(ShellRoute, RequestsRoute), stack.toList())
    }

    @Test
    fun `a window that grows wide swaps the tab bar back for the hub`() {
        val stack = stack(ShellRoute, RequestsRoute)
        stack.settleHome(connected = true, shell = false)

        assertEquals(listOf(HubRoute, RequestsRoute), stack.toList())
    }

    @Test
    fun `an unresolved connection leaves the root alone for the first frame`() {
        val stack = stack(HomeRoute)
        stack.settleHome(connected = null, shell = true)

        assertEquals(listOf<NavKey>(HomeRoute), stack.toList())
    }

    @Test
    fun `signing out from the shell takes the whole stack back to setup`() {
        val stack = stack(ShellRoute, RequestsRoute)
        stack.settleHome(connected = false, shell = true)

        assertEquals(listOf<NavKey>(HomeRoute), stack.toList())
    }

    @Test
    fun `a link opened before the connection resolves keeps its routes under the swap`() {
        val stack = stack(HomeRoute, RequestsRoute)
        stack.settleHome(connected = true, shell = true)

        assertEquals(listOf(ShellRoute, RequestsRoute), stack.toList())
    }
}
