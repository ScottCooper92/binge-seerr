package io.github.scottcooper92.binge.seerr.handoff

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.scottcooper92.binge.seerr.seerr.attempt
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * The addresses the user typed on this phone to send to a television, kept per server so the next
 * hand-off offers them again. Only an address the user typed and a TV accepted is kept.
 */
interface HandOffAddressMemory {
    /** What was sent for [server], most recent first. */
    suspend fun remembered(server: String): List<String>

    /** Puts [address] first for [server]. */
    suspend fun remember(
        server: String,
        address: String,
    )
}

/**
 * [HandOffAddressMemory] in a DataStore of its own, one entry per server keyed on its normalised
 * address, holding at most [limit] addresses. Nothing secret is stored: these are addresses the user
 * typed to show a TV, the same text the TV's own form would hold.
 */
class DataStoreHandOffAddressMemory(
    private val dataStore: DataStore<Preferences>,
    private val limit: Int = DEFAULT_LIMIT,
) : HandOffAddressMemory {
    override suspend fun remembered(server: String): List<String> = dataStore.data.first()[keyFor(server)].decode()

    override suspend fun remember(
        server: String,
        address: String,
    ) {
        dataStore.edit { prefs ->
            val key = keyFor(server)
            val updated = (listOf(address) + prefs[key].decode().filterNot { it == address }).take(limit)
            prefs[key] = Json.encodeToString(updated)
        }
    }

    private fun keyFor(server: String): Preferences.Key<String> =
        stringPreferencesKey("$KEY_PREFIX${normaliseServerAddress(server) ?: server}")

    private fun String?.decode(): List<String> = this?.let { attempt { Json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY_PREFIX = "sent_for:"
        const val DEFAULT_LIMIT = 3
    }
}
