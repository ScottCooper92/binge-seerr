package com.cooper.binge.companion.seerr.flags

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** The key of [SearchTogglesFlag], which names it on the developer flags screen and in the override store. */
const val SEARCH_TOGGLES_FLAG_KEY = "search_toggles_enabled"

/**
 * Whether a settings search result for an on/off setting carries its own switch. Off unless a debug build's developer override
 * turns it on: it has no remote value, so a shipped build never shows the switches.
 */
interface SearchTogglesFlag {
    val enabled: StateFlow<Boolean>
}

/** A local answer for a developer trying the switches on a device. */
interface SearchTogglesOverride {
    /** Null leaves the flag off. */
    val value: Flow<Boolean?>

    /** Null clears the override. */
    suspend fun set(value: Boolean?)
}

/** What a release build has: no override, and nowhere to put one. */
object NoSearchTogglesOverride : SearchTogglesOverride {
    override val value: Flow<Boolean?> = emptyFlow()

    override suspend fun set(value: Boolean?) = Unit
}

/** The override kept in the same DataStore as the other flag overrides, under the same `override_` prefix. */
class DataStoreSearchTogglesOverride(
    private val dataStore: DataStore<Preferences>,
) : SearchTogglesOverride {
    private val key = booleanPreferencesKey("override_$SEARCH_TOGGLES_FLAG_KEY")

    override val value: Flow<Boolean?> = dataStore.data.map { it[key] }.distinctUntilChanged()

    override suspend fun set(value: Boolean?) {
        dataStore.edit { if (value == null) it.remove(key) else it[key] = value }
    }
}

/**
 * [SearchTogglesFlag] over [override]. [initialOverride] is what the override held at start, so a read right after a cold start
 * does not lose a race with the store.
 */
class LocalSearchTogglesFlag(
    override: SearchTogglesOverride,
    scope: CoroutineScope,
    initialOverride: Boolean? = null,
) : SearchTogglesFlag {
    override val enabled: StateFlow<Boolean> =
        override.value
            .onStart { emit(initialOverride) }
            .map { it ?: false }
            .stateIn(scope, SharingStarted.Eagerly, initialOverride ?: false)
}
