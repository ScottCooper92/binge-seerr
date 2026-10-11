package com.cooper.binge.companion.seerr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.cooper.binge.companion.seerr.handoff.AddressHandOffs
import com.cooper.binge.companion.seerr.handoff.AddressSender
import com.cooper.binge.companion.seerr.handoff.ApplicationUrlReader
import com.cooper.binge.companion.seerr.handoff.DataStoreHandOffAddressMemory
import com.cooper.binge.companion.seerr.handoff.HandOffAddressMemory
import com.cooper.binge.companion.seerr.handoff.LanAddressHandOffs
import com.cooper.binge.companion.seerr.handoff.OkHttpAddressSender
import com.cooper.binge.companion.seerr.handoff.OkHttpTvSignInClient
import com.cooper.binge.companion.seerr.handoff.ProfileApplicationUrlReader
import com.cooper.binge.companion.seerr.handoff.TvSignInClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
