package io.github.scottcooper92.binge.seerr.ui.hub

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.BingeConnectionStore
import io.github.scottcooper92.binge.seerr.auth.BingeHint
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.DataStoreBingeConnectionStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealthMonitor
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.util.FakeResponse
import io.github.scottcooper92.binge.seerr.util.FakeSeerrServer
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val ADMIN = 2
private const val REQUEST = 32
private const val LATCH_SECONDS = 5L

/**
 * The hub over an in-memory connection, scripted by path, because the overview fans its calls out
 * concurrently and a queue would answer them in the wrong order. [DownloadsPoller] is bounded to its
 * first refresh: its own poll loop is covered by [DownloadsPollerTest], and a real `delay` loop would
 * spin forever under this test's virtual clock (#337).
 */
class HubViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()
    private val responses = mutableMapOf<String, () -> FakeResponse>()

    /** Every ViewModel goes in here and is cleared on teardown, so no poll or probe outlives its test. */
    private val viewModels = ViewModelStore()

    /** One refresh, then never again: the strip only needs its first read, and this keeps the poll loop bounded. */
    private val boundedTicker =
        object : DownloadsPollerTicker() {
            override suspend fun await(intervalMs: Long) = awaitCancellation()
        }

    /** The network gone, as with Tailscale off: every call fails with an [IOException] before any answer. */
    private val serverDown = AtomicBoolean(false)

    @Before
    fun setUp() {
        seerr.dispatcher = { request ->
            if (serverDown.get()) throw IOException("unreachable")
            responses[request.url.encodedPath]?.invoke() ?: FakeResponse(code = 404)
        }
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.awaitIdle()
    }

    private fun serve(
        path: String,
        body: String,
        code: Int = 200,
    ) {
        responses[path] = { FakeResponse(code = code, headers = headersOf("Content-Type", "application/json"), body = body) }
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

    /** A mutable install signal a test can flip mid-run, standing in for a package the user installs while backgrounded. */
    private class FakeBingeInstallCheck(
        var installed: Boolean = true,
    ) : BingeInstallCheck {
        override fun isInstalled(): Boolean = installed
    }

    /** The cache is a singleton in the app; a test shares one across ViewModels to stand in for leaving and returning to the hub. */
    private val cache = HubOverviewCache()

    private suspend fun TestScope.viewModel(
        installCheck: BingeInstallCheck = FakeBingeInstallCheck(),
        bingeConnection: BingeConnectionStore =
            DataStoreBingeConnectionStore(
                PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("binge_connection.preferences_pb") },
            ),
    ): HubViewModel {
        val monitor = SeerrConnectionHealthMonitor()
        connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("h.preferences_pb") },
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
                onServerChanged = { cache.clear() },
            )
        connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
        val vm =
            HubViewModel(
                connection,
                HubOverviewLoader(connection),
                cache,
                mainDispatcherRule.dispatcher,
                boundedTicker,
                installCheck,
                bingeConnection,
            )
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

            val ready = vm.awaitReady { it.downloading.isNotEmpty() && it.overview.blocklistCount != null }

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

            val ready = vm.awaitReady()

            assertEquals(listOf(HubSection.Requests), ready.overview.visibleSections())
            assertNull(ready.overview.userCount)
            assertNull(ready.overview.blocklistCount)
            assertNull(ready.overview.openIssueCount)
            assertEquals(HubAccount(id = 1, name = "Scott", isAdmin = false, avatarUrl = null), ready.overview.account)
        }

    /** The connection is made while the server is healthy; the hub then opens on a server that has turned. */
    private suspend fun TestScope.viewModelAfterAuthMeAnswers(code: Int): HubViewModel =
        viewModelAfterConnect { serve("/api/v1/auth/me", "", code = code) }

    private suspend fun TestScope.viewModelAfterConnect(turn: () -> Unit): HubViewModel {
        healthyServer()
        val monitor = SeerrConnectionHealthMonitor()
        connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("h.preferences_pb") },
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
        turn()
        val vm = HubViewModel(connection, HubOverviewLoader(connection), cache, mainDispatcherRule.dispatcher, boundedTicker)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `a rejected session reads as Unauthorized`() =
        runTest {
            val vm = viewModelAfterAuthMeAnswers(401)

            val ready = vm.awaitReady()

            assertEquals(ConnectionHealth.Unauthorized, ready.health)
            assertNull(ready.overview.account)
        }

    @Test
    fun `an auth-me that fails transiently on a server that answers reads as CouldNotLoad, not as a restricted user`() =
        runTest {
            val vm = viewModelAfterAuthMeAnswers(503)

            val ready = vm.awaitReady()

            assertEquals(ConnectionHealth.CouldNotLoad, ready.health)
            assertEquals(listOf(HubSection.Requests), ready.overview.visibleSections())
        }

    @Test
    fun `a cold start against a server that does not answer reaches the unreachable problem, not an endless Loading`() =
        runTest {
            val vm = viewModelAfterConnect { serverDown.set(true) }

            val error = vm.uiState.first { it is HubUiState.Error } as HubUiState.Error

            assertEquals(ConnectionHealth.Unreachable, error.health)
        }

    @Test
    fun `retrying from the unreachable problem re-reads the server and reaches Ready once it answers`() =
        runTest {
            val vm = viewModelAfterConnect { serverDown.set(true) }
            vm.uiState.first { it is HubUiState.Error }

            serverDown.set(false)
            vm.recheck()
            val ready = vm.awaitReady { it.overview.account != null }

            assertEquals("Family", ready.server.title)
            assertEquals(ConnectionHealth.Healthy, ready.health)
        }

    @Test
    fun `a warm start against a server that does not answer keeps the remembered hub and reads as a problem on it`() =
        runTest {
            healthyServer()
            viewModel().awaitReady { it.overview.account != null }
            serverDown.set(true)

            val vm = returnToHub()
            val states = mutableListOf<HubUiState>()
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect { states += it } }
            val ready = vm.awaitReady { it.overview.userLoad == HubUserLoad.Failed }

            assertEquals("Family", ready.server.title)
            assertEquals(ConnectionHealth.Unreachable, ready.health)
            assertTrue(states.none { it is HubUiState.Error })
        }

    @Test
    fun `disconnecting forgets the server`() =
        runTest {
            healthyServer()
            val vm = viewModel()
            vm.awaitReady()

            vm.disconnect()

            assertNull(connection.credentials.first { it == null })
        }

    /**
     * The placeholder overview is fail-closed — `SeerrPermissions()` grants nothing — so a Ready
     * built on it is not a security problem but a settled-looking hub with Requests alone, the one
     * section with no visibility gate. Ready waits for the real one.
     */
    @Test
    fun `a placeholder overview never reaches Ready`() =
        runTest {
            healthyServer()
            val vm = viewModel()
            val states = mutableListOf<HubUiState>()
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect { states += it } }

            val ready = vm.awaitReady()

            assertTrue(ready.overview.loaded)
            assertTrue(states.filterIsInstance<HubUiState.Ready>().all { it.overview.loaded })
        }

    /** The same instance survives a disconnect + reconnect: the nav host reuses it across the swap. */
    @Test
    fun `reconnecting after disconnect drops the previous connection's server and overview, not just its health`() =
        runTest {
            healthyServer()
            val vm = viewModel()
            vm.awaitReady { it.server.title == "Family" }

            vm.disconnect()
            connection.credentials.first { it == null }
            serve("/api/v1/settings/public", """{"applicationTitle":"Second Home","mediaServerType":2}""")
            serve("/api/v1/request/count", """{"total":0,"movie":0,"tv":0,"pending":0,"processing":0}""")
            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y-2")).getOrThrow()

            val ready = vm.awaitReady { it.server.title == "Second Home" }

            assertEquals(0, ready.overview.movieRequestCount)
        }

    /** A second visit: a fresh ViewModel over the same connection and the same singleton cache. */
    private fun TestScope.returnToHub(): HubViewModel {
        val vm = HubViewModel(connection, HubOverviewLoader(connection), cache, mainDispatcherRule.dispatcher, boundedTicker)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `returning to the hub shows the previous overview while the refresh is still running`() =
        runTest {
            healthyServer()
            viewModel().awaitReady { it.overview.account != null }
            val release = CountDownLatch(1)
            responses["/api/v1/auth/me"] = {
                release.await(LATCH_SECONDS, TimeUnit.SECONDS)
                FakeResponse(
                    headers = headersOf("Content-Type", "application/json"),
                    body = """{"id":1,"displayName":"Scott","permissions":$ADMIN}""",
                )
            }

            try {
                val ready = returnToHub().awaitReady()

                assertEquals("Scott", ready.overview.account?.name)
                assertEquals(HubSection.entries, ready.overview.visibleSections())
                assertEquals("Family", ready.server.title)
            } finally {
                release.countDown()
            }
        }

    @Test
    fun `a different server never shows the previous server's overview`() =
        runTest {
            healthyServer()
            viewModel().awaitReady { it.overview.account != null }
            // The user has left the hub: its ViewModel is gone, so only the cache can carry its overview forward.
            viewModels.clear()
            connection.disconnect()
            connection.connect("http://other-seerr.test:8080/", SeerrAuth.ApiKey("k3y-2")).getOrThrow()
            val release = CountDownLatch(1)
            responses["/api/v1/auth/me"] = {
                release.await(LATCH_SECONDS, TimeUnit.SECONDS)
                FakeResponse(
                    headers = headersOf("Content-Type", "application/json"),
                    body = """{"id":9,"displayName":"Other","permissions":$REQUEST}""",
                )
            }

            try {
                val vm = returnToHub()
                val states = mutableListOf<HubUiState>()
                backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect { states += it } }
                runCurrent()
                assertEquals(HubUiState.Loading, vm.uiState.value)
            } finally {
                release.countDown()
            }
            val ready = returnToHub().awaitReady { it.overview.account?.name == "Other" }
            assertEquals(listOf(HubSection.Requests), ready.overview.visibleSections())
        }

    @Test
    fun `a different account on the same server never shows the previous account's overview`() =
        runTest {
            healthyServer()
            viewModel().awaitReady { it.overview.account != null }
            connection.disconnect()
            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("other-k3y")).getOrThrow()
            serve("/api/v1/auth/me", "", code = 503)

            val vm = returnToHub()
            runCurrent()

            assertEquals(HubUiState.Loading, vm.uiState.value)
        }

    @Test
    fun `a live hub never shows the previous account's overview while new credentials are saved over it`() =
        runTest {
            healthyServer()
            val vm = viewModel()
            vm.awaitReady { it.overview.account != null }
            val states = mutableListOf<HubUiState>()
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.uiState.collect { states += it } }
            states.clear()
            // The connect's own probe reads auth/me once; every read after it, the live hub's reload included, fails.
            val probes = AtomicInteger()
            val healthy = responses.getValue("/api/v1/auth/me")
            responses["/api/v1/auth/me"] = {
                if (probes.getAndIncrement() ==
                    0
                ) {
                    healthy()
                } else {
                    FakeResponse(code = 503, headers = headersOf("Content-Type", "application/json"), body = "")
                }
            }

            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("other-k3y")).getOrThrow()
            runCurrent()

            assertTrue(states.none { it is HubUiState.Ready && it.overview.account != null })
        }

    @Test
    fun `a failed refresh keeps showing the remembered overview and still reads as could-not-load`() =
        runTest {
            healthyServer()
            viewModel().awaitReady { it.overview.account != null }
            serve("/api/v1/auth/me", "", code = 503)

            val ready = returnToHub().awaitReady { it.health == ConnectionHealth.CouldNotLoad }

            assertEquals("Scott", ready.overview.account?.name)
            assertEquals(HubSection.entries, ready.overview.visibleSections())
        }

    @Test
    fun `Binge not installed reads NotInstalled, even with a handshake already recorded`() =
        runTest {
            healthyServer()
            val store =
                DataStoreBingeConnectionStore(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("bc.preferences_pb") },
                )
            store.recordHandshake()
            val vm = viewModel(installCheck = FakeBingeInstallCheck(installed = false), bingeConnection = store)

            assertEquals(BingeStatus.NotInstalled, vm.awaitReady().bingeStatus)
        }

    @Test
    fun `Binge installed with no handshake yet reads NotConnected`() =
        runTest {
            healthyServer()
            val vm = viewModel(installCheck = FakeBingeInstallCheck(installed = true))

            assertEquals(BingeStatus.NotConnected, vm.awaitReady().bingeStatus)
        }

    @Test
    fun `a recorded handshake reads Connected once Binge is installed`() =
        runTest {
            healthyServer()
            val store =
                DataStoreBingeConnectionStore(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("bc.preferences_pb") },
                )
            store.recordHandshake()
            val vm = viewModel(installCheck = FakeBingeInstallCheck(installed = true), bingeConnection = store)

            assertEquals(BingeStatus.Connected, vm.awaitReady().bingeStatus)
        }

    @Test
    fun `dismissing the hint hides it for the status it was shown under, and only that one`() =
        runTest {
            healthyServer()
            val store =
                DataStoreBingeConnectionStore(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("bc.preferences_pb") },
                )
            val vm = viewModel(installCheck = FakeBingeInstallCheck(installed = true), bingeConnection = store)
            assertEquals(false, vm.awaitReady().bingeHintDismissed)

            vm.dismissBingeHint()
            val dismissed = vm.awaitReady { it.bingeHintDismissed }

            assertEquals(BingeStatus.NotConnected, dismissed.bingeStatus)
            assertEquals(setOf(BingeHint.NotConnected), store.dismissedHints.first())

            store.recordHandshake()
            assertEquals(false, vm.awaitReady { it.bingeStatus == BingeStatus.Connected }.bingeHintDismissed)
        }

    @Test
    fun `the install hint can be dismissed too, and that stays with the not-installed state`() =
        runTest {
            healthyServer()
            val installCheck = FakeBingeInstallCheck(installed = false)
            val vm = viewModel(installCheck = installCheck)
            assertEquals(false, vm.awaitReady().bingeHintDismissed)

            vm.dismissBingeHint()
            assertEquals(BingeStatus.NotInstalled, vm.awaitReady { it.bingeHintDismissed }.bingeStatus)

            installCheck.installed = true
            vm.setScreenVisible(true)
            assertEquals(false, vm.awaitReady { it.bingeStatus == BingeStatus.NotConnected }.bingeHintDismissed)
        }

    @Test
    fun `becoming visible re-checks whether Binge was installed while the screen was backgrounded`() =
        runTest {
            healthyServer()
            val installCheck = FakeBingeInstallCheck(installed = false)
            val vm = viewModel(installCheck = installCheck)
            assertEquals(BingeStatus.NotInstalled, vm.awaitReady().bingeStatus)

            installCheck.installed = true
            vm.setScreenVisible(true)

            assertEquals(BingeStatus.NotConnected, vm.awaitReady { it.bingeStatus == BingeStatus.NotConnected }.bingeStatus)
        }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
