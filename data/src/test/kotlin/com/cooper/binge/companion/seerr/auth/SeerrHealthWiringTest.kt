package com.cooper.binge.companion.seerr.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.util.ReversingCipher
import com.cooper.binge.companion.seerr.util.enqueueProfile
import com.cooper.binge.companion.seerr.util.routeProfiles
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.server.connection.CallResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val HTTP_UNAVAILABLE = 503

/**
 * The shared library's monitor behind [SeerrConnection.health]: what a call means is the library's rule, and what is checked
 * here is that the app feeds it, keys it by its one server, and adds "not connected" from the store.
 */
class SeerrHealthWiringTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer().routeProfiles().apply { start() }
    private val baseUrl = server.url("/").toString()
    private var stores = 0

    @After
    fun tearDown() = server.close()

    private fun TestScope.data(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("w${stores++}.preferences_pb") }

    private fun TestScope.connection(
        data: DataStore<Preferences> = data(),
        health: SeerrHealth = SeerrHealth(seerrHealthMonitor()) { ServerId("server-1") },
    ): SeerrConnection =
        SeerrConnection(
            store =
                LegacyCredentialStore(
                    data,
                    ReversingCipher,
                    UnconfinedTestDispatcher(testScheduler),
                ),
            apis = SeerrApiFactory(logRequests = false, health = health),
            healthMonitor = health,
        )

    private suspend fun SeerrConnection.call() {
        runCatching { api().authenticatedUser() }
    }

    private fun connect() {
        server.enqueue(MockResponse(code = 200, body = """{"id":1,"permissions":2}"""))
        server.enqueueProfile(
            MockResponse(code = 200, body = """{"version":"3.1.0"}"""),
            MockResponse(code = 200, body = """{"initialized":true}"""),
        )
    }

    @Test
    fun `nothing saved is not connected, then a connection is healthy`() =
        runTest {
            val sut = connection()
            assertEquals(SeerrConnectionHealth.NotConnected, sut.health.first())

            connect()
            sut.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()

            assertEquals(SeerrConnectionHealth.Healthy, sut.health.first())
        }

    @Test
    fun `one failed call is a blip, two in a row are unreachable, and an answer is healthy again`() =
        runTest {
            val sut = connection()
            connect()
            sut.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()

            server.enqueue(MockResponse(code = HTTP_UNAVAILABLE))
            sut.call()
            assertEquals(SeerrConnectionHealth.Healthy, sut.health.first())

            server.enqueue(MockResponse(code = HTTP_UNAVAILABLE))
            sut.call()
            assertEquals(SeerrConnectionHealth.Unreachable, sut.health.first())

            server.enqueue(MockResponse(code = 200, body = """{"id":1,"permissions":2}"""))
            sut.call()
            assertEquals(SeerrConnectionHealth.Healthy, sut.health.first())
        }

    @Test
    fun `an answer between two failures resets the run`() =
        runTest {
            val sut = connection()
            connect()
            sut.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()

            server.enqueue(MockResponse(code = HTTP_UNAVAILABLE))
            sut.call()
            server.enqueue(MockResponse(code = 200, body = """{"id":1,"permissions":2}"""))
            sut.call()
            server.enqueue(MockResponse(code = HTTP_UNAVAILABLE))
            sut.call()

            assertEquals(SeerrConnectionHealth.Healthy, sut.health.first())
        }

    @Test
    fun `a refused session is unauthorized at once, and disconnecting then connecting starts clean`() =
        runTest {
            val sut = connection()
            connect()
            sut.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()

            server.enqueue(MockResponse(code = 401))
            sut.call()
            assertEquals(SeerrConnectionHealth.Unauthorized, sut.health.first())

            sut.disconnect()
            assertEquals(SeerrConnectionHealth.NotConnected, sut.health.first())

            connect()
            sut.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()
            assertEquals(SeerrConnectionHealth.Healthy, sut.health.first())
        }

    @Test
    fun `a cold start over saved credentials is unchecked until a call speaks to the server`() =
        runTest {
            val data = data()
            val first = connection(data)
            connect()
            first.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()

            val restarted = connection(data)

            assertEquals(SeerrConnectionHealth.Unchecked, restarted.health.first())
        }

    @Test
    fun `a server that asks for a client certificate reads as such only once it is unreachable`() =
        runTest {
            val monitor = seerrHealthMonitor()
            val health = SeerrHealth(monitor) { ServerId("server-1") }
            val sut = connection(health = health)
            connect()
            sut.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()

            health.reportClientCertificate(true)
            monitor.report(ServerId("server-1"), CallResult.TransportFailure())
            assertEquals(SeerrConnectionHealth.Healthy, sut.health.first())

            health.reportClientCertificate(true)
            monitor.report(ServerId("server-1"), CallResult.TransportFailure())
            assertEquals(SeerrConnectionHealth.ClientCertificateNeeded, sut.health.first())
        }

    @Test
    fun `a different failure, an answer or a new connection clears the client certificate reading`() =
        runTest {
            val monitor = seerrHealthMonitor()
            val health = SeerrHealth(monitor) { ServerId("server-1") }
            val sut = connection(health = health)
            connect()
            sut.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()
            repeat(2) {
                health.reportClientCertificate(true)
                monitor.report(ServerId("server-1"), CallResult.TransportFailure())
            }

            health.reportClientCertificate(false)
            monitor.report(ServerId("server-1"), CallResult.TransportFailure())
            assertEquals(SeerrConnectionHealth.Unreachable, sut.health.first())

            health.reportClientCertificate(true)
            monitor.report(ServerId("server-1"), CallResult.TransportFailure())
            assertEquals(SeerrConnectionHealth.ClientCertificateNeeded, sut.health.first())

            health.reportClientCertificate(false)
            monitor.report(ServerId("server-1"), CallResult.Answered(200))
            assertEquals(SeerrConnectionHealth.Healthy, sut.health.first())

            repeat(2) {
                health.reportClientCertificate(true)
                monitor.report(ServerId("server-1"), CallResult.TransportFailure())
            }
            sut.disconnect()
            connect()
            sut.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()
            assertEquals(SeerrConnectionHealth.Healthy, sut.health.first())
        }
}
