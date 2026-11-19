package com.cooper.binge.companion.seerr.flags

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val DISCOVERY = booleanPreferencesKey("discovery")

/**
 * Whether the user wants the discovery surfaces. On until turned off; it only counts while the remote flag is on, which core's
 * `PreferenceGate` combines.
 */
class DiscoveryPrefs(
    private val dataStore: DataStore<Preferences>,
) {
    val enabled: Flow<Boolean> = dataStore.data.map { it[DISCOVERY] ?: true }.distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[DISCOVERY] = enabled }
    }
}
