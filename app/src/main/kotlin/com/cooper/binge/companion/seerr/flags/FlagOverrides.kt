package com.cooper.binge.companion.seerr.flags

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/** Local answers that win over the remote ones, for a developer trying a flag on a device. */
interface FlagOverrides {
    /** A flag with no entry follows the remote value. */
    val values: Flow<Map<Flag, Boolean>>

    /** Null clears the override. */
    suspend fun set(
        flag: Flag,
        value: Boolean?,
    )
}

/** No overrides, and nowhere to put one: what a release build has. */
object NoFlagOverrides : FlagOverrides {
    override val values: Flow<Map<Flag, Boolean>> = emptyFlow()

    override suspend fun set(
        flag: Flag,
        value: Boolean?,
    ) = Unit
}

class DataStoreFlagOverrides(
    private val dataStore: DataStore<Preferences>,
) : FlagOverrides {
    override val values: Flow<Map<Flag, Boolean>> =
        dataStore.data
            .map { prefs -> Flag.entries.mapNotNull { flag -> prefs[key(flag)]?.let { flag to it } }.toMap() }
            .distinctUntilChanged()

    override suspend fun set(
        flag: Flag,
        value: Boolean?,
    ) {
        dataStore.edit { if (value == null) it.remove(key(flag)) else it[key(flag)] = value }
    }

    private fun key(flag: Flag) = booleanPreferencesKey("override_${flag.key}")
}
