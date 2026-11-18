package com.cooper.binge.companion.seerr.di

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.auth.ServerIdentity
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.notifications.CursorFeeds
import com.cooper.binge.companion.seerr.notifications.CursorReset
import com.cooper.binge.companion.seerr.notifications.FEED_CURSOR_KEY_PREFIX
import com.cooper.binge.companion.seerr.notifications.LegacyNotificationPoll
import com.cooper.binge.companion.seerr.notifications.NOTIFICATION_WORK_NAME
import com.cooper.binge.companion.seerr.notifications.NotificationPlanner
import com.cooper.binge.companion.seerr.notifications.NotificationPlanning
import com.cooper.binge.companion.seerr.notifications.NotificationPoll
import com.cooper.binge.companion.seerr.notifications.NotificationPrefs
import com.cooper.binge.companion.seerr.notifications.NotificationScheduler
import com.cooper.binge.companion.seerr.notifications.NotificationWorker
import com.cooper.binge.companion.seerr.notifications.NotificationsChecker
import com.cooper.binge.companion.seerr.notifications.PollReactor
import com.cooper.binge.companion.seerr.notifications.PollUserStore
import com.cooper.binge.companion.seerr.notifications.PrefsCursorFeeds
import com.cooper.binge.companion.seerr.notifications.PrefsPollPauseStore
import com.cooper.binge.companion.seerr.notifications.SHARED_POLLING_OVERRIDE
import com.cooper.binge.companion.seerr.notifications.SeerrNotifier
import com.cooper.binge.companion.seerr.notifications.SeerrPollNotifier
import com.cooper.binge.companion.seerr.notifications.SharedCursorFeeds
import com.cooper.binge.companion.seerr.notifications.SharedCursorReset
import com.cooper.binge.companion.seerr.notifications.SharedNotificationPoll
import com.cooper.binge.companion.seerr.notifications.SharedNotificationScheduler
import com.cooper.binge.companion.seerr.notifications.SharedPlanning
import com.cooper.binge.companion.seerr.notifications.SharedPollingEnabled
import com.cooper.binge.companion.seerr.notifications.WorkManagerNotificationScheduler
import com.cooper.binge.companion.seerr.notifications.anyPollable
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.notifications.DataStoreFeedCursorStore
import com.cooper.homelab.core.notifications.FeedCursorStore
import com.cooper.homelab.core.notifications.WorkManagerPollScheduler
import com.cooper.homelab.core.telemetry.CrashBreadcrumbs
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton
import com.cooper.homelab.core.notifications.PollReactor as SharedPollReactor

/**
 * The notification poll, in one of two shapes chosen once per process: the shared library's reactor, planner, scheduler and
 * cursor store (the default), or the app's earlier one, kept for a release as a debug-build off switch. Both keep the worker's class, the work's unique name, every preference the app
 * has written, and what a notice says.
 */
@Module
@InstallIn(SingletonComponent::class)
object SharedPollingModule {
    /**
     * Whether the poll runs on the shared library. On, and read once here, so the choice is fixed for the life of the process.
     * A debug build's override file can turn it off for the next launch; a shipped build never reads it, and nothing remote
     * controls it. The debug read blocks the main thread once.
     */
    @Provides
    @Singleton
    @SharedPollingEnabled
    fun sharedPollingEnabled(
        @ApplicationContext context: Context,
    ): Boolean =
        sharedPollingOn(
            BuildConfig.DEBUG,
            if (BuildConfig.DEBUG) {
                runBlocking { context.flagOverridesDataStore.data.first()[booleanPreferencesKey(SHARED_POLLING_OVERRIDE)] }
            } else {
                null
            },
        )

    @Provides
    @Singleton
    fun feedCursorStore(
        @ApplicationContext context: Context,
    ): FeedCursorStore = DataStoreFeedCursorStore(context.notificationsDataStore, FEED_CURSOR_KEY_PREFIX)

    @Provides
    @Singleton
    fun pollUserStore(
        @ApplicationContext context: Context,
    ): PollUserStore = PollUserStore(context.notificationsDataStore)

    @Provides
    @Singleton
    fun cursorReset(
        @SharedPollingEnabled shared: Boolean,
        store: Lazy<FeedCursorStore>,
        identity: ServerIdentity,
        pollUser: Lazy<PollUserStore>,
    ): CursorReset = if (shared) SharedCursorReset(store.get(), identity::currentServerId, pollUser.get()) else CursorReset.None

    @Provides
    @Singleton
    fun notificationPrefs(
        @ApplicationContext context: Context,
        reset: CursorReset,
    ): NotificationPrefs = NotificationPrefs(context.notificationsDataStore, reset)

    @Provides
    @Singleton
    fun cursorFeeds(
        @SharedPollingEnabled shared: Boolean,
        prefs: NotificationPrefs,
        store: FeedCursorStore,
        identity: ServerIdentity,
        pollUser: PollUserStore,
        logger: Logger,
    ): CursorFeeds = if (shared) SharedCursorFeeds(prefs, store, identity::currentServerId, pollUser, logger) else PrefsCursorFeeds(prefs)

    @Provides
    @Singleton
    fun workManagerPollScheduler(
        @ApplicationContext context: Context,
    ): WorkManagerPollScheduler = WorkManagerPollScheduler(context, NOTIFICATION_WORK_NAME, NotificationWorker::class.java)

    @Provides
    @Singleton
    fun notificationScheduler(
        @SharedPollingEnabled shared: Boolean,
        @ApplicationContext context: Context,
        scheduler: Lazy<WorkManagerPollScheduler>,
    ): NotificationScheduler = if (shared) SharedNotificationScheduler(scheduler.get()) else WorkManagerNotificationScheduler(context)

    @Provides
    @Singleton
    fun sharedPollReactor(
        notifier: SeerrNotifier,
        scheduler: WorkManagerPollScheduler,
        prefs: NotificationPrefs,
        connection: SeerrConnection,
    ): SharedPollReactor =
        SharedPollReactor(
            notifier = SeerrPollNotifier(notifier),
            scheduler = scheduler,
            pause = PrefsPollPauseStore(prefs),
            onStopPolling = {},
            anyPollable = { anyPollable(connection, prefs) },
        )

    @Provides
    fun notificationPoll(
        @SharedPollingEnabled shared: Boolean,
        checker: NotificationsChecker,
        legacyReactor: Lazy<PollReactor>,
        sharedReactor: Lazy<SharedPollReactor>,
        identity: ServerIdentity,
        prefs: NotificationPrefs,
    ): NotificationPoll =
        if (shared) {
            SharedNotificationPoll(checker, sharedReactor.get(), identity::currentServerId, prefs)
        } else {
            LegacyNotificationPoll(checker, legacyReactor.get(), prefs)
        }

    @Provides
    fun notificationPlanning(
        @SharedPollingEnabled shared: Boolean,
        legacy: Lazy<NotificationPlanner>,
        connection: SeerrConnection,
        prefs: NotificationPrefs,
        identity: ServerIdentity,
        scheduler: WorkManagerPollScheduler,
        notifier: SeerrNotifier,
        @ApplicationScope scope: CoroutineScope,
        breadcrumbs: CrashBreadcrumbs,
    ): NotificationPlanning =
        if (shared) {
            SharedPlanning(connection, prefs, identity, scheduler, SeerrPollNotifier(notifier), scope, breadcrumbs)
        } else {
            legacy.get()
        }
}

/** On unless a debug build's override says off. A shipped build ignores the override, whatever it holds. */
internal fun sharedPollingOn(
    debug: Boolean,
    override: Boolean?,
): Boolean = !debug || override != false
