package com.cooper.binge.companion.seerr.di

import android.util.Log
import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.telemetry.LogcatLogger
import com.cooper.binge.companion.seerr.telemetry.followCredentials
import com.cooper.homelab.core.diagnostics.Redactor
import com.cooper.homelab.core.diagnostics.RingLog
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.foundation.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

private const val REDACTOR_TAG = "Redactor"

/**
 * The shared [Logger]: an in-memory [RingLog] that the Diagnostics page reads. Nothing is written to disk. A debug build also
 * forwards each redacted entry to Logcat, and says so when the redactor had to replace something (a call site is wrong then).
 */
@Module
@InstallIn(SingletonComponent::class)
object LoggingModule {
    @Provides
    @Singleton
    fun redactor(
        store: CredentialStore,
        @ApplicationScope scope: CoroutineScope,
    ): Redactor =
        Redactor(onRedaction = { if (BuildConfig.DEBUG) Log.w(REDACTOR_TAG, "A log entry held a registered literal") })
            .also { it.followCredentials(store.credentials, scope) }

    @Provides
    @Singleton
    fun ringLog(redactor: Redactor): RingLog =
        RingLog(
            clock = Clock.system(),
            redactor = redactor,
            // One saved server: no entry carries a server field today, so there is no order to label.
            serverOrder = { emptyList() },
            forward = LogcatLogger.takeIf { BuildConfig.DEBUG },
            // The frames kept in a logged throwable are this app's own, named here so the library holds no default.
            ownPackages = listOf("com.cooper.binge."),
        )

    @Provides
    @Singleton
    fun logger(ring: RingLog): Logger = ring
}
