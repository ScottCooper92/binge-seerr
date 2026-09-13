package io.github.scottcooper92.binge.seerr.notifications

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotificationPrefsTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `every signal is off until turned on, and turning one on drops what it had seen`() =
        runTest {
            val prefs = NotificationPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("p.preferences_pb") })
            assertFalse(prefs.anyEnabled.first())

            prefs.setCursor(NotificationSignal.PendingRequests, 4)
            prefs.setNotifiedIds(NotificationSignal.RequestApproved, setOf(1, 2))
            prefs.setEnabled(NotificationSignal.PendingRequests, true)
            assertTrue(prefs.anyEnabled.first())
            assertNull(prefs.cursor(NotificationSignal.PendingRequests))
            assertEquals(setOf(1, 2), prefs.notifiedIds(NotificationSignal.RequestApproved))

            prefs.forgetServer()
            assertNull(prefs.notifiedIds(NotificationSignal.RequestApproved))
            assertTrue(prefs.isEnabled(NotificationSignal.PendingRequests))
        }

    @Test
    fun `forgetServer also clears a pause left by a rejected credential`() =
        runTest {
            val prefs = NotificationPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("p.preferences_pb") })
            assertFalse(prefs.pausedForAuthFailure.first())

            prefs.setPausedForAuthFailure(true)
            assertTrue(prefs.pausedForAuthFailure.first())

            prefs.forgetServer()
            assertFalse(prefs.pausedForAuthFailure.first())
        }
}
