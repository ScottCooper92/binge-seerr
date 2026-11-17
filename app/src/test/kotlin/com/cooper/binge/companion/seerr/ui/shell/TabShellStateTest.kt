package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.ui.RequestDetailRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import com.cooper.binge.companion.seerr.ui.pop
import com.cooper.binge.companion.seerr.ui.push
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

private enum class Tab { One, Two, Three }

private data class Page(
    val id: Int,
) : NavKey

class TabShellStateTest {
    private val one = NavBackStack<NavKey>(Page(1))
    private val two = NavBackStack<NavKey>(Page(2))
    private val three = NavBackStack<NavKey>(Page(3))

    private fun state(initial: Int = 0) =
        TabShellState(Tab.entries, mutableIntStateOf(initial), mapOf(Tab.One to one, Tab.Two to two, Tab.Three to three))

    @Test
    fun `the tab it starts on is the one showing`() {
        assertEquals(Tab.Three, state(initial = 2).selected)
    }

    @Test
    fun `another tab shows as it was left`() {
        val shell = state()
        one.push(Page(10))
        shell.select(Tab.Two)
        shell.select(Tab.One)

        assertEquals(Tab.One, shell.selected)
        assertEquals(listOf(Page(1), Page(10)), one.toList())
    }

    @Test
    fun `each tab has a stack of its own`() {
        val shell = state()
        shell.stack(Tab.Two).push(Page(20))

        assertEquals(1, one.size)
        assertEquals(2, two.size)
        assertSame(three, shell.stack(Tab.Three))
    }

    @Test
    fun `selecting the tab that is showing goes back to its root`() {
        val shell = state()
        one.push(Page(10))
        one.push(Page(11))
        shell.select(Tab.One)

        assertEquals(listOf(Page(1)), one.toList())
        assertEquals(Tab.One, shell.selected)
    }

    @Test
    fun `selecting the tab that is showing leaves the other stacks alone`() {
        val shell = state()
        two.push(Page(20))
        one.push(Page(10))
        shell.select(Tab.One)

        assertEquals(2, two.size)
    }

    @Test
    fun `selecting a tab at its root changes nothing`() {
        val shell = state()
        shell.select(Tab.One)

        assertEquals(listOf(Page(1)), one.toList())
    }

    @Test
    fun `showing a tab pops nothing`() {
        val shell = state(initial = 1)
        two.push(Page(20))
        shell.show(Tab.Two)

        assertEquals(2, two.size)
    }

    @Test
    fun `back from a root has nothing to pop so the caller leaves`() {
        assertFalse(one.pop())
        one.push(Page(10))
        assertEquals(true, one.pop())
    }

    @Test
    fun `a tab with no stack is refused`() {
        assertThrows(IllegalArgumentException::class.java) { TabShellState(Tab.entries, mutableIntStateOf(0), mapOf(Tab.One to one)) }
    }

    @Test
    fun `a link replaces the app stack and shows Settings whichever tab was showing`() {
        val stacks = SeerrTab.entries.associateWith { NavBackStack<NavKey>(MoviesRoot) }
        val shell = TabShellState(SeerrTab.entries, mutableIntStateOf(SeerrTab.Tv.ordinal), stacks)
        val appStack = NavBackStack<NavKey>(ShellRoute, RequestsRoute)

        shell.openLink(appStack, listOf(ShellRoute, RequestsRoute, RequestDetailRoute(7)))

        assertEquals(SeerrTab.Settings, shell.selected)
        assertEquals(listOf(ShellRoute, RequestsRoute, RequestDetailRoute(7)), appStack.toList())
        appStack.pop()
        appStack.pop()
        assertEquals("Back walks the link and lands on the shell", listOf<NavKey>(ShellRoute), appStack.toList())
    }
}
