package io.github.scottcooper92.binge.seerr.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * What identifies this install to services outside the Seerr server. Kept apart from the
 * credentials so that disconnecting, which clears those, does not turn the next Plex sign-in into
 * a new device on the user's plex.tv account.
 */
class DeviceIdentityStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** A UUID minted on first use and kept for the install's life. */
    suspend fun plexClientIdentifier(): String =
        dataStore.data.first()[PLEX_CLIENT_IDENTIFIER]
            ?: dataStore
                .edit { prefs -> if (prefs[PLEX_CLIENT_IDENTIFIER] == null) prefs[PLEX_CLIENT_IDENTIFIER] = UUID.randomUUID().toString() }
                .let { prefs -> checkNotNull(prefs[PLEX_CLIENT_IDENTIFIER]) }

    private companion object {
        val PLEX_CLIENT_IDENTIFIER = stringPreferencesKey("plex_client_identifier")
    }
}
