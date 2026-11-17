package com.cooper.binge.companion.seerr.auth

import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.server.connection.SavedServer
import com.cooper.homelab.core.server.connection.ServerRegistry
import kotlinx.coroutines.CancellationException
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
            .map { active -> active?.let { (server, login) -> login?.let { SeerrCredentials(server.address.url, it.auth, it.variant) } } }
            .flowOn(io)
            .distinctUntilChanged()

    override val signedOutServer: Flow<String?> =
        activeLogin()
            .map { active -> active?.let { (server, login) -> server.address.url.takeIf { login == null } } }
            .flowOn(io)
            .distinctUntilChanged()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun save(credentials: SeerrCredentials): Boolean {
        ready()
        val address = ServerAddress.parse(credentials.baseUrl) ?: return false
        val login = SeerrLogin(credentials.auth, credentials.variant)
        val existing = registry.current().activeServer
        val server =
            existing?.copy(address = address)?.also { registry.update(it) }
                ?: SavedServer(serverId.currentServerId(), address, address.host, SEERR_KIND).also { registry.restore(it) }
        return try {
            logins.save(server.id, login)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A keymaster that throws leaves nothing half-written: a server added for this attempt goes again.
            if (existing == null) registry.remove(server.id)
            false
        }
    }

    override suspend fun clear() {
        ready()
        registry.current().servers.forEach { server ->
            registry.remove(server.id)
            logins.remove(server.id)
        }
    }

    override suspend fun signOut() {
        ready()
        registry.current().activeServer?.let { logins.remove(it.id) }
    }

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
