package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.handoff.HandOffProgress
import io.github.scottcooper92.binge.seerr.handoff.HandOffStatus
import io.github.scottcooper92.binge.seerr.handoff.toStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** What the phone's page hears about an address that waits on the TV's plain-HTTP opt-in (#907). */
class SetupHandOffProgressTest {
    private val publicHttp =
        SetupUiState.Address(serverUrl = "http://example.com:5055", insecure = true, isInspecting = false, error = null, received = true)

    @Test
    fun `a received public plain-HTTP address asks the page to confirm on the TV`() {
        assertEquals(HandOffProgress.ConfirmOnTv, publicHttp.toHandOffProgress(received = true, failed = false, attempts = 0))
        assertEquals(HandOffStatus.CONFIRM, HandOffProgress.ConfirmOnTv.toStatus().state)
        assertFalse("the page offers no form while the TV decides", HandOffProgress.ConfirmOnTv.acceptsAddress)
    }

    @Test
    fun `once the user agrees, or for a typed address, nothing waits on the TV`() {
        assertEquals(
            HandOffProgress.Waiting,
            publicHttp.copy(cleartextAllowed = true).toHandOffProgress(received = true, failed = false, attempts = 0),
        )
        assertFalse(publicHttp.copy(received = false).awaitingCleartextConsent)
        assertEquals(
            HandOffProgress.Checking,
            publicHttp.copy(isInspecting = true).toHandOffProgress(received = true, failed = false, attempts = 0),
        )
    }
}
