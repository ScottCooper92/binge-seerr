package io.github.scottcooper92.binge.seerr.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.scottcooper92.binge.seerr.BuildConfig
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.KeystoreSecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealthMonitor
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import javax.inject.Singleton

private val Context.credentialsDataStore: DataStore<Preferences> by preferencesDataStore(name = "seerr_credentials")

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
    fun connection(
        store: CredentialStore,
        apis: SeerrApiFactory,
        health: SeerrConnectionHealthMonitor,
    ): SeerrConnection = SeerrConnection(store, apis, health)
}
