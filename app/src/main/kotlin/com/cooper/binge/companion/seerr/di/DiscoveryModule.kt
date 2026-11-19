package com.cooper.binge.companion.seerr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.cooper.binge.companion.seerr.flags.DiscoveryEnabled
import com.cooper.binge.companion.seerr.flags.DiscoveryPrefs
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.homelab.core.flags.FeatureFlags
import com.cooper.homelab.core.flags.FlagPreferenceGate
import com.cooper.homelab.core.flags.PreferenceGate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

private val Context.discoveryDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_discovery")

/**
 * Discovery is the remote flag and the user's own preference, combined by core's shared gate; every reader goes through it.
 * Its own module, so a journey can swap it.
 */
@Module
@InstallIn(SingletonComponent::class)
object DiscoveryModule {
    @Provides
    @Singleton
    fun discoveryGate(
        @ApplicationContext context: Context,
        featureFlags: FeatureFlags,
        @ApplicationScope scope: CoroutineScope,
    ): PreferenceGate {
        val prefs = DiscoveryPrefs(context.discoveryDataStore)
        return FlagPreferenceGate(featureFlags, DiscoveryEnabled, prefs.enabled, prefs::setEnabled, scope)
    }
}
