package com.cooper.binge.companion.seerr.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.notifications.runPollWork
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * The periodic poll: runs one [NotificationPoll] and maps its outcome to WorkManager's result. A throw past the
 * checker's own guards is transient by nature: it is logged and the run ends as a success, so the next check is one
 * interval away and WorkManager applies no backoff. The class name is the one WorkManager
 * stored the work under, so it never moves.
 */
@HiltWorker
class NotificationWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val poll: NotificationPoll,
        private val logger: Logger,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result = runPollWork(logger) { poll.run().toShared() }
    }
