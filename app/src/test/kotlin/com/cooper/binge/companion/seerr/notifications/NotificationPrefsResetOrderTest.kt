package com.cooper.binge.companion.seerr.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Turning a signal on drops its shared cursor first and commits the toggle after, so a poll that runs in between sees the
 * signal off and skips it, instead of finding the old cursor and announcing what arrived while it was off.
 */
class NotificationPrefsResetOrderTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** Remembers whether the signal was still off at the moment its cursor was reset. */
    private class OrderRecordingReset : CursorReset {
        lateinit var prefs: NotificationPrefs
        val stillOffAtReset = mutableListOf<Pair<NotificationSignal, Boolean>>()

        override suspend fun reset(signal: NotificationSignal) {
            stillOffAtReset += signal to !prefs.isEnabled(signal)
        }

        override suspend fun resetAll() = Unit
    }

    private fun TestScope.prefs(reset: OrderRecordingReset): NotificationPrefs =
        NotificationPrefs(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("o.preferences_pb") },
            reset,
        ).also { reset.prefs = it }

    @Test
    fun `a signal is still off when its cursor is reset, and on afterwards`() =
        runTest {
            val reset = OrderRecordingReset()
            val prefs = prefs(reset)

            prefs.setEnabled(NotificationSignal.PendingRequests, true)

            assertEquals(listOf(NotificationSignal.PendingRequests to true), reset.stillOffAtReset)
            assertTrue(prefs.isEnabled(NotificationSignal.PendingRequests))
        }

    @Test
    fun `turning a signal off resets nothing`() =
        runTest {
            val reset = OrderRecordingReset()
            val prefs = prefs(reset)
            prefs.setEnabled(NotificationSignal.PendingRequests, true)
            reset.stillOffAtReset.clear()

            prefs.setEnabled(NotificationSignal.PendingRequests, false)

            assertEquals(emptyList<Pair<NotificationSignal, Boolean>>(), reset.stillOffAtReset)
            assertFalse(prefs.isEnabled(NotificationSignal.PendingRequests))
        }

    @Test
    fun `enabling the defaults resets their cursors while they are still off, and only when none is on`() =
        runTest {
            val reset = OrderRecordingReset()
            val prefs = prefs(reset)
            val defaults = listOf(NotificationSignal.PendingRequests, NotificationSignal.OpenIssues)

            prefs.enableIfNoneOn(defaults)

            assertEquals(defaults.map { it to true }, reset.stillOffAtReset)
            defaults.forEach { assertTrue(prefs.isEnabled(it)) }

            reset.stillOffAtReset.clear()
            prefs.enableIfNoneOn(listOf(NotificationSignal.RequestApproved))

            assertEquals(emptyList<Pair<NotificationSignal, Boolean>>(), reset.stillOffAtReset)
            assertFalse(prefs.isEnabled(NotificationSignal.RequestApproved))
        }
}
