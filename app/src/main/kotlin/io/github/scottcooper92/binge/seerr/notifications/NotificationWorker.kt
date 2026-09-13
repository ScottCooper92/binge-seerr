package io.github.scottcooper92.binge.seerr.notifications

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            try {
                when (reactor.react(checker.check())) {
                    PollOutcome.Succeed -> Result.success()
                    PollOutcome.Retry -> Result.retry()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.e(TAG, "The notification poll threw; retrying", e)
                Result.retry()
            }
    }
