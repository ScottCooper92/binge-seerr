package io.github.scottcooper92.binge.seerr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.scottcooper92.binge.seerr.handoff.AddressHandOffs
import io.github.scottcooper92.binge.seerr.handoff.AddressSender
import io.github.scottcooper92.binge.seerr.handoff.ApplicationUrlReader
import io.github.scottcooper92.binge.seerr.handoff.DataStoreHandOffAddressMemory
import io.github.scottcooper92.binge.seerr.handoff.HandOffAddressMemory
import io.github.scottcooper92.binge.seerr.handoff.LanAddressHandOffs
import io.github.scottcooper92.binge.seerr.handoff.OkHttpAddressSender
import io.github.scottcooper92.binge.seerr.handoff.OkHttpTvSignInClient
import io.github.scottcooper92.binge.seerr.handoff.ProfileApplicationUrlReader
import io.github.scottcooper92.binge.seerr.handoff.TvSignInClient
import javax.inject.Singleton

private val Context.handOffDataStore: DataStore<Preferences> by preferencesDataStore(name = "tv_handoff")

/**
 * The TV-to-phone address hand-off (#323): the television's listener, and the phone's sender with
 * what it offers to send.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class HandOffModule {
    @Binds
    abstract fun handOffs(impl: LanAddressHandOffs): AddressHandOffs

    @Binds
    abstract fun sender(impl: OkHttpAddressSender): AddressSender

    @Binds
    abstract fun signIn(impl: OkHttpTvSignInClient): TvSignInClient

    @Binds
    abstract fun applicationUrl(impl: ProfileApplicationUrlReader): ApplicationUrlReader

    companion object {
        @Provides
        @Singleton
        fun addressMemory(
            @ApplicationContext context: Context,
        ): HandOffAddressMemory = DataStoreHandOffAddressMemory(context.handOffDataStore)
    }
}
