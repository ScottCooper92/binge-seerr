package com.cooper.binge.companion.seerr.ui.shell

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.homelab.core.uiandroid.shell.TabShellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeerrTabsStartTest {
    @get:Rule
    val compose: ComposeContentTestRule = createSeerrComposeRule()

    private fun cold(discovery: Boolean): TabShellState<SeerrTab> {
        lateinit var tabs: TabShellState<SeerrTab>
        compose.setContent { tabs = rememberSeerrTabs(discovery) }
        compose.waitForIdle()
        return tabs
    }

    @Test
    fun `a cold start with discovery on shows Movies and Movies is the start tab`() {
        val tabs = cold(discovery = true)

        assertEquals(SeerrTab.Movies, tabs.selected)
        assertEquals(SeerrTab.Movies, tabs.startTab)
    }

    @Test
    fun `a cold start with discovery off shows Settings as it always has`() {
        val tabs = cold(discovery = false)

        assertEquals(SeerrTab.Settings, tabs.selected)
        assertEquals(SeerrTab.Settings, tabs.startTab)
    }

    @Test
    fun `Back from Settings goes to Movies with discovery on and Back at Movies is not claimed`() {
        val tabs = cold(discovery = true)
        compose.runOnIdle { tabs.show(SeerrTab.Settings) }

        assertTrue(compose.runOnIdle { tabs.back() })
        assertEquals(SeerrTab.Movies, tabs.selected)
        assertFalse(compose.runOnIdle { tabs.back() })
    }

    @Test
    fun `a recreate keeps the tab the user chose`() {
        lateinit var tabs: TabShellState<SeerrTab>
        val restoration = StateRestorationTester(compose)
        restoration.setContent { tabs = rememberSeerrTabs(discovery = true) }
        compose.runOnIdle { tabs.show(SeerrTab.Tv) }
        compose.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        assertEquals(SeerrTab.Tv, tabs.selected)
    }
}
