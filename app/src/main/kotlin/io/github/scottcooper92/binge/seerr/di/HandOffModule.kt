package io.github.scottcooper92.binge.seerr.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.scottcooper92.binge.seerr.handoff.AddressHandOffs
import io.github.scottcooper92.binge.seerr.handoff.AddressSender
import io.github.scottcooper92.binge.seerr.handoff.LanAddressHandOffs
import io.github.scottcooper92.binge.seerr.handoff.OkHttpAddressSender

/** The TV-to-phone address hand-off (#323): the television's listener and the phone's sender. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class HandOffModule {
    @Binds
    abstract fun handOffs(impl: LanAddressHandOffs): AddressHandOffs

    @Binds
    abstract fun sender(impl: OkHttpAddressSender): AddressSender
}
