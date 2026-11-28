package com.cooper.binge.companion.seerr.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.auth.ServerIdentity
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.notifications.FeedCursorKey
import com.cooper.homelab.core.notifications.FeedCursorStore
import com.cooper.homelab.core.notifications.PollNotifier
import com.cooper.homelab.core.notifications.PollPauseStore
import com.cooper.homelab.core.notifications.PollPlan
import com.cooper.homelab.core.notifications.PollPlanner
import com.cooper.homelab.core.notifications.PollScheduler
import com.cooper.homelab.core.notifications.ServerPollPlan
import com.cooper.homelab.core.telemetry.CrashBreadcrumbs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Qualifier
import com.cooper.homelab.core.notifications.CheckResult as SharedCheckResult
import com.cooper.homelab.core.notifications.PollOutcome as SharedPollOutcome
import com.cooper.homelab.core.notifications.PollReactor as SharedPollReactor

/** Marks the one boolean that says whether the poll runs on the shared library's reactor, planner, scheduler and cursor store. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SharedPollingEnabled

/** The preference key in the debug overrides file that turns the shared polling off. A shipped build never reads it. */
internal const val SHARED_POLLING_OVERRIDE = "override_shared_polling"

/** The unique work name the poll has shipped under. WorkManager keeps work by it across updates, so it never changes. */
internal const val NOTIFICATION_WORK_NAME = "seerr_notifications"

/** The cursors' preference-name prefix in the shared library's store. An on-disk name: never change it. */
internal const val FEED_CURSOR_KEY_PREFIX = "feed_cursor"

/** One run of the poll, as the worker sees it: check, react, and say whether it succeeded or hit a transient failure. */
interface NotificationPoll {
    suspend fun run(): PollOutcome
}

/** The poll as it has always run: the checker, then this app's reactor. */
class LegacyNotificationPoll(
    private val checker: NotificationsChecker,
    private val reactor: PollReactor,
    private val prefs: NotificationPrefs,
) : NotificationPoll {
    override suspend fun run(): PollOutcome {
        val result = checker.check()
        prefs.setLastRun(System.currentTimeMillis())
        return reactor.react(result)
    }
}

/** The same poll reacting through the shared library's reactor, for the one server this app holds. */
class SharedNotificationPoll(
    private val checker: NotificationsChecker,
    private val reactor: SharedPollReactor,
    private val server: suspend () -> ServerId,
    private val prefs: NotificationPrefs,
) : NotificationPoll {
    override suspend fun run(): PollOutcome {
        val result = checker.check()
        prefs.setLastRun(System.currentTimeMillis())
        return when (reactor.react(server(), result.toShared())) {
            SharedPollOutcome.Succeed -> PollOutcome.Succeed
            SharedPollOutcome.Retry -> PollOutcome.Retry
        }
    }
}

/** Starts whatever keeps the poll's schedule in step with the connection, the switches and the interval. */
interface NotificationPlanning {
    fun start()
}

/** The shared library's planner, fed this app's one server. */
class SharedPlanning(
    private val connection: SeerrConnection,
    private val prefs: NotificationPrefs,
    private val server: ServerIdentity,
    private val scheduler: PollScheduler,
    private val notifier: PollNotifier,
    private val scope: CoroutineScope,
    private val breadcrumbs: CrashBreadcrumbs,
) : NotificationPlanning {
    override fun start() {
        val plans: Flow<PollPlan> =
            combine(
                connection.credentials,
                prefs.anyEnabled,
                prefs.pausedForAuthFailure,
                prefs.pollIntervalMinutes,
                flow { emit(server.currentServerId()) },
            ) { credentials, enabled, paused, interval, id ->
                PollPlan(listOf(ServerPollPlan(id, credentials != null, enabled, paused, credentials)), interval)
            }
        PollPlanner(plans, scheduler, notifier, scope, breadcrumbs).start()
    }
}

