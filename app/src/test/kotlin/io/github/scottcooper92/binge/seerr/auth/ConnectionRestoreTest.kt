package io.github.scottcooper92.binge.seerr.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Restoring a transferred connection, against a real HTTP server on the JVM. What is carried is only
 * a claim; these pin what the app does with a server that agrees, one that refuses, and one that
 * says nothing at all.
 */
class ConnectionRestoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val baseUrl = server.url("/").toString()
    private val carried = SeerrCredentials(baseUrl, SeerrAuth.ApiKey("k3y"))

    @After
    fun tearDown() = server.close()

    private fun store(scope: CoroutineScope) =
        CredentialStore(
            dataStore = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("creds.preferences_pb") },
            cipher = ReversingCipher,
        )

    private fun restore(
        scope: CoroutineScope,
        store: CredentialStore,
        carrier: ConnectionCarrier,
    ) = ConnectionRestore(store, SeerrApiFactory(logRequests = false), carrier, scope)

    @Test
    fun `a carried connection the server still accepts is proved, then saved`() =
        runTest {
            server.enqueue(json("""{"id":1,"permissions":2}"""))
            val store = store(backgroundScope)
            val carrier = FakeCarrier(carried)

            restore(backgroundScope, store, carrier).run()

            assertEquals(carried, store.credentials.first())
            assertEquals("k3y", server.takeRequest().headers["X-Api-Key"])
            // Still carried: the connection it describes is the one now in use.
            assertNotNull(carrier.held)
        }

    @Test
    fun `a server that refuses the carried credentials empties the carrier`() =
        runTest {
            server.enqueue(MockResponse(code = 401))
            val store = store(backgroundScope)
            val carrier = FakeCarrier(carried)

            restore(backgroundScope, store, carrier).run()

            assertNull(store.credentials.first())
            assertNull(carrier.held)
        }

    @Test
    fun `a server that cannot be reached keeps the carried connection for next time`() =
        runTest {
            server.close()
            val store = store(backgroundScope)
            val carrier = FakeCarrier(carried)

            restore(backgroundScope, store, carrier).run()

            assertNull(store.credentials.first())
            assertEquals(carried, carrier.held)
        }

    @Test
    fun `a device that is already connected is left alone, and the carrier never read`() =
        runTest {
            val store = store(backgroundScope)
            store.save(SeerrCredentials("https://saved.example/", SeerrAuth.ApiKey("local")))
            val carrier = FakeCarrier(carried)

            restore(backgroundScope, store, carrier).run()

            assertEquals("https://saved.example/", store.credentials.first()?.baseUrl)
            assertEquals(0, carrier.reads)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun `settling is what tells the home to stop waiting, and it happens even with nothing to restore`() =
        runTest {
            val carrier = FakeCarrier(held = null)
            val sut = restore(backgroundScope, store(backgroundScope), carrier)
            assertFalse(sut.settled.value)

            sut.run()

            assertTrue(sut.settled.value)
        }

    @Test
    fun `a second run does not go back to the carrier`() =
        runTest {
            server.enqueue(json("""{"id":1,"permissions":2}"""))
            val carrier = FakeCarrier(carried)
            val sut = restore(backgroundScope, store(backgroundScope), carrier)

            sut.run()
            sut.run()

            assertEquals(1, carrier.reads)
        }

    private class FakeCarrier(
        var held: SeerrCredentials?,
    ) : ConnectionCarrier {
        var reads = 0

        override suspend fun put(credentials: SeerrCredentials) {
            held = credentials
        }

        override suspend fun read(): SeerrCredentials? {
            reads++
            return held
        }

        override suspend fun clear() {
            held = null
        }
    }

    private fun json(body: String): MockResponse =
        MockResponse(code = 200, headers = okhttp3.Headers.headersOf("Content-Type", "application/json"), body = body)

    private object ReversingCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext.reversed()

        override fun decrypt(ciphertext: String): String = ciphertext.reversed()
    }
}
