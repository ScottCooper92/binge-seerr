package io.github.scottcooper92.binge.seerr.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** A hint on the hub that the user can dismiss, one per state Binge can be in. */
enum class BingeHint { NotInstalled, NotConnected, Connected }

/**
 * Whether Binge has ever completed a handshake against the current connection — the one signal
 * this app has for "Binge is installed, allowed, and actually talking to this server". Set by
 * `SeerrRequestService.handshake()` on its first success, and forgotten when the connection
 * changes, same as [CredentialStore].
 */
interface BingeConnectionStore {
    val hasConnected: Flow<Boolean>

    /** The hints the user has closed. Kept when the connection changes: the user's answer is about the hint, not the server. */
    val dismissedHints: Flow<Set<BingeHint>>

    suspend fun dismissHint(hint: BingeHint)

    suspend fun recordHandshake()

    suspend fun forget()
}

class DataStoreBingeConnectionStore(
    private val dataStore: DataStore<Preferences>,
) : BingeConnectionStore {
    override val hasConnected: Flow<Boolean> = dataStore.data.map { it[Keys.HANDSHAKED] == true }.distinctUntilChanged()

    override val dismissedHints: Flow<Set<BingeHint>> =
        dataStore.data
            .map { prefs ->
                prefs[Keys.DISMISSED].orEmpty().mapNotNull { name -> BingeHint.entries.firstOrNull { it.name == name } }.toSet()
            }.distinctUntilChanged()

    override suspend fun dismissHint(hint: BingeHint) {
        dataStore.edit { it[Keys.DISMISSED] = it[Keys.DISMISSED].orEmpty() + hint.name }
    }

    override suspend fun recordHandshake() {
        dataStore.edit { it[Keys.HANDSHAKED] = true }
    }

    override suspend fun forget() {
        dataStore.edit { it.remove(Keys.HANDSHAKED) }
    }

    private object Keys {
        val HANDSHAKED = booleanPreferencesKey("binge_handshaked")
        val DISMISSED = stringSetPreferencesKey("binge_dismissed_hints")
    }
}

/** Never records a handshake — what a build or a test that has not wired one gets. */
object NoBingeConnectionStore : BingeConnectionStore {
    override val hasConnected: Flow<Boolean> = flowOf(false)

    override val dismissedHints: Flow<Set<BingeHint>> = flowOf(emptySet())

    override suspend fun dismissHint(hint: BingeHint) = Unit

    override suspend fun recordHandshake() = Unit

    override suspend fun forget() = Unit
}
