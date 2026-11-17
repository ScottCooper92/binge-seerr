package com.cooper.binge.companion.seerr.ui.shell

import com.cooper.binge.companion.seerr.ui.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class ShowsTabsTest {
    private fun shows(
        enabled: Boolean = true,
        compact: Boolean = true,
        state: HomeUiState = HomeUiState.Connected,
    ) = showsTabs(discoveryEnabled = enabled, compact = compact, state = state)

    @Test
    fun `a connected phone with the flag on gets the tabs`() {
        assertEquals(true, shows())
    }

    @Test
    fun `with the flag off the app is as it always was`() {
        assertEquals(false, shows(enabled = false))
    }

    @Test
    fun `a window wide enough for panes keeps its panes even with the flag on`() {
        assertEquals(false, shows(compact = false))
    }

    @Test
    fun `setup and a rejected sign-in take the whole window`() {
        listOf(
            HomeUiState.Resolving,
            HomeUiState.Setup,
            HomeUiState.Reconnect,
        ).forEach { assertEquals(it.toString(), false, shows(state = it)) }
    }

    @Test
    fun `a disconnect still waiting on the server keeps the tabs until it lands`() {
        assertEquals(true, shows(state = HomeUiState.Disconnecting))
    }
}
