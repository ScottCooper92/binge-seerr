package com.cooper.binge.companion.seerr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.cooper.binge.companion.seerr.data.IssueStore
import com.cooper.binge.companion.seerr.data.MediaStatusStore
import com.cooper.binge.companion.seerr.data.RequestStore
import com.cooper.binge.companion.seerr.data.RoomIssueStore
import com.cooper.binge.companion.seerr.data.RoomMediaStatusStore
import com.cooper.binge.companion.seerr.data.RoomRequestStore
import com.cooper.binge.companion.seerr.data.RoomUserStore
import com.cooper.binge.companion.seerr.data.SeerrCacheDatabase
import com.cooper.binge.companion.seerr.data.TitleDao
import com.cooper.binge.companion.seerr.data.TitleStatusStore
import com.cooper.binge.companion.seerr.data.UserStore
import com.cooper.binge.companion.seerr.feedback.ShakeToReportSetting
import com.cooper.binge.companion.seerr.feedback.shakeToReportPreference
import com.cooper.binge.companion.seerr.notifications.AndroidNotificationText
import com.cooper.binge.companion.seerr.notifications.AndroidNotifier
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.notifications.ForwardedNotificationCodec
import com.cooper.binge.companion.seerr.notifications.ForwardingNotifier
import com.cooper.binge.companion.seerr.notifications.SeerrForwarding
import com.cooper.binge.companion.seerr.notifications.SeerrForwardingKeys
import com.cooper.binge.companion.seerr.notifications.SeerrNotifier
import com.cooper.binge.companion.seerr.seerr.TitleCache
import com.cooper.binge.companion.seerr.telemetry.CrashReportingMigration
import com.cooper.binge.companion.seerr.ui.hub.BingeInstallCheck
import com.cooper.binge.companion.seerr.ui.hub.PackageManagerBingeInstallCheck
import com.cooper.homelab.core.bridge.NotificationForwarding
import com.cooper.homelab.core.flags.BooleanPreference
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.telemetry.TelemetryPrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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

internal val Context.notificationsDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_notifications")

private val Context.telemetryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "seerr_telemetry",
    produceMigrations = { listOf(CrashReportingMigration) },
)

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
    fun titleDao(db: SeerrCacheDatabase): TitleDao = db.titleDao()

    @Provides
    @Singleton
    fun titleCache(dao: TitleDao): TitleCache = TitleCache(dao)

    @Provides
    @Singleton
    fun issueStore(db: SeerrCacheDatabase): IssueStore = RoomIssueStore(db)

    @Provides
    @Singleton
    fun requestStore(db: SeerrCacheDatabase): RequestStore = RoomRequestStore(db)

    @Provides
    @Singleton
    fun userStore(db: SeerrCacheDatabase): UserStore = RoomUserStore(db)

    /** What every list of cards follows for a title's status; process memory, cleared with the server's other caches. */
    @Provides
    @Singleton
    fun titleStatusStore(): TitleStatusStore = TitleStatusStore()

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
    fun telemetryPrefs(
        @ApplicationContext context: Context,
    ): TelemetryPrefs = TelemetryPrefs(context.telemetryDataStore, crashReportingDefault = false)

    @Provides
    @Singleton
    @ShakeToReportSetting
    fun shakeToReport(
        @ApplicationContext context: Context,
    ): BooleanPreference = shakeToReportPreference(context.feedbackDataStore)

    @Provides
    @Singleton
    fun notificationForwarding(
        @ApplicationContext context: Context,
    ): SeerrForwarding =
        NotificationForwarding(context.notificationsDataStore, SeerrForwardingKeys, ForwardedNotificationCodec, Clock.system())

    /** Posts this app's notices itself, unless Binge is showing them instead. */
    @Provides
    @Singleton
    fun notifier(
        @ApplicationContext context: Context,
        forwarding: SeerrForwarding,
    ): SeerrNotifier = ForwardingNotifier(AndroidNotifier(context), forwarding, AndroidNotificationText(context))

    @Provides
    fun bingeInstallCheck(
        @ApplicationContext context: Context,
    ): BingeInstallCheck = PackageManagerBingeInstallCheck(context)
}
