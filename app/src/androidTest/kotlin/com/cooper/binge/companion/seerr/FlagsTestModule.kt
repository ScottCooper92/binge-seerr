package com.cooper.binge.companion.seerr

import com.cooper.binge.companion.seerr.di.FlagsModule
import com.cooper.binge.companion.seerr.flags.FeatureFlags
import com.cooper.binge.companion.seerr.flags.FixedFeatureFlags
import com.cooper.binge.companion.seerr.flags.FlagOverrides
import com.cooper.binge.companion.seerr.flags.Flags
import com.cooper.binge.companion.seerr.flags.NoFlagOverrides
import com.cooper.binge.companion.seerr.flags.NoRemoteFlagSource
import com.cooper.binge.companion.seerr.flags.RemoteFlagSource
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * The device lane has no Firebase app, so the real flag would be off. The lane runs with it on, so the Binge prompts
 * it reaches are the ones a release shows.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [FlagsModule::class])
object FlagsTestModule {
    @Provides
    @Singleton
    fun remoteFlagSource(): RemoteFlagSource = NoRemoteFlagSource

    @Provides
    @Singleton
    fun flagOverrides(): FlagOverrides = NoFlagOverrides

    @Provides
    @Singleton
    fun featureFlags(): FeatureFlags = FixedFeatureFlags(Flags.AllOn)
}
