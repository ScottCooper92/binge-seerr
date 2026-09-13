package io.github.scottcooper92.binge.seerr.notifications

import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the poll's schedule in step with the connection and the toggles: scheduled while a server
 * is connected, any signal is on, and the last poll's credentials were not rejected; cancelled
 * otherwise. Keyed on the credentials themselves, so signing in again after a rejection re-arms a
 * poll the reactor paused, and a disconnect clears the notices that would open pages of a server
 * that is gone. Also keyed on [NotificationPrefs.pausedForAuthFailure], since a reconnect with the
 * same, previously-rejected credentials changes nothing the credentials comparison would see.
 */
@Singleton
class NotificationPlanner
    @Inject
    constructor(
        private val connection: SeerrConnection,
        private val prefs: NotificationPrefs,
        private val scheduler: NotificationScheduler,
        private val notifier: SeerrNotifier,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
        fun start() {
            val plans =
                combine(
                    connection.credentials,
                    prefs.anyEnabled,
                    prefs.pausedForAuthFailure,
                ) { credentials, enabled, paused ->
                    Plan(connected = credentials != null, enabled = enabled, paused = paused, credentials = credentials)
                }
            plans.distinctUntilChanged().onEach(::apply).launchIn(scope)
        }

        private fun apply(plan: Plan) {
            if (plan.connected) notifier.cancelConnectionProblem() else notifier.cancelActivity()
            if (plan.connected && plan.enabled && !plan.paused) scheduler.schedule() else scheduler.cancel()
        }

        /** [credentials] is carried so a change of server or sign-in re-plans even when nothing else moved. */
        private data class Plan(
            val connected: Boolean,
            val enabled: Boolean,
            val paused: Boolean,
            val credentials: Any?,
        )
    }
