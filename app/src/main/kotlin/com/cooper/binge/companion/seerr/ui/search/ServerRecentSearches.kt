package com.cooper.binge.companion.seerr.ui.search

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.presentation.search.DataStoreRecentSearches
import com.cooper.homelab.core.presentation.search.RecentSearches
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The preference the recents have always been kept under: the entry ids, one to a line, newest first. */
internal const val RECENT_ENTRIES_KEY = "recent_entries"

/**
 * The recents of this app's one server, in the shared library's per-server store. The server's id is read on first use, because
 * it is a DataStore read, and the list a released version kept under the bare key ([RECENT_ENTRIES_KEY]) is copied to the server's
 * key once, so what the user last opened is still offered. The old key is left where it is.
 */
internal class ServerRecentSearches(
    private val dataStore: DataStore<Preferences>,
    private val server: suspend () -> ServerId,
) : RecentSearches {
    private val lock = Mutex()
    private var store: RecentSearches? = null

    private suspend fun store(): RecentSearches =
        lock.withLock {
            store ?: open().also { store = it }
        }

    private suspend fun open(): RecentSearches {
        val id = server()
        val old = stringPreferencesKey(RECENT_ENTRIES_KEY)
        val current = stringPreferencesKey("$RECENT_ENTRIES_KEY.${id.value}")
        dataStore.edit { prefs -> if (prefs[current] == null) prefs[old]?.let { prefs[current] = it } }
        return DataStoreRecentSearches(dataStore, id, RECENT_ENTRIES_KEY)
    }

    override val ids: Flow<List<String>> = flow { emitAll(store().ids) }

    override suspend fun remember(id: String) = store().remember(id)

    override suspend fun clear() = store().clear()
}
