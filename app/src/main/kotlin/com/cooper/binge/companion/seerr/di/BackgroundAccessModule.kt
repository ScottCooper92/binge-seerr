package com.cooper.binge.companion.seerr.di

import android.content.Context
import com.cooper.binge.companion.seerr.notifications.AndroidBackgroundAccess
import com.cooper.binge.companion.seerr.notifications.BackgroundAccess
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** What Android says about this app's battery restriction, which delays the notification poll. */
@Module
@InstallIn(SingletonComponent::class)
object BackgroundAccessModule {
    @Provides
    fun backgroundAccess(
        @ApplicationContext context: Context,
    ): BackgroundAccess = AndroidBackgroundAccess(context)
}
