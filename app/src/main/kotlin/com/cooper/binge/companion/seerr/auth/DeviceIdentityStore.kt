package com.cooper.binge.companion.seerr.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.cooper.homelab.core.server.connection.DeviceIdentity
import java.util.UUID

/**
 * What identifies this install to services outside the Seerr server. Kept apart from the
 * credentials so that disconnecting, which clears those, does not turn the next Plex sign-in into
 * a new device on the user's plex.tv account.
 *
 * The id is the shared library's [DeviceIdentity] over the file and key the released versions used, so an installed id reads back
 * as it is.
 */
class DeviceIdentityStore(
    dataStore: DataStore<Preferences>,
) {
    private val identity = DeviceIdentity(dataStore, PLEX_CLIENT_IDENTIFIER_KEY) { UUID.randomUUID().toString() }

    /** A UUID minted on first use and kept for the install's life. */
    suspend fun plexClientIdentifier(): String = identity.id()

    private companion object {
        const val PLEX_CLIENT_IDENTIFIER_KEY = "plex_client_identifier"
    }
}
