package com.cooper.binge.companion.seerr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.auth.ServerIdentity
import com.cooper.binge.companion.seerr.discover.SeerrStudioSearch
import com.cooper.binge.companion.seerr.discover.StudioSearch
import com.cooper.binge.companion.seerr.ui.search.ServerRecentSearches
import com.cooper.homelab.core.presentation.search.DataStoreQueryHistory
import com.cooper.homelab.core.presentation.search.QueryHistory
import com.cooper.homelab.core.presentation.search.RecentSearches
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/** The preference the title searches are kept under, apart from the settings search's entries. An on-disk name: it never changes. */
private const val RECENT_TITLE_QUERIES_KEY = "recent_title_queries"

private val Context.searchDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "seerr_search",
    corruptionHandler = corruptionHandler(),
)

/** What the settings search keeps on the device: the entries last opened from it. */
@Module
@InstallIn(SingletonComponent::class)
object SearchModule {
    @Provides
    @Singleton
    fun recentSearches(
        @ApplicationContext context: Context,
        identity: ServerIdentity,
    ): RecentSearches = ServerRecentSearches(context.searchDataStore, identity::currentServerId)

    @Provides
    @Singleton
    fun titleSearchHistory(
        @ApplicationContext context: Context,
        identity: ServerIdentity,
    ): QueryHistory = DataStoreQueryHistory(context.searchDataStore, identity::currentServerId, RECENT_TITLE_QUERIES_KEY)

    @Provides
    @Singleton
    fun studioSearch(
        connection: SeerrConnection,
        @IoDispatcher io: CoroutineDispatcher,
    ): StudioSearch = SeerrStudioSearch(api = connection::api, profile = connection::profile, io = io)
}
