package io.github.scottcooper92.binge.seerr.notifications

import javax.inject.Inject

/** Whether WorkManager should count the run done, or schedule a backed-off retry. */
enum class PollOutcome { Succeed, Retry }

/**
 * What one poll's outcome does, kept out of the worker so it is testable without a Context. A
 * rejected credential cannot be retried into success, so the user is asked to sign in again and
 * the poll is paused until the planner sees new credentials.
 */
class PollReactor
    @Inject
    constructor(
        private val notifier: SeerrNotifier,
        private val scheduler: NotificationScheduler,
    ) {
        fun react(result: CheckResult): PollOutcome =
            when (result) {
                CheckResult.Ok -> PollOutcome.Succeed
                CheckResult.TransientFailure -> PollOutcome.Retry
                CheckResult.AuthFailure -> {
                    notifier.notifyConnectionProblem()
                    scheduler.cancel()
                    PollOutcome.Succeed
                }
            }
    }
