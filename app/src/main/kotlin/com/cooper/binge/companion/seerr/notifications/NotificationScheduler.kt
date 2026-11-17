package com.cooper.binge.companion.seerr.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.binge.companion.sdk.attempt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/** Schedules or cancels the periodic poll; seamed so the planner and the reactor are tested against a fake. */
interface NotificationScheduler {
    /** Runs the poll about every [intervalMinutes], or moves a poll already scheduled to that interval. Android may run it less often. */
    fun schedule(intervalMinutes: Int)

    fun cancel()

    /** When the next run is due, or null while nothing is scheduled. */
    fun nextRunMillis(): Flow<Long?>
}

/** The poll as unique periodic work, kept across restarts and updated in place when re-scheduled. */
class WorkManagerNotificationScheduler(
    private val context: Context,
) : NotificationScheduler {
    override fun schedule(intervalMinutes: Int) {
        val request =
            PeriodicWorkRequestBuilder<NotificationWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(NOTIFICATION_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NOTIFICATION_WORK_NAME)
    }

    /** Null as well when WorkManager cannot start on this device, which the planner records; Settings just shows no run. */
    override fun nextRunMillis(): Flow<Long?> =
        flow {
            val manager = attempt { WorkManager.getInstance(context) }.getOrNull()
            if (manager == null) {
                emit(null)
            } else {
                emitAll(
                    manager
                        .getWorkInfosForUniqueWorkFlow(NOTIFICATION_WORK_NAME)
                        .map { infos -> infos.firstOrNull { it.state == WorkInfo.State.ENQUEUED }?.nextScheduleTimeMillis },
                )
            }
        }
}
