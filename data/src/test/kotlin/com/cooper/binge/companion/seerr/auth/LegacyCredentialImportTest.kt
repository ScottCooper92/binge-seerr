package com.cooper.binge.companion.seerr.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.homelab.core.foundation.LogLevel
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.server.connection.SecretCipher
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.cooper.homelab.core.server.connection.CredentialStore as LoginStore

/**
 * The one-time import, each test starting from a file the released version wrote: [LegacyCredentialStore] is that version's own
 * store, so the file is exactly the shape it left on a user's phone. The new stores are then opened over the same folder, as the
 * app does on the first start with the switch on.
 */
class LegacyCredentialImportTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val cipher = ReversingCipher()
    private var ids = 0
    private val warnings = mutableListOf<String>()
    private val logger = Logger { level, _, event, _, _ -> if (level == LogLevel.Warn) warnings += event }

    private inner class Phone(
        parent: CoroutineScope,
        testScope: TestScope,
    ) {
        private val job = Job()
        private val scope = CoroutineScope(parent.coroutineContext + job)

        /** Ends this install's DataStores, so a second one over the same files is the app starting again. */
        suspend fun stop() = job.cancelAndJoin()

        private fun file(name: String): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("$name.preferences_pb") }

        val legacyFile = file("seerr_credentials")
        val registryFile = file("seerr_servers")
        val loginsFile = file("seerr_server_logins")
        val deviceFile = file("seerr_device")
        val serverId = ServerIdentity(deviceFile)
        val io = UnconfinedTestDispatcher(testScope.testScheduler)
        val released = LegacyCredentialStore(legacyFile, cipher, io)
        val registry = ServerRegistry(registryFile, "registry", { "server-${++ids}" }, logger)
        val logins = LoginStore(loginsFile, SeerrLoginCodec, cipher, logger)
        val importer = LegacyCredentialImport(legacyFile, registry, logins, registryFile, serverId, logger)
        val store = RegistryCredentialStore(registry, logins, importer, serverId, io)
    }

    private fun TestScope.phone() = Phone(backgroundScope, this)

    private val apiKey = SeerrCredentials("https://seerr.example.com/", SeerrAuth.ApiKey("k3y"), SeerrVariant.Jellyseerr)
    private val session = SeerrCredentials("http://192.168.1.5:5055/", SeerrAuth.Session("c00kie", userId = 7), SeerrVariant.Seerr)
    private val shared =
        SeerrCredentials("https://seerr.example.com/", SeerrAuth.Session("c00kie", 9, shared = true), SeerrVariant.Overseerr)

    @Test
    fun `an api key the released version wrote leaves the user signed in to the same server`() =
        runTest {
            val phone = phone()
            phone.released.save(apiKey)

            assertEquals(apiKey, phone.store.credentials.first())
            assertEquals(
                1,
                phone.registry
                    .current()
                    .servers.size,
            )
        }

    @Test
    fun `a session the released version wrote leaves the user signed in to the same server and user`() =
        runTest {
            val phone = phone()
            phone.released.save(session)

            assertEquals(session, phone.store.credentials.first())
        }

    @Test
    fun `a shared session stays shared so a disconnect still does not end it`() =
        runTest {
            val phone = phone()
            phone.released.save(shared)

            assertEquals(shared, phone.store.credentials.first())
        }

    @Test
    fun `the secret is copied as the blob it is and nothing is decrypted`() =
        runTest {
            val phone = phone()
            phone.released.save(apiKey)
            cipher.decrypts = 0

            phone.importer.importOnce()

            assertEquals(0, cipher.decrypts)
            val blob =
                phone.loginsFile.data
                    .first()
                    .asMap()
                    .filterKeys { it.name.endsWith("/secret") }
                    .values
                    .single()
            assertEquals(phone.legacyFile.data.first()[stringPreferencesKey("secret")], blob)
        }

    @Test
    fun `running the import twice saves one server`() =
        runTest {
            val phone = phone()
            phone.released.save(apiKey)

            phone.importer.importOnce()
            phone.importer.importOnce()
            phone.stop()
            val restarted = Phone(backgroundScope, this)
            restarted.importer.importOnce()

            assertEquals(
                1,
                restarted.registry
                    .current()
                    .servers.size,
            )
        }

    @Test
    fun `an old file whose secret does not decrypt imports as a saved server that is signed out`() =
        runTest {
            val phone = phone()
            phone.released.save(apiKey)
            cipher.keyLost = true

            assertNull(phone.store.credentials.first())
            assertEquals(apiKey.baseUrl, phone.store.signedOutServer.first())
            assertEquals(
                1,
                phone.registry
                    .current()
                    .servers.size,
            )
        }

    @Test
    fun `a server the user signed out of imports as a saved server with no login`() =
        runTest {
            val phone = phone()
            phone.released.save(session)
            phone.released.signOut()

            assertNull(phone.store.credentials.first())
            assertEquals(session.baseUrl, phone.store.signedOutServer.first())
        }

    @Test
    fun `an empty old file imports nothing`() =
        runTest {
            val phone = phone()

            assertNull(phone.store.credentials.first())
            assertNull(phone.store.signedOutServer.first())
            assertTrue(
                phone.registry
                    .current()
                    .servers
                    .isEmpty(),
            )
        }

    @Test
    fun `the old file is only read and is unchanged afterwards`() =
        runTest {
            val phone = phone()
            phone.released.save(session)
            val before =
                phone.legacyFile.data
                    .first()
                    .asMap()

            phone.store.credentials.first()
            phone.store.signOut()
            phone.store.clear()

            assertEquals(
                before,
                phone.legacyFile.data
                    .first()
                    .asMap(),
            )
        }

    @Test
    fun `a user who signs out under the new stores is not signed back in from the old file at the next start`() =
        runTest {
            val phone = phone()
            phone.released.save(apiKey)
            phone.store.credentials.first()
            phone.store.clear()

            phone.stop()
            val restarted = Phone(backgroundScope, this)

            assertNull(restarted.store.credentials.first())
            assertTrue(
                restarted.registry
                    .current()
                    .servers
                    .isEmpty(),
            )
        }

    @Test
    fun `an address the shared library cannot read is not imported and is logged`() =
        runTest {
            val phone = phone()
            phone.legacyFile.updateData { prefs ->
                prefs.toMutablePreferences().also { it[stringPreferencesKey("signed_out_url")] = "ftp://nas/" }
            }

            assertNull(phone.store.signedOutServer.first())
            assertEquals(listOf("legacy-address-unreadable"), warnings)
        }

    private class ReversingCipher : SecretCipher {
        var keyLost = false
        var decrypts = 0

        override fun encrypt(plaintext: String): String = plaintext.reversed()

        override fun decrypt(ciphertext: String): String? {
            decrypts++
            return if (keyLost) null else ciphertext.reversed()
        }
    }
}
