package io.github.scottcooper92.binge.seerr.di

import android.content.Context
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.scottcooper92.binge.seerr.BuildConfig
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsClient
import io.github.scottcooper92.binge.seerr.telemetry.CrashCollection
import io.github.scottcooper92.binge.seerr.telemetry.FirebaseCrashCollection
import io.github.scottcooper92.binge.seerr.telemetry.PostHogAnalytics
import io.github.scottcooper92.binge.seerr.telemetry.PostHogClient
import io.github.scottcooper92.binge.seerr.telemetry.postHogConfig
import javax.inject.Singleton

/** Analytics and crash reporting, each off in a build that was not given this app's own project. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {
    @Binds
    abstract fun analytics(impl: PostHogAnalytics): Analytics

    @Binds
    abstract fun crashCollection(impl: FirebaseCrashCollection): CrashCollection

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
