package io.github.scottcooper92.binge.seerr.ui.nav

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.scottcooper92.binge.seerr.auth.ConnectionCarrier
import io.github.scottcooper92.binge.seerr.auth.NoConnectionCarrier
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.di.DeviceKeysModule
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
    fun secretCipher(): SecretCipher =
        object : SecretCipher {
            override fun encrypt(plaintext: String): String = plaintext

            override fun decrypt(ciphertext: String): String = ciphertext
        }

    @Provides
    @Singleton
    fun connectionCarrier(): ConnectionCarrier = NoConnectionCarrier
}
