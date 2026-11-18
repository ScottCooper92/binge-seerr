package com.cooper.binge.companion.seerr.ui.search.title

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cooper.binge.companion.seerr.core.search.QueryHistory
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.presentation.search.DataStoreRecentSearches
import com.cooper.homelab.core.presentation.search.RecentSearches
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The preference the title searches are kept under, apart from the settings search's entries. */
internal const val RECENT_QUERIES_KEY = "recent_title_queries"

/** The queries searched on this app's one server, in the shared per-server store. The server's id is read on first use. */
internal class ServerQueryHistory(
    private val dataStore: DataStore<Preferences>,
    private val server: suspend () -> ServerId,
) : QueryHistory {
    private val lock = Mutex()
    private var store: RecentSearches? = null

    private suspend fun store(): RecentSearches =
        lock.withLock { store ?: DataStoreRecentSearches(dataStore, server(), RECENT_QUERIES_KEY).also { store = it } }

    override val recents: Flow<List<String>> = flow { emitAll(store().ids) }

    override suspend fun remember(query: String) = store().remember(query)

    override suspend fun clear() = store().clear()
}
