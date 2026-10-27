package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stack operations every Back and every screen that leaves on its own go through: none of them can empty it (#1760). */
class BackStackOpsTest {
    private fun stackOf(vararg keys: NavKey) = NavBackStack(mutableStateListOf(*keys))

    @Test
    fun `replacing the stack is applied at once, so no reader ever sees it empty or half replaced`() {
        val stack = stackOf(HubRoute, UsersRoute, UserDetailRoute(1), UserSettingsRoute(1))
        val seen = mutableListOf<List<NavKey>>()
        val observer = Snapshot.registerApplyObserver { _, _ -> seen += stack.toList() }
        try {
            stack.replaceAll(listOf(HomeRoute, RequestsRoute))
            Snapshot.sendApplyNotifications()
            stack.replaceAll(listOf(HomeRoute, IssuesRoute, IssueDetailRoute(7)))
            Snapshot.sendApplyNotifications()
        } finally {
            observer.dispose()
        }

        assertEquals(
            listOf(listOf(HomeRoute, RequestsRoute), listOf(HomeRoute, IssuesRoute, IssueDetailRoute(7))),
            seen,
        )
    }

    @Test
    fun `replacing the stack keeps the instance of a route that is equal, so its page can still leave`() {
        val kept = UserDetailRoute(5)
        val stack = stackOf(HubRoute, UsersRoute, kept)

        stack.replaceAll(listOf(HubRoute, UsersRoute, UserDetailRoute(5)))

        assertSame(kept, stack.last())
        assertTrue(stack.popIfTop(kept))
    }

    @Test
    fun `replacing the stack with nothing is refused, and the stack is kept`() {
        val stack = stackOf(HubRoute, SettingsRoute)

        assertThrows(IllegalArgumentException::class.java) { stack.replaceAll(emptyList()) }
        assertEquals(listOf(HubRoute, SettingsRoute), stack.toList())
    }

    @Test
    fun `Back at the root pops nothing and says so`() {
        val stack = stackOf(HubRoute, RequestsRoute)

        assertTrue(stack.pop())
        assertFalse(stack.pop())
        assertFalse(stack.pop())
        assertEquals(listOf<NavKey>(HubRoute), stack.toList())
    }

    @Test
    fun `a page leaving on its own takes nothing when another page is on top`() {
        val stack = stackOf(HubRoute, UsersRoute, UserDetailRoute(1))

        assertFalse(stack.popIfTop(UserDetailRoute(2)))
        assertFalse(stack.popIfTop(UsersRoute))
        assertEquals(listOf(HubRoute, UsersRoute, UserDetailRoute(1)), stack.toList())
    }

    @Test
    fun `of two equal pages, a late call from the older one leaves the newer on top`() {
        val older = UserDetailRoute(5)
        val newer = UserDetailRoute(5)
        val stack = stackOf(HubRoute, UsersRoute, older, RequestDetailRoute(9), newer)

        assertFalse(stack.popIfTop(older))
        assertSame(newer, stack.last())
        assertEquals(5, stack.size)

        assertTrue(stack.popIfTop(newer))
        assertEquals(listOf(HubRoute, UsersRoute, older, RequestDetailRoute(9)), stack.toList())
    }

    @Test
    fun `a page that is the root is never taken off by its own call`() {
        val stack = stackOf(HubRoute)

        assertFalse(stack.popIfTop(HubRoute))
        assertEquals(listOf<NavKey>(HubRoute), stack.toList())
    }

    @Test
    fun `an object route is never stacked on itself, so its own call is always about the one on top`() {
        val stack = stackOf(HubRoute, SettingsRoute)

        stack.push(EditConnectionRoute)
        stack.push(EditConnectionRoute)

        assertEquals(listOf(HubRoute, SettingsRoute, EditConnectionRoute), stack.toList())
        assertTrue(stack.popIfTop(EditConnectionRoute))
        assertFalse(stack.popIfTop(EditConnectionRoute))
        assertEquals(listOf(HubRoute, SettingsRoute), stack.toList())
    }

    @Test
    fun `in three panes, opening what is already open keeps its instance, so its removal still closes it`() {
        val panes = PaneLayout(hubBeside = { true }, showBack = { false }, threePane = { true })
        val open = RequestDetailRoute(5)
        val stack = stackOf(HubRoute, RequestsRoute, open)

        panes.open(stack, RequestDetailRoute(5))

        assertSame(open, stack.last())
        assertEquals(3, stack.size)
        assertTrue(stack.popIfTop(open))
        assertEquals(listOf(HubRoute, RequestsRoute), stack.toList())
    }
}
