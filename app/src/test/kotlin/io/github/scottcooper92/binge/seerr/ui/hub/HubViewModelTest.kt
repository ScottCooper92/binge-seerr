package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealthMonitor
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val ADMIN = 2
private const val REQUEST = 32

/**
 * The hub over a real connection: DataStore in a temp file, HTTP into a Seerr scripted by path,
 * because the overview fans its calls out concurrently and a queue would answer them in the wrong
 * order. Main is a real-time dispatcher here, not the test scheduler: the ViewModel owns polling
 * loops, and virtual time would spin them while the body waits on the network.
 */
class HubViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer()
    private val responses = mutableMapOf<String, () -> MockResponse>()

    /** Every ViewModel goes in here and is cleared on teardown, so no poll or probe outlives its test. */
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    responses[request.url.encodedPath]?.invoke() ?: MockResponse(code = 404)
            }
        seerr.start()
    }

    /**
     * Main is set on every setup and never reset: a callback still in flight at teardown would
     * otherwise dispatch into the unset window and be reported into whichever test runs next.
     */
    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private fun serve(
        path: String,
        body: String,
        code: Int = 200,
    ) {
        responses[path] = { MockResponse(code = code, headers = headersOf("Content-Type", "application/json"), body = body) }
    }

    /** An admin on a Seerr 3.4 with one request downloading and a quota. */
    private fun healthyServer(permissions: Int = ADMIN) {
        serve("/api/v1/status", """{"version":"3.4.0","updateAvailable":true}""")
        serve("/api/v1/settings/public", """{"applicationTitle":"Family","mediaServerType":2}""")
        serve("/api/v1/auth/me", """{"id":1,"displayName":"Scott","permissions":$permissions}""")
        serve("/api/v1/user/1/quota", """{"movie":{"days":7,"limit":10,"used":3,"remaining":7},"tv":{"days":7,"limit":0}}""")
        serve("/api/v1/request/count", """{"total":12,"movie":8,"tv":4,"pending":2,"processing":1}""")
        serve("/api/v1/issue/count", """{"total":3,"open":1,"closed":2}""")
        serve("/api/v1/user", """{"pageInfo":{"results":5},"results":[]}""")
        serve("/api/v1/blocklist", """{"pageInfo":{"results":9},"results":[]}""")
        serve(
            "/api/v1/request",
            """{"pageInfo":{"results":1},"results":[{"id":7,"status":2,"media":{"tmdbId":550,"mediaType":"movie","status":3,
               "downloadStatus":[{"title":"Fight.Club","size":2000,"sizeLeft":500,"status":"downloading","timeLeft":"00:12:00"}]}}]}""",
        )
        serve("/api/v1/movie/550", """{"title":"Fight Club","posterPath":"/fc.jpg","releaseDate":"1999-10-15"}""")
    }

    private lateinit var connection: SeerrConnection

    private suspend fun TestScope.viewModel(): HubViewModel {
        val monitor = SeerrConnectionHealthMonitor()
        connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("h.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false, health = monitor),
                healthMonitor = monitor,
            )
        connection.connect(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
        val vm = HubViewModel(connection, HubOverviewLoader(connection))
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun HubViewModel.awaitReady(match: (HubUiState.Ready) -> Boolean = { true }): HubUiState.Ready =
        uiState.first { it is HubUiState.Ready && match(it) } as HubUiState.Ready

    @Test
    fun `an admin's hub carries the server, the account, the quota, every count and the strip`() =
        runTest {
            healthyServer()
            val vm = viewModel()
            vm.setScreenVisible(true)

            val ready = vm.awaitReady { it.overview.loaded && it.downloading.isNotEmpty() && it.overview.blocklistCount != null }

            assertEquals("Family", ready.server.title)
            assertEquals(SeerrVariant.Seerr, ready.server.variant)
            assertEquals("3.4.0", ready.server.versionLabel)
            assertTrue(ready.server.updateAvailable)
            assertEquals(ConnectionHealth.Healthy, ready.health)
            assertEquals(HubAccount(id = 1, name = "Scott", isAdmin = true, avatarUrl = null), ready.overview.account)
            assertEquals(HubQuota(movie = HubQuotaBucket(limit = 10, remaining = 7, days = 7), tv = null), ready.overview.quota)
            assertEquals(8, ready.overview.movieRequestCount)
            assertEquals(2, ready.overview.pendingRequestCount)
            assertEquals(1, ready.overview.openIssueCount)
            assertEquals(5, ready.overview.userCount)
            assertEquals(9, ready.overview.blocklistCount)
            assertEquals(HubSection.entries, ready.overview.visibleSections())
            val download = ready.downloading.single()
            assertEquals("Fight Club", download.title)
            assertEquals("https://image.tmdb.org/t/p/w342/fc.jpg", download.posterUrl)
            assertEquals(0.75f, download.fraction)
            assertEquals(12, download.etaMinutes)
        }

    @Test
    fun `a plain requester sees only the requests row, and no count the server would refuse`() =
        runTest {
            healthyServer(permissions = REQUEST)
            val vm = viewModel()

            val ready = vm.awaitReady { it.overview.loaded }

            assertEquals(listOf(HubSection.Requests), ready.overview.visibleSections())
            assertNull(ready.overview.userCount)
            assertNull(ready.overview.blocklistCount)
            assertNull(ready.overview.openIssueCount)
            assertEquals(HubAccount(id = 1, name = "Scott", isAdmin = false, avatarUrl = null), ready.overview.account)
        }

    /** The connection is made while the server is healthy; the hub then opens on a server that has turned. */
    private suspend fun TestScope.viewModelAfterAuthMeAnswers(code: Int): HubViewModel {
        healthyServer()
        val monitor = SeerrConnectionHealthMonitor()
        connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("h.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false, health = monitor),
                healthMonitor = monitor,
            )
        connection.connect(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
        serve("/api/v1/auth/me", "", code = code)
        val vm = HubViewModel(connection, HubOverviewLoader(connection))
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `a rejected session reads as Unauthorized`() =
        runTest {
            val vm = viewModelAfterAuthMeAnswers(401)

            val ready = vm.awaitReady { it.overview.loaded }

            assertEquals(ConnectionHealth.Unauthorized, ready.health)
            assertNull(ready.overview.account)
        }

    @Test
    fun `an auth-me that fails transiently on a server that answers reads as CouldNotLoad, not as a restricted user`() =
        runTest {
            val vm = viewModelAfterAuthMeAnswers(503)

            val ready = vm.awaitReady { it.overview.loaded }

            assertEquals(ConnectionHealth.CouldNotLoad, ready.health)
            assertEquals(listOf(HubSection.Requests), ready.overview.visibleSections())
        }

    @Test
    fun `disconnecting forgets the server`() =
        runTest {
            healthyServer()
            val vm = viewModel()
            vm.awaitReady { it.overview.loaded }

            vm.disconnect()

            assertNull(connection.credentials.first { it == null })
        }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
