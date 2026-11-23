package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.binge.designsystem.shell.TabShellState
import com.cooper.binge.companion.seerr.ui.RequestDetailRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import com.cooper.binge.companion.seerr.ui.pop
import org.junit.Assert.assertEquals
import org.junit.Test

/** What a link does to the shell is the app's own: the shared shell knows nothing of Settings or of the app's routes. */
class OpenLinkTest {
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
