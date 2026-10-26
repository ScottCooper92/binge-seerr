package com.cooper.binge.companion.seerr.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.coroutines.CoroutineContext

/**
 * The store on the JVM, over a real DataStore in a temp file and a reversible fake cipher: what is
 * pinned is the shape of a row and what an undecryptable secret reads as, not the Keystore.
 */
class CredentialStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val cipher = FakeCipher()

    private fun store(
        scope: kotlinx.coroutines.CoroutineScope,
        io: CoroutineDispatcher? = null,
    ): CredentialStore {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("creds.preferences_pb") }
        return if (io == null) CredentialStore(dataStore, cipher) else CredentialStore(dataStore, cipher, io)
    }

    @Test
    fun `the secret is decrypted on the io dispatcher, never on the collector's main thread`() =
        runTest {
            val main = Labelled("main", StandardTestDispatcher(testScheduler))
            val io = Labelled("io", StandardTestDispatcher(testScheduler))
            val store = store(backgroundScope, io)
            val saved = SeerrCredentials("http://seerr.local/", SeerrAuth.ApiKey("k3y"))
            store.save(saved)

            // The first screen collects on Main; the Keystore decrypt reads from disk, so it must not run there.
            assertEquals(saved, withContext(main) { store.credentials.first() })

            assertTrue("decrypt never ran", cipher.decryptedOn.isNotEmpty())
            assertEquals(listOf("io"), cipher.decryptedOn.distinct())
        }

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

    /** Runs every block on [delegate] with [name] in [current], so a plain function can tell which dispatcher called it. */
    private class Labelled(
        private val name: String,
        private val delegate: CoroutineDispatcher,
    ) : CoroutineDispatcher() {
        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) = delegate.dispatch(context) {
            val outer = current.get()
            current.set(name)
            try {
                block.run()
            } finally {
                current.set(outer)
            }
        }

        companion object {
            val current = ThreadLocal<String?>()
        }
    }

    private class FakeCipher : SecretCipher {
        var keyLost = false
        var encryptFails = false
        val decryptedOn = mutableListOf<String?>()

        override fun encrypt(plaintext: String): String {
            check(!encryptFails) { "keymaster unavailable" }
            return plaintext.reversed()
        }

        override fun decrypt(ciphertext: String): String? {
            decryptedOn += Labelled.current.get()
            return if (keyLost) null else ciphertext.reversed()
        }
    }
}
