package com.cooper.binge.companion.seerr.di

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.binge.companion.kit.SecretCipher
import com.cooper.binge.companion.seerr.BuildConfig
import com.cooper.binge.companion.seerr.auth.BingeConnectionStore
import com.cooper.binge.companion.seerr.auth.ConnectionCarrier
import com.cooper.binge.companion.seerr.auth.ConnectionRestore
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.DataStoreBingeConnectionStore
import com.cooper.binge.companion.seerr.auth.DeviceIdentityStore
import com.cooper.binge.companion.seerr.auth.ForegroundUserRefresh
import com.cooper.binge.companion.seerr.auth.LegacyCredentialImport
import com.cooper.binge.companion.seerr.auth.LegacyCredentialStore
import com.cooper.binge.companion.seerr.auth.PlexPinFlow
import com.cooper.binge.companion.seerr.auth.RegistryCredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.auth.SeerrHealth
import com.cooper.binge.companion.seerr.auth.SeerrLogin
import com.cooper.binge.companion.seerr.auth.SeerrLoginCodec
import com.cooper.binge.companion.seerr.auth.ServerIdentity
import com.cooper.binge.companion.seerr.auth.forLibrary
import com.cooper.binge.companion.seerr.auth.seerrHealthMonitor
import com.cooper.binge.companion.seerr.data.MediaStatusStore
import com.cooper.binge.companion.seerr.handoff.HandOffAddressMemory
import com.cooper.binge.companion.seerr.notifications.ApplicationScope
import com.cooper.binge.companion.seerr.notifications.NotificationPrefs
import com.cooper.binge.companion.seerr.seerr.PlexClientIdentity
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.TitleCache
import com.cooper.binge.companion.seerr.seerr.insecurePublicHostOrNull
import com.cooper.binge.companion.seerr.ui.hub.HubOverviewCache
import com.cooper.homelab.core.diagnostics.LogcatLogger
import com.cooper.homelab.core.server.connection.ServerRegistry
import com.cooper.homelab.core.server.network.AndroidLocalNetworkPermission
import com.cooper.homelab.core.server.network.CleartextConsent
import com.cooper.homelab.core.server.network.DataStoreCleartextConsent
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import javax.inject.Qualifier
import javax.inject.Singleton
import com.cooper.homelab.core.server.connection.CredentialStore as LoginStore

private val Context.credentialsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "seerr_credentials",
    corruptionHandler = corruptionHandler(),
)
private val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "seerr_device",
    corruptionHandler = corruptionHandler(),
)
private val Context.cleartextConsentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cleartext_consent",
    corruptionHandler = corruptionHandler(),
)
private val Context.bingeConnectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "binge_connection",
    corruptionHandler = corruptionHandler(),
)
private val Context.serverRegistryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "seerr_servers",
    corruptionHandler = corruptionHandler(),
)
private val Context.serverLoginsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "seerr_server_logins",
    corruptionHandler = corruptionHandler(),
)

/** Marks the one boolean that says whether the saved connection lives in the shared library's per-server stores. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ServerRegistryEnabled

/** The preference key in the debug overrides file that turns the registry on. A shipped build never reads it. */
internal const val SERVER_REGISTRY_OVERRIDE = "override_server_registry"

/** The name plex.tv lists this app under on the user's authorised devices. A brand name, never translated. */
private const val PLEX_PRODUCT_NAME = "Seerr Companion"

/**
 * Everything that reaches the server: the saved connection and the clients that speak to it. What
 * encrypts the connection and what carries it to a new device are [DeviceKeysModule]'s. The one connection is application-scoped because the
 * exported Service and the app's own screens share it: what the user connects on one is what the
 * host is served from the other.
 */
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    /**
     * Whether the saved connection lives in the shared library's per-server stores. Off unless a debug build's override file says
     * so, and read once here, so the choice is fixed for the life of the process and a flip takes effect on the next launch. It is
     * not remote-controlled. The read blocks the main thread once, which is why it stays behind `BuildConfig.DEBUG`.
     */
    @Provides
    @Singleton
    @ServerRegistryEnabled
    fun serverRegistryEnabled(
        @ApplicationContext context: Context,
    ): Boolean =
        BuildConfig.DEBUG &&
            runBlocking { context.flagOverridesDataStore.data.first()[booleanPreferencesKey(SERVER_REGISTRY_OVERRIDE)] } == true

    @Provides
    @Singleton
    fun serverRegistry(
        @ApplicationContext context: Context,
    ): ServerRegistry = ServerRegistry(context.serverRegistryDataStore, "registry", { UUID.randomUUID().toString() }, LogcatLogger())

    @Provides
    @Singleton
    fun serverLogins(
        @ApplicationContext context: Context,
        cipher: SecretCipher,
    ): LoginStore<SeerrLogin> = LoginStore(context.serverLoginsDataStore, SeerrLoginCodec, cipher.forLibrary(), LogcatLogger())

    @Provides
    @Singleton
    fun currentServerId(
        @ApplicationContext context: Context,
    ): ServerIdentity = ServerIdentity(context.deviceDataStore)

    @Provides
    @Singleton
    fun credentialStore(
        @ApplicationContext context: Context,
        cipher: SecretCipher,
        @IoDispatcher io: CoroutineDispatcher,
        @ServerRegistryEnabled useRegistry: Boolean,
        registry: dagger.Lazy<ServerRegistry>,
        logins: dagger.Lazy<LoginStore<SeerrLogin>>,
        currentServerId: ServerIdentity,
    ): CredentialStore {
        val legacy = LegacyCredentialStore(context.credentialsDataStore, cipher, io)
        if (!useRegistry) return legacy
        val importer =
            LegacyCredentialImport(
                legacy = context.credentialsDataStore,
                registry = registry.get(),
                logins = logins.get(),
                marker = context.serverRegistryDataStore,
                serverId = currentServerId,
                logger = LogcatLogger(),
            )
        return RegistryCredentialStore(registry.get(), logins.get(), importer, currentServerId, io)
    }

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
    fun healthMonitor(identity: ServerIdentity): SeerrHealth = SeerrHealth(seerrHealthMonitor(), identity::currentServerId)

    /**
     * The write hook drops the title-status cache. It covers this app's own pages, which reach the
     * server through the same client as the exported service does; the service also drops the cache
     * itself, because its own writes are followed immediately by a status it must not answer stale.
     */
    @Provides
    @Singleton
    fun apiFactory(
        health: SeerrHealth,
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
    ): CleartextConsent =
        DataStoreCleartextConsent(context.cleartextConsentDataStore) {
            store.credentials
                .first()
                ?.baseUrl
                ?.insecurePublicHostOrNull()
        }

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
        health: SeerrHealth,
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
