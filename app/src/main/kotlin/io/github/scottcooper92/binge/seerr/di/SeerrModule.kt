package io.github.scottcooper92.binge.seerr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.scottcooper92.binge.seerr.data.IssueStore
import io.github.scottcooper92.binge.seerr.data.RoomIssueStore
import io.github.scottcooper92.binge.seerr.data.RoomUserStore
import io.github.scottcooper92.binge.seerr.data.SeerrCacheDatabase
import io.github.scottcooper92.binge.seerr.data.UserStore
import io.github.scottcooper92.binge.seerr.notifications.AndroidNotifier
import io.github.scottcooper92.binge.seerr.notifications.ApplicationScope
import io.github.scottcooper92.binge.seerr.notifications.NotificationPrefs
import io.github.scottcooper92.binge.seerr.notifications.NotificationScheduler
import io.github.scottcooper92.binge.seerr.notifications.SeerrNotifier
import io.github.scottcooper92.binge.seerr.notifications.WorkManagerNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

private val Context.notificationsDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_notifications")

/**
 * The wiring that is not the connection's: the cache, the notification plumbing and the scope the
 * background work runs on. [AuthModule] holds everything that reaches the server.
 */
@Module
@InstallIn(SingletonComponent::class)
object SeerrModule {
    /** A cache, so a schema change rebuilds it rather than migrating it. */
    @Provides
    @Singleton
    fun cacheDatabase(
        @ApplicationContext context: Context,
    ): SeerrCacheDatabase =
        Room
            .databaseBuilder(context, SeerrCacheDatabase::class.java, "seerr_cache.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun issueStore(db: SeerrCacheDatabase): IssueStore = RoomIssueStore(db)

    @Provides
    @Singleton
    fun userStore(db: SeerrCacheDatabase): UserStore = RoomUserStore(db)

    /** The scope for work that outlives every screen: the notification planner runs on it. */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun notificationPrefs(
        @ApplicationContext context: Context,
    ): NotificationPrefs = NotificationPrefs(context.notificationsDataStore)

    @Provides
    @Singleton
    fun notificationScheduler(
        @ApplicationContext context: Context,
    ): NotificationScheduler = WorkManagerNotificationScheduler(context)

    @Provides
    @Singleton
    fun notifier(
        @ApplicationContext context: Context,
    ): SeerrNotifier = AndroidNotifier(context)
}