/** The shared library's notices, posted through this app's [SeerrNotifier]: a forwarded notice is queued in a DataStore, so each call suspends. */
class SeerrPollNotifier(
    private val notifier: SeerrNotifier,
) : PollNotifier {
    override suspend fun canPost(): Boolean = notifier.canPost()

    override suspend fun notifyConnectionProblem(server: ServerId) = notifier.notifyConnectionProblem()

    override suspend fun cancelConnectionProblem(server: ServerId) = notifier.cancelConnectionProblem()

    override suspend fun cancelNotices(server: ServerId) = notifier.cancelActivity()
}

/** The pause flag where it has always been, so a one-server install keeps its key. */
class PrefsPollPauseStore(
    private val prefs: NotificationPrefs,
) : PollPauseStore {
    override suspend fun setPausedForAuthFailure(
        server: ServerId,
        paused: Boolean,
    ) = prefs.setPausedForAuthFailure(paused)
}

/** Whether anything is left to poll, which the reactor asks before it cancels the shared schedule. */
suspend fun anyPollable(
    connection: SeerrConnection,
    prefs: NotificationPrefs,
): Boolean = connection.credentials.first() != null && prefs.anyEnabled.first() && !prefs.pausedForAuthFailure.first()

/**
 * The user the poll last read cursors for. A cursor is kept per user, and turning a signal on has to find the current user's
 * without asking the server, so the poll writes it here each run. It is not a credential.
 */
class PollUserStore(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun get(): String? = dataStore.data.first()[KEY]

    /** Writes only when the user changed, since the poll calls this for every feed it checks. */
    suspend fun set(userId: String) {
        if (get() != userId) dataStore.edit { it[KEY] = userId }
    }

    private companion object {
        val KEY = stringPreferencesKey("shared_poll_user_id")
    }
}

/** Starts a signal's cursor over, so what arrived while it was off is not announced. */
interface CursorReset {
    suspend fun reset(signal: NotificationSignal)

    suspend fun resetAll()

    /** Nothing to reset: the cursors are in [NotificationPrefs], which drops them itself. */
    object None : CursorReset {
        override suspend fun reset(signal: NotificationSignal) = Unit

        override suspend fun resetAll() = Unit
    }
}

/** [CursorReset] for the shared store. One signal goes with the current user; every server cursor goes when the server changes. */
class SharedCursorReset(
    private val store: FeedCursorStore,
    private val server: suspend () -> ServerId,
    private val pollUser: PollUserStore,
) : CursorReset {
    override suspend fun reset(signal: NotificationSignal) {
        val user = pollUser.get() ?: return
        store.remove(FeedCursorKey(server(), user, signal.key))
    }

    override suspend fun resetAll() = store.clear(server())
}

internal fun SharedCheckResult.toSeerr(): CheckResult =
    when (this) {
        SharedCheckResult.Ok, SharedCheckResult.StopPolling -> CheckResult.Ok
        SharedCheckResult.TransientFailure -> CheckResult.TransientFailure
        SharedCheckResult.AuthFailure -> CheckResult.AuthFailure
    }

internal fun CheckResult.toShared(): SharedCheckResult =
    when (this) {
        CheckResult.Ok -> SharedCheckResult.Ok
        CheckResult.TransientFailure -> SharedCheckResult.TransientFailure
        CheckResult.AuthFailure -> SharedCheckResult.AuthFailure
    }

/** The shared scheduler behind the interface the settings screen and the planner already use. */
class SharedNotificationScheduler(
    private val scheduler: PollScheduler,
) : NotificationScheduler {
    override fun schedule(intervalMinutes: Int) = scheduler.scheduleNoSoonerThan(intervalMinutes)

    override fun cancel() = scheduler.cancel()

    override fun nextRunMillis(): Flow<Long?> = scheduler.nextRunMillis()
}

internal fun PollOutcome.toShared(): SharedPollOutcome =
    when (this) {
        PollOutcome.Succeed -> SharedPollOutcome.Succeed
        PollOutcome.Retry -> SharedPollOutcome.Retry
    }
