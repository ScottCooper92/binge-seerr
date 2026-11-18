package com.cooper.binge.companion.seerr.telemetry

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.cooper.homelab.core.telemetry.AnalyticsConsent
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * What a released version wrote, opened by this one: an install that answered is not asked again and keeps crash reports as they were,
 * whichever way it answered, and one that never answered is asked, with crash reports off until it agrees.
 */
class CrashReportingMigrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val analytics = booleanPreferencesKey("analytics_granted")
    private val crashes = booleanPreferencesKey("crash_reporting_enabled")

    private fun TestScope.open(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope, migrations = listOf(CrashReportingMigration)) { file }

    private fun TestScope.prefs(file: File) = TelemetryPrefs(open(file), crashReportingDefault = false)

    /** The file as the previous version left it: only the keys it wrote. */
    private suspend fun TestScope.previousVersion(write: (androidx.datastore.preferences.core.MutablePreferences) -> Unit): File {
        val file = folder.newFile("t.preferences_pb").also { it.delete() }
        val released = CoroutineScope(Job())
        PreferenceDataStoreFactory.create(scope = released) { file }.edit(write)
        released.cancel()
        released.coroutineContext.job.join()
        return file
    }

    @Test
    fun `an install that agreed keeps usage data and crash reports on and is not asked`() =
        runTest {
            val prefs = prefs(previousVersion { it[analytics] = true })

            assertEquals(AnalyticsConsent.GRANTED, prefs.analyticsConsent.first())
            assertEquals(true, prefs.crashReportingEnabled.first())
        }

    @Test
    fun `an install that said not now keeps crash reports on, as it has, and is not asked`() =
        runTest {
            val prefs = prefs(previousVersion { it[analytics] = false })

            assertEquals(AnalyticsConsent.DENIED, prefs.analyticsConsent.first())
            assertEquals(true, prefs.crashReportingEnabled.first())
        }

    @Test
    fun `an install that turned crash reports off keeps them off`() =
        runTest {
            val prefs =
                prefs(
                    previousVersion {
                        it[analytics] = true
                        it[crashes] = false
                    },
                )

            assertEquals(false, prefs.crashReportingEnabled.first())
        }

    @Test
    fun `an install that never answered is asked, with crash reports off`() =
        runTest {
            val prefs = prefs(previousVersion { })

            assertEquals(AnalyticsConsent.UNDECIDED, prefs.analyticsConsent.first())
            assertEquals(false, prefs.crashReportingEnabled.first())
        }

    @Test
    fun `the migrated crash choice can be changed under This app afterwards`() =
        runTest {
            val file = previousVersion { it[analytics] = false }
            val first = prefs(file)
            first.crashReportingEnabled.first()

            first.setCrashReportingEnabled(false)

            assertEquals(false, first.crashReportingEnabled.first())
        }
}
