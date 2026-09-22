package io.github.scottcooper92.binge.seerr

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.scottcooper92.binge.seerr.auth.ConnectionRestore
import io.github.scottcooper92.binge.seerr.notifications.NotificationPlanner
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.CrashReportingSwitch
import javax.inject.Inject

/**
 * The object graph is Hilt's, assembled from [io.github.scottcooper92.binge.seerr.di.SeerrModule].
 * A companion this size did not earn a DI framework; a client that manages the server it connects
 * to (#26) does, and the screens it inherits from Binge are shaped for one. WorkManager takes its
 * workers from the same graph, and the notification poll is planned from here for the app's life.
 */
@HiltAndroidApp
class SeerrApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var planner: NotificationPlanner

    @Inject
    lateinit var connectionRestore: ConnectionRestore

    @Inject
    lateinit var crashReporting: CrashReportingSwitch

    /** Injected so it is built at start: building it is what registers it with the consent gate. */
    @Inject
    lateinit var analytics: Analytics

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        crashReporting.start()
        connectionRestore.start()
        planner.start()
    }
}
