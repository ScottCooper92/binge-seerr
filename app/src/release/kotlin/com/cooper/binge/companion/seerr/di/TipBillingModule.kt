package com.cooper.binge.companion.seerr.di

import com.cooper.binge.companion.seerr.tip.PlayTipBilling
import com.cooper.binge.companion.seerr.tip.TipBilling
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** A release build sells tips through Google Play. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TipBillingModule {
    @Binds
    abstract fun tipBilling(impl: PlayTipBilling): TipBilling
}
