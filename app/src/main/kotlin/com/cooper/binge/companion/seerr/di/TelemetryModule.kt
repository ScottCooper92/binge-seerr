package com.cooper.binge.companion.seerr.di

import android.content.Context
import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.binge.companion.seerr.telemetry.Analytics
import com.cooper.binge.companion.seerr.telemetry.AnalyticsClient
import com.cooper.binge.companion.seerr.telemetry.CrashBreadcrumbs
import com.cooper.binge.companion.seerr.telemetry.CrashCollection
import com.cooper.binge.companion.seerr.telemetry.FirebaseCrashBreadcrumbs
import com.cooper.binge.companion.seerr.telemetry.FirebaseCrashCollection
import com.cooper.binge.companion.seerr.telemetry.PostHogAnalytics
import com.cooper.binge.companion.seerr.telemetry.PostHogClient
import com.cooper.binge.companion.seerr.telemetry.postHogConfig
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Analytics and crash reporting, each off in a build that was not given this app's own project. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {
    @Binds
    abstract fun analytics(impl: PostHogAnalytics): Analytics

    @Binds
    abstract fun crashCollection(impl: FirebaseCrashCollection): CrashCollection

    @Binds
    abstract fun crashBreadcrumbs(impl: FirebaseCrashBreadcrumbs): CrashBreadcrumbs

    companion object {
        /** A blank key means no PostHog project was built in: no config, no client, and no SDK started. */
        @Provides
        @Singleton
        fun postHogConfig(): PostHogAndroidConfig? =
            BuildConfig.POSTHOG_API_KEY.takeIf { it.isNotBlank() }?.let { postHogConfig(it, BuildConfig.POSTHOG_HOST) }

        @Provides
        @Singleton
        fun postHog(
            @ApplicationContext context: Context,
            config: PostHogAndroidConfig?,
        ): AnalyticsClient? = config?.let { PostHogClient(PostHogAndroid.with(context, it)) }
    }
}
