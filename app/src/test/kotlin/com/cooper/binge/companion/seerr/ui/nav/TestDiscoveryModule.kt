package com.cooper.binge.companion.seerr.ui.nav

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.di.DiscoveryModule
import com.cooper.binge.companion.seerr.flags.DiscoveryEnabled
import com.cooper.binge.companion.seerr.flags.DiscoveryGate
import com.cooper.binge.companion.seerr.flags.DiscoveryPrefs
import com.cooper.binge.companion.seerr.flags.PrefsDiscoveryGate
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.homelab.core.flags.FixedFeatureFlags
import com.cooper.homelab.core.flags.Flags
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import java.io.File
import javax.inject.Singleton

/**
 * The real [PrefsDiscoveryGate] and the real preference, with the remote flag fixed to [NavHostActivity.discoveryEnabled] as the test
 * set it before the first read, and the preference kept in a file of its own, so a journey flips it through the screens as a user does.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DiscoveryModule::class])
object TestDiscoveryModule {
    @Provides
    @Singleton
    fun discoveryGate(
        @ApplicationScope scope: CoroutineScope,
    ): DiscoveryGate {
        val file = File.createTempFile("discovery", ".preferences_pb").also { it.delete() }
        val prefs = DiscoveryPrefs(PreferenceDataStoreFactory.create(scope = scope) { file })
        return PrefsDiscoveryGate(FixedFeatureFlags(Flags.of(DiscoveryEnabled to NavHostActivity.discoveryEnabled)), prefs, scope)
    }
}
