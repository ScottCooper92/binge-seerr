package com.cooper.binge.companion.seerr.ui.requests

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrRequestMediaDto
import com.cooper.binge.companion.seerr.seerr.SeerrServiceSettingsDto
import com.cooper.binge.companion.seerr.util.FakeRequest
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.binge.companion.seerr.util.PlainCipher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

private const val ADMIN = 2
private const val MANAGE_REQUESTS = 1 shl 4

private fun json(body: String) = FakeResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)

private fun service(syncEnabled: Boolean) = SeerrServiceSettingsDto(id = 0, syncEnabled = syncEnabled)

private fun instance(
    id: Int,
    syncEnabled: Boolean,
    is4k: Boolean = false,
) = SeerrServiceSettingsDto(id = id, syncEnabled = syncEnabled, is4k = is4k)

private fun tv(id: Int) = DownloadQueueGate.ScanningInstance(RequestMediaType.Tv, is4k = false, id = id)

/** What the default fixtures read as: Radarr 0, and Sonarr 0 and 1, all scanning. */
private val scanningEverywhere =
    DownloadQueueGate(
        movies = true,
        tv = true,
        scanning = setOf(DownloadQueueGate.ScanningInstance(RequestMediaType.Movie, is4k = false, id = 0), tv(0), tv(1)),
    )

private fun movie(
    serviceId: Int? = null,
    serviceId4k: Int? = null,
) = SeerrRequestMediaDto(tmdbId = 1, mediaType = "movie", serviceId = serviceId, serviceId4k = serviceId4k)

class DownloadQueueGateTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = FakeSeerrServer()
    private val received = CopyOnWriteArrayList<FakeRequest>()
    private val permissions = AtomicInteger(ADMIN)
    private var radarr = """[{"id":0,"syncEnabled":true}]"""
    private var sonarr = """[{"id":0,"syncEnabled":true},{"id":1,"syncEnabled":true}]"""
    private var settingsStatus = 200

    @After
    fun tearDown() = seerr.awaitIdle()

    private suspend fun TestScope.gates(): DownloadQueueGates {
        seerr.dispatcher = { request ->
            received += request
            when (request.method + " " + request.url.encodedPath) {
                "GET /api/v1/auth/me" -> json("""{"id":7,"permissions":${permissions.get()}}""")
                "GET /api/v1/status" -> json("""{"version":"3.1.0"}""")
                "GET /api/v1/settings/public" -> json("""{"mediaServerType":2}""")
                "GET /api/v1/settings/radarr" -> if (settingsStatus == 200) json(radarr) else FakeResponse(code = settingsStatus)
                "GET /api/v1/settings/sonarr" -> if (settingsStatus == 200) json(sonarr) else FakeResponse(code = settingsStatus)
                else -> FakeResponse(code = 404)
            }
        }
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("g.preferences_pb") },
                        PlainCipher,
                        UnconfinedTestDispatcher(testScheduler),
                    ),
                apis = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher),
            )
        connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
        return DownloadQueueGates(connection)
    }

    private fun settingsReads() = received.count { it.url.encodedPath.startsWith("/api/v1/settings/radarr") }

    @Test
    fun `a version is seen by its own instance, so a mixed setup reads right on the scanning one`() {
        val gate =
            DownloadQueueGate.from(
                radarr = listOf(instance(1, syncEnabled = true), instance(2, syncEnabled = false, is4k = true)),
                sonarr = emptyList(),
            )

        assertFalse(gate.sees(RequestMediaType.Movie))
        assertTrue(gate.sees(movie(serviceId = 1, serviceId4k = 2), is4k = false))
        assertFalse(gate.sees(movie(serviceId = 1, serviceId4k = 2), is4k = true))
    }

    @Test
    fun `an instance the settings do not list reads neutral, and so does a version sent nowhere`() {
        val gate =
            DownloadQueueGate.from(
                radarr = listOf(instance(1, syncEnabled = true), instance(2, syncEnabled = false)),
                sonarr = emptyList(),
            )

        assertFalse(gate.sees(movie(serviceId = 9), is4k = false))
        assertFalse(gate.sees(movie(serviceId = 2), is4k = false))
        assertFalse(gate.sees(movie(), is4k = false))
    }

    @Test
    fun `a 4K instance is not mistaken for a standard one with the same id`() {
        val gate = DownloadQueueGate.from(radarr = listOf(instance(1, syncEnabled = true, is4k = true)), sonarr = emptyList())

        assertTrue(gate.sees(movie(serviceId4k = 1), is4k = true))
        assertFalse(gate.sees(movie(serviceId = 1), is4k = false))
    }

    @Test
    fun `no instance id falls back to the type's answer, and an unknown gate sees nothing`() {
        val all = DownloadQueueGate.from(radarr = listOf(instance(1, syncEnabled = true)), sonarr = emptyList())

        assertTrue(all.sees(movie(), is4k = false))
        assertFalse(DownloadQueueGate.Unknown.sees(movie(serviceId = 1), is4k = false))
    }

    @Test
    fun `a type is seen only when every one of its instances scans`() {
        val gate = DownloadQueueGate.from(radarr = listOf(service(true)), sonarr = listOf(service(true), service(false)))

        assertTrue(gate.sees(RequestMediaType.Movie))
        assertFalse("one Sonarr with scan off hides the queue of a show", gate.sees(RequestMediaType.Tv))
        assertTrue(gate.sees("movie"))
        assertFalse(gate.sees("person"))
    }

    @Test
    fun `no instance at all is not a queue to trust`() {
        val gate = DownloadQueueGate.from(radarr = emptyList(), sonarr = emptyList())

        assertFalse(gate.sees(RequestMediaType.Movie))
        assertFalse(gate.sees(RequestMediaType.Tv))
    }

    @Test
    fun `an administrator reads the gate from the services settings`() =
        runTest {
            assertEquals(scanningEverywhere, gates().current())
        }

    @Test
    fun `a scan that is off is not seen`() =
        runTest {
            radarr = """[{"id":0,"syncEnabled":false}]"""
            assertEquals(DownloadQueueGate(movies = false, tv = true, scanning = setOf(tv(0), tv(1))), gates().current())
        }

    @Test
    fun `anyone but an administrator is told nothing, and the settings are not asked for`() =
        runTest {
            permissions.set(MANAGE_REQUESTS)
            assertEquals(DownloadQueueGate.Unknown, gates().current())
            assertEquals(0, settingsReads())
        }

    @Test
    fun `a failed read is unknown, kept for a few seconds so a list asks once, and then asked again`() =
        runTest {
            settingsStatus = 500
            var now = 0L
            val gates = gates().also { it.clock = { now } }
            repeat(5) { assertEquals(DownloadQueueGate.Unknown, gates.current()) }
            assertEquals("five rows ask once", 1, settingsReads())

            settingsStatus = 200
            now += 16_000
            assertEquals(scanningEverywhere, gates.current())
        }

    @Test
    fun `a read is kept for a minute and then asked again`() =
        runTest {
            var now = 0L
            val gates = gates().also { it.clock = { now } }
            gates.current()
            gates.current()
            assertEquals(1, settingsReads())

            now += 61_000
            gates.current()
            assertEquals(2, settingsReads())
        }
}
