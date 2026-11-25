package com.cooper.binge.companion.seerr.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.ReversingCipher
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.server.connection.SavedServer
import com.cooper.homelab.core.server.connection.ServerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.cooper.homelab.core.server.connection.CredentialStore as LoginStore

private const val ADDRESS = "https://seerr.example.com/"
private const val SERVER_ID = "server-from-the-last-version"

/**
 * An upgrade: the registry and the login are written the way the previous version wrote them, straight to the shared library's
 * stores, and a new process reads them through [RegistryCredentialStore]. The user must still be signed in to the same server.
 */
class RegistryCredentialStoreUpgradeTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val credentials = SeerrCredentials(ADDRESS, SeerrAuth.ApiKey("k3y"), SeerrVariant.Jellyseerr)
    private val quiet = Logger { _, _, _, _, _ -> }

    private inner class Install(
        parent: CoroutineScope,
        testScope: TestScope,
    ) {
        private val job = Job()
        private val scope = CoroutineScope(parent.coroutineContext + job)

        suspend fun stop() = job.cancelAndJoin()

        private fun file(name: String): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("$name.preferences_pb") }

        val legacyFile = file("seerr_credentials")
        val registryFile = file("seerr_servers")
        val deviceFile = file("seerr_device")
        val registry = ServerRegistry(registryFile, "registry", { "minted" }, quiet)
        val logins = LoginStore(file("seerr_server_logins"), SeerrLoginCodec, ReversingCipher, quiet)
        private val serverId = ServerIdentity(deviceFile)
        private val importer = LegacyCredentialImport(legacyFile, registry, logins, registryFile, serverId, quiet)
        val store = RegistryCredentialStore(registry, logins, importer, serverId, UnconfinedTestDispatcher(testScope.testScheduler))
    }

    /** What the previous version's save left: the login first, bound to its address, then the server in the registry. */
    private suspend fun Install.writeAsThePreviousVersionDid(login: SeerrLogin?) {
        val address = checkNotNull(ServerAddress.parse(ADDRESS))
        login?.let { logins.save(ServerId(SERVER_ID), it) }
        registry.restore(SavedServer(ServerId(SERVER_ID), address, address.host, SEERR_KIND))
    }

    @Test
    fun `a connection the previous version saved is still signed in after the upgrade`() =
        runTest {
            val before = Install(backgroundScope, this)
            before.writeAsThePreviousVersionDid(SeerrLogin(credentials.auth, credentials.variant, boundTo = ADDRESS))
            before.stop()

            val after = Install(backgroundScope, this)

            assertEquals(credentials, after.store.credentials.first())
            assertNull(after.store.signedOutServer.first())
        }

    @Test
    fun `the server keeps its id and name when the user saves again after the upgrade`() =
        runTest {
            val before = Install(backgroundScope, this)
            before.writeAsThePreviousVersionDid(SeerrLogin(credentials.auth, credentials.variant, boundTo = ADDRESS))
            before.stop()
            val after = Install(backgroundScope, this)
            val again = SeerrCredentials(ADDRESS, SeerrAuth.ApiKey("fresh"), SeerrVariant.Jellyseerr)

            after.store.save(again)

            assertEquals(again, after.store.credentials.first())
            val servers = after.registry.current().servers
            assertEquals(listOf(SERVER_ID), servers.map { it.id.value })
            assertEquals(listOf("seerr.example.com"), servers.map { it.name })
        }

    @Test
    fun `a profile kept by the previous version reads back after the upgrade`() =
        runTest {
            val record = StoredProfile(savedAt = 5L, variant = "Jellyseerr", version = "2.7.3")
            val before = Install(backgroundScope, this)
            before.writeAsThePreviousVersionDid(
                SeerrLogin(credentials.auth, credentials.variant, boundTo = ADDRESS, profile = record.encode()),
            )
            before.stop()

            val after = Install(backgroundScope, this)

            assertEquals(record, after.store.profile.first())
        }

    @Test
    fun `a server the user had signed out of stays signed out on its address`() =
        runTest {
            val before = Install(backgroundScope, this)
            before.writeAsThePreviousVersionDid(login = null)
            before.stop()

            val after = Install(backgroundScope, this)

            assertNull(after.store.credentials.first())
            assertEquals(ADDRESS, after.store.signedOutServer.first())
        }

    @Test
    fun `the old file does not sign the user back in once the import has run`() =
        runTest {
            val before = Install(backgroundScope, this)
            before.writeAsThePreviousVersionDid(login = null)
            before.registryFile.edit { it[booleanPreferencesKey("legacy_import_done")] = true }
            before.legacyFile.edit {
                it[stringPreferencesKey("base_url")] = ADDRESS
                it[stringPreferencesKey("auth_type")] = "api_key"
                it[stringPreferencesKey("secret")] = "stale"
            }
            before.stop()

            val after = Install(backgroundScope, this)

            assertNull(after.store.credentials.first())
            assertEquals(ADDRESS, after.store.signedOutServer.first())
        }
}
