package com.cooper.binge.companion.seerr.telemetry

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey

private val analyticsGranted = booleanPreferencesKey("analytics_granted")
private val crashReportingEnabled = booleanPreferencesKey("crash_reporting_enabled")

/**
 * An install that answered the first-run question before the question covered crash reports keeps its crash reports as they were,
 * whichever way it answered. Until then an absent crash key read as on, and the default is now off (the question asks, and "Not now"
 * turns both off), so an answered install with no crash key gets the old behaviour written down: on, until the user changes it under
 * This app. Nobody who has answered is asked again.
 *
 * A DataStore migration, so it runs before the first read of the file and nothing sees the new default in between. An install that
 * never answered has no analytics key and is left alone: it is asked, and the answer writes both.
 */
internal object CrashReportingMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[analyticsGranted] != null && currentData[crashReportingEnabled] == null

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().also { it[crashReportingEnabled] = true }

    override suspend fun cleanUp() = Unit
}
