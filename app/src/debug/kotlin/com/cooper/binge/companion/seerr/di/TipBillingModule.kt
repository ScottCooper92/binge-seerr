package com.cooper.binge.companion.seerr.di

import com.cooper.homelab.core.tip.DebugTipBilling
import com.cooper.homelab.core.tip.TipBilling
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** A debug build is installed over adb, where Play cannot sell anything, so a stand-in lets the screens be driven. */
@Module
@InstallIn(SingletonComponent::class)
internal object TipBillingModule {
    @Provides
    @Singleton
    fun tipBilling(): TipBilling = DebugTipBilling()
}
