package io.github.scottcooper92.binge.seerr.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.util.InMemoryDataStore
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
import java.net.InetAddress

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

    /** Real consent, shared by the factory's guard and the restore, as the app wires them. */
    private val cleartext = DataStoreCleartextConsent(InMemoryDataStore()) { null }

    /** A public plain-HTTP address for [server]: every name resolves to it, so the guard judges a real request. */
    private val publicCarried = SeerrCredentials("http://$PUBLIC_HOST:${server.port}/", SeerrAuth.ApiKey("k3y"))

    private fun restore(
        scope: CoroutineScope,
        store: CredentialStore,
        carrier: ConnectionCarrier,
        consent: CleartextConsent = cleartext,
    ) = ConnectionRestore(
        store,
        SeerrApiFactory(logRequests = false, cleartext = consent, testDns = { listOf(InetAddress.getLoopbackAddress()) }),
        carrier,
        scope,
        consent,
    )

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
            assertEquals(carried, carrier.held?.credentials)
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
            val carrier = FakeCarrier(credentials = null)
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

    @Test
    fun `a carried plain-http opt-in is granted for the probe and kept with the restored connection`() =
        runTest {
            server.enqueue(json("""{"id":1,"permissions":2}"""))
            val store = store(backgroundScope)

            restore(backgroundScope, store, FakeCarrier(publicCarried, cleartext = true)).run()

            assertEquals(publicCarried, store.credentials.first())
            assertTrue(cleartext.allows(PUBLIC_HOST))
        }

    @Test
    fun `without a carried opt-in a public plain-http connection is refused, and kept for a later setup`() =
        runTest {
            val store = store(backgroundScope)
            val carrier = FakeCarrier(publicCarried)

            restore(backgroundScope, store, carrier).run()

            assertNull(store.credentials.first())
            assertEquals(0, server.requestCount)
            assertFalse(cleartext.allows(PUBLIC_HOST))
            assertEquals(publicCarried, carrier.held?.credentials)
        }

    @Test
    fun `a carried opt-in is dropped again when the server refuses the connection`() =
        runTest {
            server.enqueue(MockResponse(code = 401))
            val carrier = FakeCarrier(publicCarried, cleartext = true)

            restore(backgroundScope, store(backgroundScope), carrier).run()

            assertFalse(cleartext.allows(PUBLIC_HOST))
            assertNull(carrier.held)
        }

    @Test
    fun `a carried opt-in is dropped again when the server cannot be reached`() =
        runTest {
            val unreachable = publicCarried
            server.close()
            val carrier = FakeCarrier(unreachable, cleartext = true)

            restore(backgroundScope, store(backgroundScope), carrier).run()

            assertFalse(cleartext.allows(PUBLIC_HOST))
            assertEquals(unreachable, carrier.held?.credentials)
        }

    /** #721: a connection carried before the payload held the opt-in gets it on the next start. */
    @Test
    fun `a carrier missing the opt-in the saved public plain-http server holds is rewritten with it`() =
        runTest {
            val store = store(backgroundScope)
            store.save(publicCarried)
            cleartext.grant(PUBLIC_HOST)
            val carrier = FakeCarrier(publicCarried)

            restore(backgroundScope, store, carrier).refreshCarrier()

            assertEquals(CarriedCredentials(publicCarried, cleartext = true), carrier.held)
        }

    /** A user whose consent was grandfathered in never saw the opt-in, so no save ever carried it. */
    @Test
    fun `a grandfathered consent is carried on the next start`() =
        runTest {
            val store = store(backgroundScope)
            store.save(publicCarried)
            val grandfathered = DataStoreCleartextConsent(InMemoryDataStore()) { publicCarried.baseUrl }
            val carrier = FakeCarrier(publicCarried)

            restore(backgroundScope, store, carrier, grandfathered).refreshCarrier()

            assertEquals(CarriedCredentials(publicCarried, cleartext = true), carrier.held)
        }

    @Test
    fun `a carrier already in step is not written again`() =
        runTest {
            val store = store(backgroundScope)
            store.save(publicCarried)
            cleartext.grant(PUBLIC_HOST)
            val carrier = FakeCarrier(publicCarried, cleartext = true)

            restore(backgroundScope, store, carrier).refreshCarrier()

            assertEquals(0, carrier.puts)
        }

    /** The opt-in is carried only for a host that needs it: a server reached over https never carries one. */
    @Test
    fun `a carrier for a server that needs no opt-in is left without one`() =
        runTest {
            val store = store(backgroundScope)
            val secure = SeerrCredentials("https://$PUBLIC_HOST/", SeerrAuth.ApiKey("k3y"))
            store.save(secure)
            cleartext.grant(PUBLIC_HOST)
            val carrier = FakeCarrier(secure)

            restore(backgroundScope, store, carrier).refreshCarrier()

            assertEquals(0, carrier.puts)
            assertEquals(CarriedCredentials(secure, cleartext = false), carrier.held)
        }

    @Test
    fun `a device with nothing saved leaves the carrier alone`() =
        runTest {
            val carrier = FakeCarrier(publicCarried)

            restore(backgroundScope, store(backgroundScope), carrier).refreshCarrier()

            assertEquals(0, carrier.reads)
            assertEquals(CarriedCredentials(publicCarried), carrier.held)
        }

    private class FakeCarrier(
        credentials: SeerrCredentials?,
        cleartext: Boolean = false,
    ) : ConnectionCarrier {
        var held: CarriedCredentials? = credentials?.let { CarriedCredentials(it, cleartext) }
        var reads = 0
        var puts = 0

        override suspend fun put(carried: CarriedCredentials) {
            puts++
            held = carried
        }

        override suspend fun read(): CarriedCredentials? {
            reads++
            return held
        }

        override suspend fun clear() {
            held = null
        }
    }

    private fun json(body: String): MockResponse =
        MockResponse(code = 200, headers = okhttp3.Headers.headersOf("Content-Type", "application/json"), body = body)

    private companion object {
        const val PUBLIC_HOST = "seerr.example.com"
    }

    private object ReversingCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext.reversed()

        override fun decrypt(ciphertext: String): String = ciphertext.reversed()
    }
}
