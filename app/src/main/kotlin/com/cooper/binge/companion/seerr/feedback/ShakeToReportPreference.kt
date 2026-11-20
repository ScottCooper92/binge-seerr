package com.cooper.binge.companion.seerr.feedback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cooper.homelab.core.flags.BooleanPreference
import javax.inject.Qualifier

/** The user's shake-to-report choice, among the app's other [BooleanPreference]s. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ShakeToReportSetting

/** The name this choice has been stored under since it shipped. A different one would lose what users chose. */
internal const val SHAKE_TO_REPORT_KEY = "shake_to_report"

/** Whether a shake offers to report a bug: on until the user turns it off, as in Binge. */
fun shakeToReportPreference(dataStore: DataStore<Preferences>): BooleanPreference =
    BooleanPreference(dataStore, SHAKE_TO_REPORT_KEY, default = true)
