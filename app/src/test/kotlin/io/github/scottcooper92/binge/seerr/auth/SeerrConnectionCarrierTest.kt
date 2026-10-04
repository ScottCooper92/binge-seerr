package io.github.scottcooper92.binge.seerr.auth

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.util.InMemoryDataStore
import io.github.scottcooper92.binge.seerr.util.enqueueProfile
import io.github.scottcooper92.binge.seerr.util.routeProfiles
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetAddress

/**
 * What a connect hands the carrier. The plain-HTTP opt-in goes with it only where the saved server
 * needs one, so a restore on a new device can reach the same server (#706).
 */
class SeerrConnectionCarrierTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer().apply { start() }.routeProfiles()
    private val cleartext = DataStoreCleartextConsent(InMemoryDataStore()) { null }
    private val carried = mutableListOf<CarriedCredentials>()

    @After
    fun tearDown() = server.close()

    private fun TestScope.connection() =
        SeerrConnection(
            store =
                CredentialStore(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c.preferences_pb") },
                    PlainCipher,
                ),
            apis = SeerrApiFactory(logRequests = false, cleartext = cleartext, testDns = { listOf(InetAddress.getLoopbackAddress()) }),
            carrier =
                object : ConnectionCarrier {
                    override suspend fun put(carried: CarriedCredentials) {
                        this@SeerrConnectionCarrierTest.carried += carried
                    }

                    override suspend fun read(): CarriedCredentials? = null

                    override suspend fun clear() = Unit
                },
            cleartext = cleartext,
        )

    private fun answerConnect() {
        server.enqueue(json("""{"id":1,"permissions":2}"""))
        server.enqueueProfile(json("""{"version":"3.0.0"}"""), json("""{"mediaServerType":2}"""))
    }

    @Test
    fun `a public plain-http server the user opted in for is carried with the opt-in`() =
        runTest {
            cleartext.grant(PUBLIC_HOST)
            answerConnect()

            connection().connect("http://$PUBLIC_HOST:${server.port}/", SeerrAuth.ApiKey("k3y")).getOrThrow()

            assertTrue(carried.single().cleartext)
        }

    @Test
    fun `a server that needs no opt-in is carried without one`() =
        runTest {
            answerConnect()

            val saved: SeerrCredentials = connection().connect(server.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()

            assertEquals(CarriedCredentials(saved, cleartext = false), carried.single())
            assertFalse(cleartext.allows(server.hostName))
        }

    private fun json(body: String) = MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }

    private companion object {
        const val PUBLIC_HOST = "seerr.example.com"
    }
}
