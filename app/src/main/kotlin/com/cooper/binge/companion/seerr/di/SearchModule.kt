package com.cooper.binge.companion.seerr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.cooper.binge.companion.seerr.ui.search.RecentSearches
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.searchDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_search")

/** What the settings search keeps on the device: the entries last opened from it. */
@Module
@InstallIn(SingletonComponent::class)
object SearchModule {
    @Provides
    @Singleton
    fun recentSearches(
        @ApplicationContext context: Context,
    ): RecentSearches = RecentSearches(context.searchDataStore)
}
