package com.cooper.binge.companion.seerr.auth

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.server.connection.ActiveServer
import com.cooper.homelab.core.server.connection.SavedServer
import com.cooper.homelab.core.server.connection.ServerRegistry
import com.cooper.homelab.core.server.connection.SingleServerStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.cooper.homelab.core.server.connection.CredentialStore as LoginStore

/** The word this app gives the saved server's `kind`. The library reads nothing into it. */
internal const val SEERR_KIND = "seerr"

/**
 * The saved connection in the shared library's per-server stores, as one server and one account. [SingleServerStore] keeps the
 * server, the login and the write lock; what stays here is the app's: the address a login is bound to, the credentials it reads
 * as, and the profile kept beside it. This app has no switcher, so [save] replaces the server and [clear] removes it.
 *
 * The first read, and every write, runs [importer] once, so an updating user has the connection the released version kept. The
 * secret is decrypted on [io], as [LegacyCredentialStore] does, because the first screen collects on the main thread.
 */
class RegistryCredentialStore(
    registry: ServerRegistry,
    logins: LoginStore<SeerrLogin>,
    importer: LegacyCredentialImport,
    serverId: ServerIdentity,
    io: CoroutineDispatcher,
) : CredentialStore {
    private val store =
        SingleServerStore(
            registry = registry,
            logins = logins,
            kind = SEERR_KIND,
            serverId = serverId::currentServerId,
            io = io,
            importOnce = importer::importOnce,
        )

    override val credentials: Flow<SeerrCredentials?> =
        store.active
            .map { active -> active?.let { signedIn(it.server, it.login) } }
            .distinctUntilChanged()

    override val signedOutServer: Flow<String?> =
        store.active
            .map { active -> active?.signedOutAddress() }
            .distinctUntilChanged()

    override val profile: Flow<StoredProfile?> =
        store.active
            .map { active -> active?.profileText() }
            .map(StoredProfile::decode)
            .distinctUntilChanged()

    override suspend fun saveProfile(
        credentials: SeerrCredentials,
        profile: StoredProfile,
    ) {
        attempt {
            store.modifyLogin { server, login ->
                login
                    .takeIf { server.address.url == credentials.baseUrl && it.isFor(server) && it.auth == credentials.auth }
                    ?.copy(profile = profile.encode())
            }
        }
    }

    override suspend fun save(credentials: SeerrCredentials): Boolean {
        val address = ServerAddress.parse(credentials.baseUrl) ?: return false
        return store.save(address, address.host, SeerrLogin(credentials.auth, credentials.variant, boundTo = address.url))
    }

    override suspend fun clear() = store.clear()

    override suspend fun signOut() = store.signOut()

    /** The address of a server whose login is missing, or is another address's, so setup can open on its sign-in. */
    private fun ActiveServer<SeerrLogin>.signedOutAddress(): String? = server.address.url.takeIf { login?.isFor(server) != true }

    private fun ActiveServer<SeerrLogin>.profileText(): String? = login?.takeIf { it.isFor(server) }?.profile

    private fun signedIn(
        server: SavedServer,
        login: SeerrLogin?,
    ): SeerrCredentials? = login?.takeIf { it.isFor(server) }?.let { SeerrCredentials(server.address.url, it.auth, it.variant) }

    /** A login saved for an address is the server's only while the server still has that address. A row with none is trusted. */
    private fun SeerrLogin.isFor(server: SavedServer): Boolean = boundTo == null || boundTo == server.address.url
}
