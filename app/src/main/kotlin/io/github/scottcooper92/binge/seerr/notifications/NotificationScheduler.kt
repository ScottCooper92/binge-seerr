package io.github.scottcooper92.binge.seerr.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** WorkManager's floor for periodic work; Doze stretches it further. */
private const val POLL_INTERVAL_MINUTES = 15L
private const val WORK_NAME = "seerr_notifications"

/** Schedules or cancels the periodic poll; seamed so the planner and the reactor are tested against a fake. */
interface NotificationScheduler {
    fun schedule()

    fun cancel()
}

/** The poll as unique periodic work, kept across restarts and updated in place when re-scheduled. */
class WorkManagerNotificationScheduler(
    private val context: Context,
) : NotificationScheduler {
    override fun schedule() {
        val request =
            PeriodicWorkRequestBuilder<NotificationWorker>(POLL_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
