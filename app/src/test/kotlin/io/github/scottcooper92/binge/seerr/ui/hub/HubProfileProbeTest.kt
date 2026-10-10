package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealthMonitor
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.util.FakeResponse
import io.github.scottcooper92.binge.seerr.util.FakeSeerrServer
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.PlainCipher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val STATUS = "/api/v1/status"

/**
 * The hub's server card and its overview both read the server's profile off one connection, behind
 * one lock, and an unreachable server's profile is never cached (#635). Counting the probes is the
 * assertion: each is a full connect timeout in the field, so two in a row is the problem screen
 * arriving twice as late.
 */
class HubProfileProbeTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()
    private val serverDown = AtomicBoolean(false)
    private val statusReads = AtomicInteger()

    @Before
    fun setUp() {
        seerr.dispatcher = { request ->
            if (request.url.encodedPath == STATUS) statusReads.incrementAndGet()
            if (serverDown.get()) throw IOException("unreachable")
            val body =
                when (request.url.encodedPath) {
                    STATUS -> """{"version":"3.4.0"}"""
                    "/api/v1/settings/public" -> """{"applicationTitle":"Family","mediaServerType":2}"""
                    "/api/v1/auth/me" -> """{"id":1,"displayName":"Scott","permissions":2}"""
                    else -> null
                }
            body?.let { FakeResponse(headers = headersOf("Content-Type", "application/json"), body = it) } ?: FakeResponse(code = 404)
        }
    }

    @After
    fun tearDown() {
        seerr.awaitIdle()
    }

    private suspend fun TestScope.loader(): HubOverviewLoader {
        val monitor = SeerrConnectionHealthMonitor()
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("p.preferences_pb") },
                        PlainCipher,
                    ),
                apis =
                    SeerrApiFactory(
                        logRequests = false,
                        health = monitor,
                        testTransport = seerr::interceptor,
                        testDispatcher = seerr::newDispatcher,
                    ),
                healthMonitor = monitor,
            )
        connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
        statusReads.set(0)
        return HubOverviewLoader(connection)
    }

    @Test
    fun `an unreachable server is probed once when the server card reads first`() =
        runTest {
            val loader = loader()
            serverDown.set(true)

            val card = async { runCatching { loader.server() } }
            val overview = async { loader.load() }

            assertTrue(card.await().isFailure)
            assertEquals(HubUserLoad.Failed, overview.await().userLoad)
            assertEquals(1, statusReads.get())
        }

    @Test
    fun `an unreachable server is probed once when the overview reads first`() =
        runTest {
            val loader = loader()
            serverDown.set(true)

            val overview = async { loader.load() }
            val card = async { runCatching { loader.server() } }

            assertEquals(HubUserLoad.Failed, overview.await().userLoad)
            assertTrue("the card still reports the failure, not a guess", card.await().isFailure)
            assertEquals(1, statusReads.get())
        }

    @Test
    fun `a retry that starts after the failure probes again`() =
        runTest {
            val loader = loader()
            serverDown.set(true)
            assertTrue(runCatching { loader.server() }.isFailure)
            assertEquals(1, statusReads.get())

            assertTrue(runCatching { loader.server() }.isFailure)

            assertEquals(2, statusReads.get())
        }

    @Test
    fun `a healthy server is read by the card and served from cache to the overview`() =
        runTest {
            val loader = loader()

            val card = loader.server()
            val overview = loader.load()

            assertEquals("Family", card.title)
            assertEquals(HubUserLoad.Loaded, overview.userLoad)
            assertEquals(1, statusReads.get())
        }

    @Test
    fun `a server that recovers is read again, not remembered as down`() =
        runTest {
            val loader = loader()
            serverDown.set(true)
            assertTrue(runCatching { loader.server() }.isFailure)
            serverDown.set(false)

            assertEquals("Family", loader.server().title)
            assertEquals(HubUserLoad.Loaded, loader.load().userLoad)
        }
}
