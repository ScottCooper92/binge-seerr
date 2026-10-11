package com.cooper.binge.companion.seerr.di

import android.content.Context
import com.cooper.binge.companion.seerr.auth.BlockStoreConnectionCarrier
import com.cooper.binge.companion.seerr.auth.ConnectionCarrier
import com.cooper.binge.companion.seerr.auth.KeystoreSecretCipher
import com.cooper.binge.companion.seerr.auth.SecretCipher
import com.google.android.gms.auth.blockstore.Blockstore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
