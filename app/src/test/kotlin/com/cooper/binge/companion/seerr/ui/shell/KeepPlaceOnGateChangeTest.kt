package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.cooper.homelab.core.uiandroid.shell.TabShellState
import org.junit.Assert.assertEquals
import org.junit.Test

/** What flipping Discovery does to the tabs is the app's own. The page the user is on is the app stack's, which the helper is not given. */
class KeepPlaceOnGateChangeTest {
    private val movies = NavBackStack<NavKey>(MoviesRoot, SearchRoot)
    private val settings = NavBackStack<NavKey>(SettingsRoot)

    private fun shell(selected: SeerrTab): TabShellState<SeerrTab> {
        val stacks = SeerrTab.entries.associateWith { NavBackStack<NavKey>(MoviesRoot) }.toMutableMap()
        stacks[SeerrTab.Movies] = movies
        stacks[SeerrTab.Settings] = settings
        return TabShellState(SeerrTab.entries, mutableIntStateOf(selected.ordinal), stacks)
    }

    @Test
    fun `a toggle shows Settings and takes every tab's own stack back to its root`() {
        val shell = shell(SeerrTab.Movies)

        shell.settleOnSettings()

        assertEquals(SeerrTab.Settings, shell.selected)
        assertEquals(listOf<NavKey>(MoviesRoot), movies.toList())
        assertEquals(listOf<NavKey>(SettingsRoot), settings.toList())
    }
}
