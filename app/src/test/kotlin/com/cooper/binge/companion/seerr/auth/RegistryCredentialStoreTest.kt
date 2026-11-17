package com.cooper.binge.companion.seerr.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.binge.companion.kit.SecretCipher
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.server.connection.ServerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.cooper.homelab.core.server.connection.CredentialStore as LoginStore

/**
 * The registry-backed store beside the one the released versions shipped. Every file is a real DataStore in a temp folder. The
 * cipher is a reversible fake, so what is pinned is what is stored and what a lost key reads as, not the Keystore.
 */
class RegistryCredentialStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val cipher = ReversingCipher()
    private var ids = 0

    /** All the files of one install, so a second [Install] over the same [folder] is the app restarting. */
    private inner class Install(
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
        val legacy = LegacyCredentialStore(legacyFile, cipher, UnconfinedTestDispatcher(testScope.testScheduler))
        val registry = ServerRegistry(registryFile, "registry", { "server-${++ids}" }, Logger { _, _, _, _, _ -> })
        val logins = LoginStore(loginsFile, SeerrLoginCodec, cipher.forLibrary(), Logger { _, _, _, _, _ -> })
        val importer =
            LegacyCredentialImport(
                legacyFile,
                registry,
                logins,
                registryFile,
                { "server-${++ids}" },
                Logger {
                    _,
                    _,
                    _,
                    _,
                    _,
                    ->
                },
            )
        val store = RegistryCredentialStore(registry, logins, importer, UnconfinedTestDispatcher(testScope.testScheduler))
    }

    private fun TestScope.install() = Install(backgroundScope, this)

    private val apiKey = SeerrCredentials("https://seerr.example.com/", SeerrAuth.ApiKey("k3y"), SeerrVariant.Jellyseerr)
    private val session = SeerrCredentials("http://192.168.1.5:5055/", SeerrAuth.Session("c00kie", userId = 7), SeerrVariant.Seerr)
    private val shared =
        SeerrCredentials("https://seerr.example.com/", SeerrAuth.Session("c00kie", 9, shared = true), SeerrVariant.Overseerr)

    // The same shapes the released store round-trips, through the new one.

    @Test
    fun `an api key a session and a shared session each round-trip with the variant`() =
        runTest {
            for (saved in listOf(apiKey, session, shared)) {
                val install = Install(backgroundScope, this)

                assertTrue(install.store.save(saved))

                assertEquals(saved, install.store.credentials.first())
                install.store.clear()
                install.stop()
            }
        }

    @Test
    fun `saving again replaces the connection and keeps one server`() =
        runTest {
            val install = install()
            install.store.save(apiKey)

            install.store.save(session)

            assertEquals(session, install.store.credentials.first())
            assertEquals(
                1,
                install.registry
                    .current()
                    .servers.size,
            )
        }

    @Test
    fun `signing out forgets the secret and keeps the address for setup`() =
        runTest {
            val install = install()
            install.store.save(apiKey)

            install.store.signOut()

            assertNull(install.store.credentials.first())
            assertEquals(apiKey.baseUrl, install.store.signedOutServer.first())
            assertNull(
                install.logins.parts(
                    install.registry
                        .current()
                        .servers
                        .single()
                        .id,
                ),
            )
        }

    @Test
    fun `a new connection forgets the signed out server and clear forgets everything`() =
        runTest {
            val install = install()
            install.store.save(apiKey)
            install.store.signOut()
            install.store.save(session)
            assertNull(install.store.signedOutServer.first())

            install.store.clear()

            assertNull(install.store.credentials.first())
            assertNull(install.store.signedOutServer.first())
            assertTrue(
                install.registry
                    .current()
                    .servers
                    .isEmpty(),
            )
        }

    @Test
    fun `a secret that no longer decrypts reads as signed out of that server`() =
        runTest {
            val install = install()
            install.store.save(apiKey)

            cipher.keyLost = true

            assertNull(install.store.credentials.first())
            assertEquals(apiKey.baseUrl, install.store.signedOutServer.first())
        }

    @Test
    fun `a failed encryption stores nothing and leaves no server behind`() =
        runTest {
            val install = install()
            cipher.encryptFails = true

            assertFalse(install.store.save(apiKey))

            assertNull(install.store.credentials.first())
            assertTrue(
                install.registry
                    .current()
                    .servers
                    .isEmpty(),
            )
        }

    @Test
    fun `an address that is not one is not saved`() =
        runTest {
            val install = install()

            assertFalse(install.store.save(SeerrCredentials("", SeerrAuth.ApiKey("k3y"))))
            assertTrue(
                install.registry
                    .current()
                    .servers
                    .isEmpty(),
            )
        }

    @Test
    fun `the connection survives the app restarting`() =
        runTest {
            val first = install()
            first.store.save(session)

            first.stop()
            // A second set of stores over the same files, as after a process restart.
            val second = Install(backgroundScope, this)

            assertEquals(session, second.store.credentials.first())
        }

    @Test
    fun `the stored fields are the ones the importer writes`() {
        val parts = SeerrLoginCodec.split(SeerrLogin(SeerrAuth.Session("c", 3, shared = true), SeerrVariant.Seerr))

        assertEquals(
            mapOf("kind" to "session", "userId" to "3", "shared" to "true", "variant" to "Seerr"),
            parts.fields,
        )
        assertEquals(
            mapOf("kind" to "api_key", "variant" to "Unknown"),
            SeerrLoginCodec.split(SeerrLogin(SeerrAuth.ApiKey("k"), SeerrVariant.Unknown)).fields,
        )
    }

    @Test
    fun `fields this version does not know read as no login`() {
        assertNull(SeerrLoginCodec.join(mapOf("kind" to "oauth"), "s"))
        assertNull(SeerrLoginCodec.join(mapOf("kind" to "session"), "s"))
        assertEquals(SeerrVariant.Unknown, SeerrLoginCodec.join(mapOf("kind" to "api_key", "variant" to "Nope"), "s")?.variant)
    }

    private class ReversingCipher : SecretCipher {
        var keyLost = false
        var encryptFails = false

        override fun encrypt(plaintext: String): String {
            check(!encryptFails) { "keymaster unavailable" }
            return plaintext.reversed()
        }

        override fun decrypt(ciphertext: String): String? = if (keyLost) null else ciphertext.reversed()
    }
}
