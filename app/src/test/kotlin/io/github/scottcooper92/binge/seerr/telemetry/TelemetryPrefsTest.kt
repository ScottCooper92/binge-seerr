package io.github.scottcooper92.binge.seerr.telemetry

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TelemetryPrefsTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `analytics is undecided until answered, and an answer is kept either way`() =
        runTest {
            val prefs = TelemetryPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("t.preferences_pb") })
            assertEquals(AnalyticsConsent.UNDECIDED, prefs.analyticsConsent.first())

            prefs.setAnalyticsGranted(false)
            assertEquals(AnalyticsConsent.DENIED, prefs.analyticsConsent.first())

            prefs.setAnalyticsGranted(true)
            assertEquals(AnalyticsConsent.GRANTED, prefs.analyticsConsent.first())
        }

    @Test
    fun `crash reporting is on until turned off, whatever the analytics answer`() =
        runTest {
            val prefs = TelemetryPrefs(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("t.preferences_pb") })
            assertTrue(prefs.crashReportingEnabled.first())

            prefs.setAnalyticsGranted(false)
            assertTrue(prefs.crashReportingEnabled.first())

            prefs.setCrashReportingEnabled(false)
            assertFalse(prefs.crashReportingEnabled.first())
        }
}
