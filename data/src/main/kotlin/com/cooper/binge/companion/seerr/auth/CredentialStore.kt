package com.cooper.binge.companion.seerr.auth

import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The saved connection, which is one server and one account. [LegacyCredentialStore] keeps it in the file the released
 * versions wrote. [RegistryCredentialStore] keeps it in the shared library's per-server stores, behind a local switch, and
 * imports the old file once.
 */
interface CredentialStore {
    /** The saved connection, or null when nothing is saved or its secret does not decrypt. */
    val credentials: Flow<SeerrCredentials?>

    /** The server the user signed out of, while nothing is saved, so setup can open on its sign-in. */
    val signedOutServer: Flow<String?>

    /**
     * The profile last read for the saved server, or null when none is kept. It lives and dies with the connection it was
     * read for: [save], [signOut] and [clear] drop it.
     */
    val profile: Flow<StoredProfile?> get() = flowOf(null)

    /** Keeps [profile] beside the connection, but only while [credentials] is still the saved one, so a late write cannot outlive a reconnect. */
    suspend fun saveProfile(
        credentials: SeerrCredentials,
        profile: StoredProfile,
    ) = Unit

    /** Persists [credentials]. Returns false, storing nothing, when it could not be saved. */
    suspend fun save(credentials: SeerrCredentials): Boolean

    /** Forgets the connection and the address. */
    suspend fun clear()

    /** Forgets the connection, secret and all, but keeps its address as [signedOutServer]. */
    suspend fun signOut()
}
