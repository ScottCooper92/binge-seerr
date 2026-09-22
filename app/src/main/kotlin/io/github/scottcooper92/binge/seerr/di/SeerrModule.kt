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
import io.github.scottcooper92.binge.seerr.data.MediaStatusStore
import io.github.scottcooper92.binge.seerr.data.RoomIssueStore
import io.github.scottcooper92.binge.seerr.data.RoomMediaStatusStore
import io.github.scottcooper92.binge.seerr.data.RoomUserStore
import io.github.scottcooper92.binge.seerr.data.SeerrCacheDatabase
import io.github.scottcooper92.binge.seerr.data.UserStore
import io.github.scottcooper92.binge.seerr.feedback.FeedbackPrefs
import io.github.scottcooper92.binge.seerr.notifications.AndroidNotifier
import io.github.scottcooper92.binge.seerr.notifications.ApplicationScope
import io.github.scottcooper92.binge.seerr.notifications.NotificationPrefs
import io.github.scottcooper92.binge.seerr.notifications.NotificationScheduler
import io.github.scottcooper92.binge.seerr.notifications.SeerrNotifier
import io.github.scottcooper92.binge.seerr.notifications.WorkManagerNotificationScheduler
import io.github.scottcooper92.binge.seerr.telemetry.TelemetryPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * The dispatcher a ViewModel launches its network work on, instead of inheriting
 * `Dispatchers.Main.immediate` from `viewModelScope`. A coroutine launched this way never touches
 * `Dispatchers.Main` at all, including after `ViewModelStore.clear()` cancels it and its
 * in-flight call still resumes: it resumes on this dispatcher, not on a Main a test may have
 * already reset. See `MainDispatcherRule`'s KDoc and #177.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

private val Context.notificationsDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_notifications")

private val Context.telemetryDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_telemetry")

private val Context.feedbackDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_feedback")

/**
 * The wiring that is not the connection's: the cache, the notification plumbing, the reporting
 * and feedback preferences and the scope the background work runs on. [AuthModule] holds everything that reaches the server.
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

    @Provides
    @Singleton
    fun mediaStatusStore(db: SeerrCacheDatabase): MediaStatusStore = RoomMediaStatusStore(db)

    /** The scope for work that outlives every screen: the notification planner runs on it. */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun notificationPrefs(
        @ApplicationContext context: Context,
    ): NotificationPrefs = NotificationPrefs(context.notificationsDataStore)

    @Provides
    @Singleton
    fun telemetryPrefs(
        @ApplicationContext context: Context,
    ): TelemetryPrefs = TelemetryPrefs(context.telemetryDataStore)

    @Provides
    @Singleton
    fun feedbackPrefs(
        @ApplicationContext context: Context,
    ): FeedbackPrefs = FeedbackPrefs(context.feedbackDataStore)

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
