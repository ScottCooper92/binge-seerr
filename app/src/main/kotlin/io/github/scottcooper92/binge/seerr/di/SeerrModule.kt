package io.github.scottcooper92.binge.seerr.di

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.scottcooper92.binge.seerr.BuildConfig
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.DeviceIdentityStore
import io.github.scottcooper92.binge.seerr.auth.KeystoreSecretCipher
import io.github.scottcooper92.binge.seerr.auth.PlexPinFlow
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealthMonitor
import io.github.scottcooper92.binge.seerr.data.IssueStore
import io.github.scottcooper92.binge.seerr.data.RoomIssueStore
import io.github.scottcooper92.binge.seerr.data.RoomUserStore
import io.github.scottcooper92.binge.seerr.data.SeerrCacheDatabase
import io.github.scottcooper92.binge.seerr.data.UserStore
import io.github.scottcooper92.binge.seerr.seerr.PlexClientIdentity
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import javax.inject.Singleton

private val Context.credentialsDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_credentials")
private val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_device")

/** The name plex.tv lists this app under on the user's authorised devices. A brand name, never translated. */
private const val PLEX_PRODUCT_NAME = "Binge Seerr"

/**
 * The wiring, in one place a reader can see. The one connection is application-scoped because the
 * exported Service and the app's own screens share it: what the user connects on one is what the
 * host is served from the other.
 */
@Module
@InstallIn(SingletonComponent::class)
object SeerrModule {
    @Provides
    @Singleton
    fun credentialStore(
        @ApplicationContext context: Context,
    ): CredentialStore = CredentialStore(context.credentialsDataStore, KeystoreSecretCipher())

    @Provides
    @Singleton
    fun healthMonitor(): SeerrConnectionHealthMonitor = SeerrConnectionHealthMonitor()

    @Provides
    @Singleton
    fun apiFactory(health: SeerrConnectionHealthMonitor): SeerrApiFactory =
        SeerrApiFactory(logRequests = BuildConfig.DEBUG, health = health)

    @Provides
    @Singleton
    fun deviceIdentityStore(
        @ApplicationContext context: Context,
    ): DeviceIdentityStore = DeviceIdentityStore(context.deviceDataStore)

    @Provides
    @Singleton
    fun plexPinFlow(devices: DeviceIdentityStore): PlexPinFlow =
        PlexPinFlow(
            identity = {
                PlexClientIdentity(
                    identifier = devices.plexClientIdentifier(),
                    product = PLEX_PRODUCT_NAME,
                    version = BuildConfig.VERSION_NAME,
                    device = Build.MODEL,
                )
            },
        )

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

    /** The caches keyed to one server are cleared when the server changes, so nothing of the last one shows. */
    @Provides
    @Singleton
    fun connection(
        store: CredentialStore,
        apis: SeerrApiFactory,
        health: SeerrConnectionHealthMonitor,
        issues: IssueStore,
        users: UserStore,
    ): SeerrConnection =
        SeerrConnection(store, apis, health, onServerChanged = {
            issues.clearAll()
            users.clearAll()
        })
}
