package com.cooper.binge.companion.seerr.auth

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.server.connection.SavedServer
import com.cooper.homelab.core.server.connection.ServerRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.cooper.homelab.core.server.connection.CredentialStore as LoginStore

/** The word this app gives the saved server's `kind`. The library reads nothing into it. */
internal const val SEERR_KIND = "seerr"

/**
 * The saved connection in the shared library's per-server stores, as one server and one account: the active server of the
 * registry, and its login. This app has no switcher, so [save] replaces that server and [clear] removes it.
 *
 * The first read, and every write, runs [importer] once, so a user who updates has the connection the released version kept. The
 * secret is decrypted on [io], as [LegacyCredentialStore] does: the first screen collects on the main thread, and a Keystore decrypt
 * reads from disk.
 */
class RegistryCredentialStore(
    private val registry: ServerRegistry,
    private val logins: LoginStore<SeerrLogin>,
    private val importer: LegacyCredentialImport,
    private val serverId: ServerIdentity,
    private val io: CoroutineDispatcher,
) : CredentialStore {
    private val importing = Mutex()
    private val writing = Mutex()

    @Volatile
    private var imported = false

    private fun activeLogin(): Flow<Pair<SavedServer, SeerrLogin?>?> =
        registry.state
            .onStart { ready() }
            .flatMapLatest { state ->
                val server = state.activeServer
                if (server == null) flowOf(null) else logins.observe(server.id).map { server to it }
            }

    override val credentials: Flow<SeerrCredentials?> =
        activeLogin()
            .map { active -> active?.let { (server, login) -> signedIn(server, login) } }
            .flowOn(io)
            .distinctUntilChanged()

    override val signedOutServer: Flow<String?> =
        activeLogin()
            .map { active -> active?.let { (server, login) -> server.address.url.takeIf { login?.isFor(server) != true } } }
            .flowOn(io)
            .distinctUntilChanged()

    override val profile: Flow<StoredProfile?> =
        activeLogin()
            .map { active -> active?.let { (server, login) -> login?.takeIf { it.isFor(server) }?.profile } }
            .map(StoredProfile::decode)
            .flowOn(io)
            .distinctUntilChanged()

    override suspend fun saveProfile(
        credentials: SeerrCredentials,
        profile: StoredProfile,
    ) {
        ready()
        writing.withLock {
            val server = registry.current().activeServer?.takeIf { it.address.url == credentials.baseUrl } ?: return
            val login = attempt { logins.get(server.id) }.getOrNull()?.takeIf { it.isFor(server) && it.auth == credentials.auth } ?: return
            attempt { logins.save(server.id, login.copy(profile = profile.encode())) }
        }
    }

    override suspend fun save(credentials: SeerrCredentials): Boolean = writing.withLock { saveConnection(credentials) }

    private suspend fun saveConnection(credentials: SeerrCredentials): Boolean {
        ready()
        val address = ServerAddress.parse(credentials.baseUrl) ?: return false
        val existing = registry.current().activeServer
        val id = existing?.id ?: serverId.currentServerId()
        // The login goes first. It encrypts before it writes, so a keymaster that throws leaves the registry exactly as it was.
        // It carries the address it is for, so between the two writes it reads as signed out and never as the old server's.
        if (attempt { logins.save(id, SeerrLogin(credentials.auth, credentials.variant, boundTo = address.url)) }.isFailure) return false
        if (existing != null) {
            registry.update(existing.copy(address = address, name = address.host))
        } else {
            registry.restore(SavedServer(id, address, address.host, SEERR_KIND))
        }
        return true
    }

    override suspend fun clear() {
        ready()
        writing.withLock { forgetAll() }
    }

    private suspend fun forgetAll() {
        registry.current().servers.forEach { server ->
            registry.remove(server.id)
            logins.remove(server.id)
        }
    }

    override suspend fun signOut() {
        ready()
        writing.withLock { registry.current().activeServer?.let { logins.remove(it.id) } }
    }

    private fun signedIn(
        server: SavedServer,
        login: SeerrLogin?,
    ): SeerrCredentials? = login?.takeIf { it.isFor(server) }?.let { SeerrCredentials(server.address.url, it.auth, it.variant) }

    /** A login saved for an address is the server's only while the server still has that address. A row with none is trusted. */
    private fun SeerrLogin.isFor(server: SavedServer): Boolean = boundTo == null || boundTo == server.address.url

    /** Runs the one-time import before anything reads or writes, and never twice. */
    private suspend fun ready() {
        if (imported) return
        importing.withLock {
            if (!imported) {
                importer.importOnce()
                imported = true
            }
        }
    }
}
