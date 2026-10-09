package io.github.scottcooper92.binge.seerr.di

import android.content.Context
import com.google.android.gms.auth.blockstore.Blockstore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.scottcooper92.binge.seerr.auth.BlockStoreConnectionCarrier
import io.github.scottcooper92.binge.seerr.auth.ConnectionCarrier
import io.github.scottcooper92.binge.seerr.auth.KeystoreSecretCipher
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import javax.inject.Singleton

/**
 * What only a real device has: the Android Keystore that encrypts the saved connection, and Play
 * Services' Block Store that carries it to a new device. A module of its own so a JVM test can
 * replace these two and run the rest of the graph as it ships.
 */
@Module
@InstallIn(SingletonComponent::class)
object DeviceKeysModule {
    @Provides
    @Singleton
    fun secretCipher(): SecretCipher = KeystoreSecretCipher()

    /**
     * The carrier is Block Store where Play Services has it. `getClient` hands one back on any
     * device; a device without Play Services fails the calls instead, which the carrier absorbs.
     */
    @Provides
    @Singleton
    fun connectionCarrier(
        @ApplicationContext context: Context,
    ): ConnectionCarrier = BlockStoreConnectionCarrier(Blockstore.getClient(context))
}
