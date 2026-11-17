package com.cooper.binge.companion.seerr.di

import android.content.Context
import com.cooper.homelab.core.tip.PlayTipBilling
import com.cooper.homelab.core.tip.TipBilling
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** A release build sells tips through Google Play. */
@Module
@InstallIn(SingletonComponent::class)
internal object TipBillingModule {
    @Provides
    @Singleton
    fun tipBilling(
        @ApplicationContext context: Context,
    ): TipBilling = PlayTipBilling(context)
}
