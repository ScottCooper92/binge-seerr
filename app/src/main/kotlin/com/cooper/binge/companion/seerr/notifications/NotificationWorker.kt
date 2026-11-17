package com.cooper.binge.companion.seerr.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.foundation.error
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

private const val TAG = "NotificationWorker"

/**
 * The periodic poll: runs the checker and hands the outcome to the reactor. Everything with a
 * decision in it lives in those two; this only maps the outcome to WorkManager's result. A throw
 * past the checker's own guards is transient by nature, so it retries rather than fails.
 */
@HiltWorker
class NotificationWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val checker: NotificationsChecker,
        private val reactor: PollReactor,
        private val prefs: NotificationPrefs,
        private val logger: Logger,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            try {
                val result = checker.check()
                prefs.setLastRun(System.currentTimeMillis())
                when (reactor.react(result)) {
                    PollOutcome.Succeed -> Result.success()
                    PollOutcome.Retry -> Result.retry()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                logger.error(TAG, "the notification poll threw and will retry", error = e)
                Result.retry()
            }
    }
