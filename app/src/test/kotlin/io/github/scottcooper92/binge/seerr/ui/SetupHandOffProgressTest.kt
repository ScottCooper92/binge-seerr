package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.handoff.HandOffProgress
import io.github.scottcooper92.binge.seerr.handoff.HandOffStatus
import io.github.scottcooper92.binge.seerr.handoff.toStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** What the phone's page hears about an address that waits on the TV: its plain-HTTP opt-in (#907), or its user going on (#1084). */
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
    fun `a typed address, or one being read, waits on nothing on the TV`() {
        assertEquals(
            HandOffProgress.Waiting,
            publicHttp.copy(received = false, cleartextAllowed = true).toHandOffProgress(received = false, failed = false, attempts = 0),
        )
        assertFalse(publicHttp.copy(received = false).awaitingCleartextConsent)
        assertEquals(
            HandOffProgress.Checking,
            publicHttp.copy(isInspecting = true).toHandOffProgress(received = true, failed = false, attempts = 0),
        )
    }

    /** #1084: any address a phone sent waits for the TV's user, not only a plain-HTTP one. */
    @Test
    fun `a received address waits on the TV until it is read, and a failed one offers the form again`() {
        val lan = publicHttp.copy(serverUrl = "http://192.168.1.10:5055", insecure = false)
        assertEquals(HandOffProgress.ConfirmOnTv, lan.toHandOffProgress(received = true, failed = false, attempts = 0))
        val failed = lan.copy(error = SetupError.Unreachable)
        assertFalse(failed.awaitingConfirm)
        assertEquals(HandOffProgress.Failed, failed.toHandOffProgress(received = true, failed = true, attempts = 0))
    }
}
