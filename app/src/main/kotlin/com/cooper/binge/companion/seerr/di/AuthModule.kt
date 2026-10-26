package com.cooper.binge.companion.seerr.di

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.binge.companion.seerr.auth.BingeConnectionStore
import com.cooper.binge.companion.seerr.auth.CleartextConsent
import com.cooper.binge.companion.seerr.auth.ConnectionCarrier
import com.cooper.binge.companion.seerr.auth.ConnectionRestore
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.DataStoreBingeConnectionStore
import com.cooper.binge.companion.seerr.auth.DataStoreCleartextConsent
import com.cooper.binge.companion.seerr.auth.DeviceIdentityStore
import com.cooper.binge.companion.seerr.auth.ForegroundUserRefresh
import com.cooper.binge.companion.seerr.auth.PlexPinFlow
import com.cooper.binge.companion.seerr.auth.SecretCipher
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.auth.SeerrConnectionHealthMonitor
import com.cooper.binge.companion.seerr.data.MediaStatusStore
import com.cooper.binge.companion.seerr.handoff.HandOffAddressMemory
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.notifications.NotificationPrefs
import com.cooper.binge.companion.seerr.seerr.AndroidLocalNetworkPermission
import com.cooper.binge.companion.seerr.seerr.LocalNetworkPermission
import com.cooper.binge.companion.seerr.seerr.PlexClientIdentity
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.TitleCache
import com.cooper.binge.companion.seerr.ui.hub.HubOverviewCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Singleton

private val Context.credentialsDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_credentials")
private val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_device")
private val Context.cleartextConsentDataStore: DataStore<Preferences> by preferencesDataStore(name = "cleartext_consent")
private val Context.bingeConnectionDataStore: DataStore<Preferences> by preferencesDataStore(name = "binge_connection")

/** The name plex.tv lists this app under on the user's authorised devices. A brand name, never translated. */
private const val PLEX_PRODUCT_NAME = "Binge Seerr"

/**
 * Everything that reaches the server: the saved connection and the clients that speak to it. What
 * encrypts the connection and what carries it to a new device are [DeviceKeysModule]'s. The one connection is application-scoped because the
 * exported Service and the app's own screens share it: what the user connects on one is what the
 * host is served from the other.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun credentialStore(
        @ApplicationContext context: Context,
        cipher: SecretCipher,
        @IoDispatcher io: CoroutineDispatcher,
    ): CredentialStore = CredentialStore(context.credentialsDataStore, cipher, io)

    @Provides
    @Singleton
    fun bingeConnectionStore(
        @ApplicationContext context: Context,
    ): BingeConnectionStore = DataStoreBingeConnectionStore(context.bingeConnectionDataStore)

    @Provides
    @Singleton
    fun localNetworkPermission(
        @ApplicationContext context: Context,
    ): LocalNetworkPermission = AndroidLocalNetworkPermission(context)

    @Provides
    @Singleton
    fun healthMonitor(): SeerrConnectionHealthMonitor = SeerrConnectionHealthMonitor()

    /**
     * The write hook drops the title-status cache. It covers this app's own pages, which reach the
     * server through the same client as the exported service does; the service also drops the cache
     * itself, because its own writes are followed immediately by a status it must not answer stale.
     */
    @Provides
    @Singleton
    fun apiFactory(
        health: SeerrConnectionHealthMonitor,
        statuses: MediaStatusStore,
        cleartext: CleartextConsent,
        localNetwork: LocalNetworkPermission,
        @ApplicationScope scope: CoroutineScope,
    ): SeerrApiFactory =
        SeerrApiFactory(
            logRequests = BuildConfig.DEBUG,
            health = health,
            onWrite = dropStatusesOnWrite(statuses, scope),
            cleartext = cleartext,
            localNetwork = localNetwork,
        )

    @Provides
    @Singleton
    fun cleartextConsent(
        @ApplicationContext context: Context,
        store: CredentialStore,
    ): CleartextConsent = DataStoreCleartextConsent(context.cleartextConsentDataStore) { store.credentials.first()?.baseUrl }

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

    @Provides
    @Singleton
    fun connectionRestore(
        store: CredentialStore,
        apis: SeerrApiFactory,
        carrier: ConnectionCarrier,
        cleartext: CleartextConsent,
        @ApplicationScope scope: CoroutineScope,
    ): ConnectionRestore = ConnectionRestore(store, apis, carrier, scope, cleartext)

    @Provides
    @Singleton
    fun foregroundUserRefresh(
        connection: SeerrConnection,
        @ApplicationScope scope: CoroutineScope,
    ): ForegroundUserRefresh = ForegroundUserRefresh(connection, scope)

    /** The caches keyed to one server are cleared when the server changes, so nothing of the last one shows. */
    @Provides
    @Singleton
    fun connection(
        store: CredentialStore,
        apis: SeerrApiFactory,
        health: SeerrConnectionHealthMonitor,
        carrier: ConnectionCarrier,
        caches: ServerScopedCaches,
        titles: TitleCache,
        notifications: NotificationPrefs,
        bingeConnection: BingeConnectionStore,
        hubOverview: HubOverviewCache,
        cleartext: CleartextConsent,
        handOffMemory: HandOffAddressMemory,
    ): SeerrConnection =
        SeerrConnection(store, apis, health, carrier = carrier, cleartext = cleartext, onServerChanged = {
            caches.clearAll()
            titles.clear()
            notifications.forgetServer()
            bingeConnection.forget()
            hubOverview.clear()
            // The addresses sent to a TV were typed for this server, so disconnecting removes them too.
            handOffMemory.clear()
        })
}

/**
 * [AuthModule.apiFactory]'s write hook: a write the saved server accepts drops the title-status cache. Its own function so a
 * test can build a factory with this exact hook and a dispatcher it can drain (#903).
 */
internal fun dropStatusesOnWrite(
    statuses: MediaStatusStore,
    scope: CoroutineScope,
): () -> Unit = { scope.launch { statuses.clearAll() } }
