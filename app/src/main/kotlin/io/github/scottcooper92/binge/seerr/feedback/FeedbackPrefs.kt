package io.github.scottcooper92.binge.seerr.feedback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val SHAKE_TO_REPORT = booleanPreferencesKey("shake_to_report")

/** Whether a shake offers to report a bug. On until turned off, as in Binge. */
class FeedbackPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    val shakeToReport: Flow<Boolean> = dataStore.data.map { it[SHAKE_TO_REPORT] ?: true }.distinctUntilChanged()

    suspend fun setShakeToReport(enabled: Boolean) {
        dataStore.edit { it[SHAKE_TO_REPORT] = enabled }
    }
}
