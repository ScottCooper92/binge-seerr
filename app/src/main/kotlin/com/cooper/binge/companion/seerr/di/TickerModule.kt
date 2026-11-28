package com.cooper.binge.companion.seerr.di

import com.cooper.binge.companion.seerr.ui.Ticker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The wait the polling view models share: a real delay, which a test replaces with one that ends. */
@Module
@InstallIn(SingletonComponent::class)
object TickerModule {
    @Provides
    fun ticker(): Ticker = Ticker()
}
