package io.github.scottcooper92.binge.seerr.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The store on the JVM, over a real DataStore in a temp file and a reversible fake cipher: what is
 * pinned is the shape of a row and what an undecryptable secret reads as, not the Keystore.
 */
class CredentialStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val cipher = FakeCipher()

    private fun store(scope: kotlinx.coroutines.CoroutineScope): CredentialStore =
        CredentialStore(
            dataStore = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("creds.preferences_pb") },
            cipher = cipher,
        )

    @Test
    fun `an api key round-trips, with the variant`() =
        runTest {
            val store = store(backgroundScope)
            val saved = SeerrCredentials("http://seerr.local/", SeerrAuth.ApiKey("k3y"), SeerrVariant.Jellyseerr)

            assertEquals(true, store.save(saved))

            assertEquals(saved, store.credentials.first())
        }

    @Test
    fun `a session round-trips with its user id`() =
        runTest {
            val store = store(backgroundScope)
            val saved = SeerrCredentials("http://seerr.local/", SeerrAuth.Session("c00kie", userId = 7))

            store.save(saved)

            assertEquals(saved, store.credentials.first())
        }

    @Test
    fun `a secret that no longer decrypts reads as not connected`() =
        runTest {
            val store = store(backgroundScope)
            store.save(SeerrCredentials("http://seerr.local/", SeerrAuth.ApiKey("k3y")))

            cipher.keyLost = true

            assertNull(store.credentials.first())
        }

    @Test
    fun `a failed encryption stores nothing`() =
        runTest {
            val store = store(backgroundScope)
            cipher.encryptFails = true

            assertFalse(store.save(SeerrCredentials("http://seerr.local/", SeerrAuth.ApiKey("k3y"))))
            assertNull(store.credentials.first())
        }

    @Test
    fun `clear forgets the connection`() =
        runTest {
            val store = store(backgroundScope)
            store.save(SeerrCredentials("http://seerr.local/", SeerrAuth.ApiKey("k3y")))

            store.clear()

            assertNull(store.credentials.first())
        }

    private class FakeCipher : SecretCipher {
        var keyLost = false
        var encryptFails = false

        override fun encrypt(plaintext: String): String {
            check(!encryptFails) { "keymaster unavailable" }
            return plaintext.reversed()
        }

        override fun decrypt(ciphertext: String): String? = if (keyLost) null else ciphertext.reversed()
    }
}
