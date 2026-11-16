package com.cooper.binge.companion.seerr.ui.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val RECENT = stringPreferencesKey("recent_entries")

/** How many entries the empty search page remembers, as Android's own Settings does. */
internal const val MAX_RECENT = 5

private const val SEPARATOR = "\n"

/**
 * The entries the user last opened from the settings search, newest first, in a DataStore of their own on this device. What is kept is
 * the entry (see [SettingEntry.id]), never what the user typed, and nothing here is sent anywhere. An id for an entry the app no longer
 * has, or the viewer may no longer open, is simply not shown. Cleared when the connected server changes, since the entries are pages of
 * one account.
 */
class RecentSearches(
    private val dataStore: DataStore<Preferences>,
) {
    internal val ids: Flow<List<String>> =
        dataStore.data.map { it[RECENT].orEmpty().split(SEPARATOR).filter(String::isNotBlank) }.distinctUntilChanged()

    /** [id] becomes the newest; one already there moves up rather than twice, and only the last [MAX_RECENT] stay. */
    internal suspend fun remember(id: String) {
        if (id.isBlank()) return
        dataStore.edit { prefs ->
            val kept = prefs[RECENT].orEmpty().split(SEPARATOR).filter { it.isNotBlank() && it != id }
            prefs[RECENT] = (listOf(id) + kept).take(MAX_RECENT).joinToString(SEPARATOR)
        }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(RECENT) }
    }
}
