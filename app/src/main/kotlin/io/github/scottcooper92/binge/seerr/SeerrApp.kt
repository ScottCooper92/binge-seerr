package io.github.scottcooper92.binge.seerr

import android.app.Application
import androidx.datastore.preferences.preferencesDataStore
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.KeystoreSecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory

private val Application.credentialsDataStore by preferencesDataStore(name = "seerr_credentials")

/**
 * The object graph, built by hand. A companion this size does not earn a DI framework, and a
 * reader learning what a companion looks like should see the wiring, not an annotation processor.
 * The one connection is application-scoped because the exported Service and the setup screen
 * share it: what the user connects on one is what the host is served from the other.
 */
class SeerrApp : Application() {
    val connection: SeerrConnection by lazy {
        SeerrConnection(
            store = CredentialStore(credentialsDataStore, KeystoreSecretCipher()),
            apis = SeerrApiFactory(logRequests = BuildConfig.DEBUG),
        )
    }
}
