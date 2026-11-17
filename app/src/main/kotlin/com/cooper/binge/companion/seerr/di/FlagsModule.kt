package com.cooper.binge.companion.seerr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.homelab.core.flags.DataStoreFlagOverrides
import com.cooper.homelab.core.flags.FeatureFlags
import com.cooper.homelab.core.flags.FirebaseRemoteFlagSource
import com.cooper.homelab.core.flags.FlagOverrides
import com.cooper.homelab.core.flags.NoFlagOverrides
import com.cooper.homelab.core.flags.RemoteFeatureFlags
import com.cooper.homelab.core.flags.RemoteFlagSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

private val Context.flagOverridesDataStore: DataStore<Preferences> by preferencesDataStore(name = "flag_overrides")

/** The feature flags: Remote Config's values, and in a debug build the developer's own on top. */
@Module
@InstallIn(SingletonComponent::class)
object FlagsModule {
    @Provides
    @Singleton
    fun remoteFlagSource(
        @ApplicationContext context: Context,
    ): RemoteFlagSource = FirebaseRemoteFlagSource(context)

    /** Only a debug build reads overrides, so nothing a shipped build holds can switch a flag on. */
    @Provides
    @Singleton
    fun flagOverrides(
        @ApplicationContext context: Context,
    ): FlagOverrides = if (BuildConfig.DEBUG) DataStoreFlagOverrides(context.flagOverridesDataStore) else NoFlagOverrides

    @Provides
    @Singleton
    fun featureFlags(
        source: RemoteFlagSource,
        overrides: FlagOverrides,
        @ApplicationScope scope: CoroutineScope,
        @IoDispatcher io: CoroutineDispatcher,
    ): FeatureFlags {
        // A debug build waits for its stored overrides once, so the first screen at cold start sees them. This blocks the
        // main thread, so it must stay behind BuildConfig.DEBUG and never move into a release path.
        val initial = if (BuildConfig.DEBUG) runBlocking { overrides.values.first() } else emptyMap()
        return RemoteFeatureFlags(source, overrides, scope, io, initial, seedFromCache = true)
    }
}
