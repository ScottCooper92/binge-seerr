package com.cooper.binge.companion.seerr.di

import android.content.Context
import com.cooper.binge.companion.seerr.ui.settings.DataStoreWatchPlayerSetting
import com.cooper.binge.companion.seerr.ui.settings.WatchPlayerSetting
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Where Watch plays a title, kept in the store of the app's other own choices. */
@Module
@InstallIn(SingletonComponent::class)
object WatchPlayerModule {
    @Provides
    @Singleton
    fun watchPlayerSetting(
        @ApplicationContext context: Context,
    ): WatchPlayerSetting = DataStoreWatchPlayerSetting(context.feedbackDataStore)
}
