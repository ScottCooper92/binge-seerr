package io.github.scottcooper92.binge.seerr.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.scottcooper92.binge.seerr.seerr.insecurePublicHostOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The public hosts the user has explicitly agreed to reach over plain HTTP.
 *
 * Plain HTTP to a loopback, LAN or tailnet host needs no consent: that traffic stays on a network the
 * user controls or one that is already encrypted. To any other host it carries the API key or session
 * cookie in the clear, so every client `SeerrApiFactory` builds refuses it unless the host is here.
 * Setup asks for the consent; it covers only the saved server, and leaving that server drops it.
 */
interface CleartextConsent {
    /** Whether plain HTTP to [host] has been agreed to. */
    suspend fun allows(host: String): Boolean

    /** Records the user's opt-in for [host]. */
    suspend fun grant(host: String)

    /** Forgets every host but [host], or every host when it is null: the consent belongs to the saved server. */
    suspend fun retainOnly(host: String?)

    /** Allows nothing and records nothing. The default where no consent is wired, so the guard stays shut. */
    object None : CleartextConsent {
        override suspend fun allows(host: String): Boolean = false

        override suspend fun grant(host: String) = Unit

        override suspend fun retainOnly(host: String?) = Unit
    }
}

/**
 * [CleartextConsent] in a DataStore of its own.
 *
 * Builds before the opt-in existed only warned, so a user may already be connected over plain HTTP to a
 * public host. The first read grandfathers that saved server in, [savedBaseUrl] being where to find it,
 * rather than cutting off a connection the user set up knowingly. It runs once per install: after that,
 * consent is only ever given on the setup screen.
 */
class DataStoreCleartextConsent(
    private val dataStore: DataStore<Preferences>,
    private val savedBaseUrl: suspend () -> String?,
) : CleartextConsent {
    private val seeding = Mutex()

    override suspend fun allows(host: String): Boolean = host in seeded()[Keys.HOSTS].orEmpty()

    override suspend fun grant(host: String) {
        seeded()
        dataStore.edit { it[Keys.HOSTS] = it[Keys.HOSTS].orEmpty() + host }
    }

    override suspend fun retainOnly(host: String?) {
        seeded()
        dataStore.edit { prefs -> prefs[Keys.HOSTS] = prefs[Keys.HOSTS].orEmpty().filter { it == host }.toSet() }
    }

    private suspend fun seeded(): Preferences {
        dataStore.data.first().let { if (it[Keys.SEEDED] == true) return it }
        return seeding.withLock {
            dataStore.data.first().let { if (it[Keys.SEEDED] == true) return it }
            val legacy = savedBaseUrl()?.insecurePublicHostOrNull()
            dataStore.edit { prefs ->
                prefs[Keys.SEEDED] = true
                legacy?.let { prefs[Keys.HOSTS] = prefs[Keys.HOSTS].orEmpty() + it }
            }
        }
    }

    private object Keys {
        val HOSTS = stringSetPreferencesKey("hosts")
        val SEEDED = booleanPreferencesKey("seeded")
    }
}
