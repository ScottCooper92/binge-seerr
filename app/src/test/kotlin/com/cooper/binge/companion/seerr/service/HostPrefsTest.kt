package com.cooper.binge.companion.seerr.service

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HostPrefsTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `available notifications are off until turned on`() =
        runTest {
            val prefs = HostPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("h.preferences_pb") })
            assertFalse(prefs.availableNotifications.first())
            assertFalse(prefs.isAvailableNotificationsEnabled())

            prefs.setAvailableNotifications(true)
            assertTrue(prefs.availableNotifications.first())

            prefs.setAvailableNotifications(false)
            assertFalse(prefs.isAvailableNotificationsEnabled())
        }
}
