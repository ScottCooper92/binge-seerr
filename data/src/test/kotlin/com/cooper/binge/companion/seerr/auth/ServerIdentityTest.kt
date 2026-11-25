package com.cooper.binge.companion.seerr.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.server.connection.ServerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.cooper.binge.companion.seerr.auth.LegacyCredentialStore as Legacy
import com.cooper.homelab.core.server.connection.CredentialStore as LoginStore

/** The one id the app keys its server by, whichever store holds the connection. */
class ServerIdentityTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun file(
        scope: CoroutineScope,
        name: String,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("$name.preferences_pb") }

    @Test
    fun `the id is minted once and is the same after a restart`() =
        runTest {
            val first = Job()
            val one = ServerIdentity(file(CoroutineScope(backgroundScope.coroutineContext + first), "seerr_device")).currentServerId()
            first.cancelAndJoin()

            val two = ServerIdentity(file(backgroundScope, "seerr_device")).currentServerId()

            assertEquals(one, two)
        }

    @Test
    fun `an id already in the file is read as it is`() =
        runTest {
            val device = file(backgroundScope, "seerr_device")
            device.edit { it[stringPreferencesKey("current_server_id")] = "kept-id" }

            assertEquals("kept-id", ServerIdentity(device).currentServerId().value)
        }

    @Test
    fun `the id does not depend on the plex client identifier in the same file`() =
        runTest {
            val device = file(backgroundScope, "seerr_device")

            val plex = DeviceIdentityStore(device).plexClientIdentifier()

            assertNotEquals(plex, ServerIdentity(device).currentServerId().value)
        }

    @Test
    fun `the imported server and a server saved later both have that id`() =
        runTest {
            val scope = backgroundScope
            val io = UnconfinedTestDispatcher(testScheduler)
            val cipher = reversing()
            val device = file(scope, "seerr_device")
            val registryFile = file(scope, "seerr_servers")
            val legacyFile = file(scope, "seerr_credentials")
            val logger = Logger { _, _, _, _, _ -> }
            val serverId = ServerIdentity(device)
            val registry = ServerRegistry(registryFile, "registry", { "unused" }, logger)
            val logins = LoginStore(file(scope, "seerr_server_logins"), SeerrLoginCodec, cipher, logger)
            val store =
                RegistryCredentialStore(
                    registry,
                    logins,
                    LegacyCredentialImport(legacyFile, registry, logins, registryFile, serverId, logger),
                    serverId,
                    io,
                )
            Legacy(legacyFile, cipher, io).save(SeerrCredentials("https://a.example.com/", SeerrAuth.ApiKey("k")))

            store.credentials.first()
            val imported =
                registry
                    .current()
                    .servers
                    .single()
                    .id
            store.clear()
            store.save(SeerrCredentials("https://b.example.com/", SeerrAuth.ApiKey("k2")))

            assertEquals(serverId.currentServerId(), imported)
            assertEquals(
                imported,
                registry
                    .current()
                    .servers
                    .single()
                    .id,
            )
        }

    private fun reversing() =
        object : com.cooper.homelab.core.server.connection.SecretCipher {
            override fun encrypt(plaintext: String) = plaintext.reversed()

            override fun decrypt(ciphertext: String): String? = ciphertext.reversed()
        }
}
