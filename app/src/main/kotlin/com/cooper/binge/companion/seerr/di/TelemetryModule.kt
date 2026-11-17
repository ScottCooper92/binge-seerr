package com.cooper.binge.companion.seerr.di

import android.content.Context
import android.util.Log
import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.telemetry.DemoAnalyticsDecorator
import com.cooper.binge.companion.seerr.telemetry.DemoSession
import com.cooper.binge.companion.seerr.telemetry.SavedConnectionDemoSession
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.AnalyticsClient
import com.cooper.homelab.core.telemetry.AnalyticsConsentGate
import com.cooper.homelab.core.telemetry.AnalyticsDecorator
import com.cooper.homelab.core.telemetry.CrashBreadcrumbs
import com.cooper.homelab.core.telemetry.CrashCollection
import com.cooper.homelab.core.telemetry.CrashReportingSwitch
import com.cooper.homelab.core.telemetry.FirebaseCrashBreadcrumbs
import com.cooper.homelab.core.telemetry.FirebaseCrashCollection
import com.cooper.homelab.core.telemetry.GatedAnalytics
import com.cooper.homelab.core.telemetry.PostHogClient
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import com.cooper.homelab.core.telemetry.postHogConfig
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/** Analytics and crash reporting, each off in a build that was not given this app's own project. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TelemetryModule {
    @Binds
    abstract fun demoSession(impl: SavedConnectionDemoSession): DemoSession

    companion object {
        /** The one owner of "may analytics report"; every backend registers with it. */
        @Provides
        @Singleton
        fun consentGate(
            prefs: TelemetryPrefs,
            @ApplicationScope scope: CoroutineScope,
        ): AnalyticsConsentGate = AnalyticsConsentGate(prefs, scope, Logger { tag, message, cause -> Log.e(tag, message, cause) })

        @Provides
        @Singleton
        fun analyticsDecorator(demo: DemoSession): AnalyticsDecorator = DemoAnalyticsDecorator(demo)

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

        @Provides
        @Singleton
        fun analytics(
            client: AnalyticsClient?,
            gate: AnalyticsConsentGate,
            decorator: AnalyticsDecorator,
            @ApplicationScope scope: CoroutineScope,
        ): Analytics = GatedAnalytics(client, gate, decorator, scope)

        @Provides
        @Singleton
        fun crashCollection(
            @ApplicationContext context: Context,
        ): CrashCollection = FirebaseCrashCollection(context)

        @Provides
        @Singleton
        fun crashBreadcrumbs(
            @ApplicationContext context: Context,
        ): CrashBreadcrumbs = FirebaseCrashBreadcrumbs(context)

        @Provides
        @Singleton
        fun crashReportingSwitch(
            collection: CrashCollection,
            prefs: TelemetryPrefs,
            @ApplicationScope scope: CoroutineScope,
        ): CrashReportingSwitch = CrashReportingSwitch(collection, prefs, scope, isDebugBuild = BuildConfig.DEBUG)
    }
}
