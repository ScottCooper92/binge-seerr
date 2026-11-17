package com.cooper.binge.companion.seerr.telemetry

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavKey
import com.cooper.binge.companion.seerr.ui.HomeRoute
import com.cooper.binge.companion.seerr.ui.RequestsRoute
import com.cooper.binge.companion.seerr.ui.ShellRoute
import com.cooper.binge.companion.seerr.ui.shell.MoviesRoot
import com.cooper.binge.companion.seerr.ui.shell.SeerrTab
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the app reports as its screen while the tab bar is on top, and as the tab changes (#2092). */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellScreenNamesTest {
    @Test
    fun `the tab bar reports the selected tab and the Settings tab keeps the hub's name`() {
        assertEquals("hub", screenNameFor(ShellRoute, SeerrTab.Settings))
        assertEquals("movies", screenNameFor(ShellRoute, SeerrTab.Movies))
        assertEquals("tv", screenNameFor(ShellRoute, SeerrTab.Tv))
        assertEquals("search", screenNameFor(ShellRoute, SeerrTab.Search))
    }

    @Test
    fun `every tab has a name of its own`() {
        assertEquals(
            SeerrTab.entries.size,
            SeerrTab.entries
                .map { it.screenName() }
                .toSet()
                .size,
        )
    }

    @Test
    fun `a page above the tab bar is reported by its own name whatever tab is under it`() {
        SeerrTab.entries.forEach { assertEquals("requests", screenNameFor(RequestsRoute, it)) }
        assertEquals("home", screenNameFor(HomeRoute, SeerrTab.Movies))
    }

    @Test
    fun `a key that is not a screen of the app reports nothing`() {
        assertNull(screenNameFor(MoviesRoot, SeerrTab.Settings))
        assertNull(screenNameFor(null, SeerrTab.Settings))
    }

    @Test
    fun `a switch of tab is a screen change and a page above is too`() =
        runTest(UnconfinedTestDispatcher()) {
            val stack = mutableStateListOf<NavKey>(ShellRoute)
            val tab = mutableStateOf(SeerrTab.Settings)
            val seen = mutableListOf<String>()
            val job = launch { screenNames(stack, selectedTab = { tab.value }).collect { seen += it } }

            fun change(block: () -> Unit) {
                Snapshot.withMutableSnapshot(block)
                Snapshot.sendApplyNotifications()
            }
            change { tab.value = SeerrTab.Movies }
            change { tab.value = SeerrTab.Settings }
            change { stack.add(RequestsRoute) }
            change { stack.removeAt(stack.lastIndex) }
            change { tab.value = SeerrTab.Settings }
            job.cancel()

            assertEquals(listOf("hub", "movies", "hub", "requests", "hub"), seen)
        }
}
