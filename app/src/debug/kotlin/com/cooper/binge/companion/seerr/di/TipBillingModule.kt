package com.cooper.binge.companion.seerr.di

import com.cooper.binge.companion.seerr.tip.DebugTipBilling
import com.cooper.binge.companion.seerr.tip.TipBilling
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** A debug build is installed over adb, where Play cannot sell anything, so a stand-in lets the screens be driven. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class TipBillingModule {
    @Binds
    abstract fun tipBilling(impl: DebugTipBilling): TipBilling
}
