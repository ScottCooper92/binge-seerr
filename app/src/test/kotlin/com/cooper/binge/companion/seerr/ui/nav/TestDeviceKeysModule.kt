package com.cooper.binge.companion.seerr.ui.nav

import com.cooper.binge.companion.seerr.auth.ConnectionCarrier
import com.cooper.binge.companion.seerr.auth.NoConnectionCarrier
import com.cooper.binge.companion.seerr.auth.SecretCipher
import com.cooper.binge.companion.seerr.di.DeviceKeysModule
import com.cooper.binge.companion.seerr.util.PlainCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * The device's keys, for a JVM test. The Android Keystore cannot encrypt on Robolectric, so nothing would
 * save a connection; the secret is kept in the clear instead. Nothing carries the connection to another
 * device. Everything else in the graph is the app's own.
 *
 * Hilt fails the build if this stops providing a binding [DeviceKeysModule] provides, so the two cannot
 * drift apart unnoticed.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DeviceKeysModule::class])
object TestDeviceKeysModule {
    @Provides
    @Singleton
    fun secretCipher(): SecretCipher = PlainCipher

    @Provides
    @Singleton
    fun connectionCarrier(): ConnectionCarrier = NoConnectionCarrier
}
