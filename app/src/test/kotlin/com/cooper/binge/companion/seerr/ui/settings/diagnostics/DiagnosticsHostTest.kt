package com.cooper.binge.companion.seerr.ui.settings.diagnostics

import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.homelab.core.diagnostics.Redactor
import com.cooper.homelab.core.diagnostics.RingLog
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.presentation.diagnostics.DiagnosticsState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The page's own lines: this app's version and the device, and nothing a server or a user could put there. */
class DiagnosticsHostTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `the page carries the app's version and no address`() {
        val log =
            RingLog(Clock.system(), Redactor(), serverOrder = { emptyList() }, forward = null, ownPackages = listOf("com.cooper.binge."))
        val state = DiagnosticsHost(log).model.state.value

        assertTrue(state is DiagnosticsState.Empty)
        assertTrue(state.preamble.first().contains(BuildConfig.VERSION_NAME))
        assertTrue(state.preamble.none { "http" in it })
    }
}
