package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.paging.testing.asSnapshot
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.FakeRequestStore
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.util.FakeRequest
import io.github.scottcooper92.binge.seerr.util.FakeResponse
import io.github.scottcooper92.binge.seerr.util.FakeSeerrServer
import io.github.scottcooper92.binge.seerr.util.FakeTitleDao
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val REQUEST_WAIT_MILLIS = 2_000L
private const val POLL_MILLIS = 10L

private const val ADMIN = 2
private const val HTTP_OK = 200
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_SERVER_ERROR = 500
private const val REQUEST = 32

/** The browser over an in-memory connection into a path-scripted Seerr. */
class RequestsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()
    private val received = CopyOnWriteArrayList<FakeRequest>()
    private val viewModels = ViewModelStore()

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.awaitIdle()
    }

    /** The viewer's permissions as the server currently has them; a test can change them mid-run. */
    private val viewerPermissions = AtomicInteger(0)

    /** The server's version, which a test can change mid-run, as an upgrade in place would. */
    private val serverVersion = AtomicReference("3.1.0")

    private fun server(permissions: Int) {
        viewerPermissions.set(permissions)
        seerr.dispatcher = { request ->
            received += request
            when (request.url.encodedPath) {
                "/api/v1/auth/me" -> json("""{"id":7,"displayName":"Scott","permissions":${viewerPermissions.get()}}""")
                "/api/v1/status" -> json("""{"version":"${serverVersion.get()}"}""")
                "/api/v1/settings/public" -> json("""{"mediaServerType":2}""")
                "/api/v1/request/count" -> json("""{"total":3,"pending":1,"approved":2,"processing":1,"available":1}""")
                "/api/v1/request" ->
                    json(
                        """{"pageInfo":{"pages":1,"results":1},"results":[{"id":11,"status":2,"media":{"tmdbId":100,"mediaType":"movie","status":3}}]}""",
                    )
                "/api/v1/movie/100" -> json("""{"title":"Heat","posterPath":"/heat.jpg","releaseDate":"1995-12-15"}""")
                "/api/v1/request/11/approve" -> json("{}")
                else -> FakeResponse(code = 404)
            }
        }
    }

    private suspend fun TestScope.viewModel(): RequestsViewModel {
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("r.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher),
            )
        connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
        val vm = RequestsViewModel(connection, TitleCache(FakeTitleDao()), FakeRequestStore(), mainDispatcherRule.dispatcher)
        viewModels.put("requests", vm)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setScreenVisible(true)
        return vm
    }

    private suspend fun RequestsViewModel.awaitReady(match: (RequestsUiState.Ready) -> Boolean): RequestsUiState.Ready =
        uiState.first { it is RequestsUiState.Ready && match(it) } as RequestsUiState.Ready

    @Test
    fun `a moderator sees everyone's requests with the chip counts, and the sort re-queries`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()

            val ready = vm.awaitReady { it.counts != null && it.scope.permissions.canManageRequests }
            assertEquals(RequestCounts(total = 3, pending = 1, approved = 2, processing = 1, available = 1), ready.counts)

            val rows = vm.requests(RequestFilter.All).asSnapshot()
            assertEquals(listOf("Heat"), rows.map { it.title })
            // The refresh's count reaches the state, so the list can tell an empty answer from rows on their way.
            assertEquals(
                1,
                vm
                    .awaitReady { RequestFilter.All in it.refreshes }
                    .refreshes
                    .getValue(RequestFilter.All)
                    .rowsWritten,
            )
            val list = received.last { it.url.encodedPath == "/api/v1/request" }.url
            assertNull(list.queryParameter("requestedBy"))
            assertEquals("added", list.queryParameter("sort"))

            vm.setSort(RequestSort.Modified)
            vm.awaitReady { it.sort == RequestSort.Modified }
            vm.requests(RequestFilter.All).asSnapshot()
            assertEquals("modified", received.last { it.url.encodedPath == "/api/v1/request" }.url.queryParameter("sort"))
        }

    @Test
    fun `a moderation carries its list version in the state, and each filter refreshes once on it`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()
            val before = vm.awaitReady { it.counts != null }.listVersion

            vm.listChanged()

            val after = vm.awaitReady { it.listVersion > before }.listVersion
            // The gate is per filter per version: the open list refreshes once, a filter that was
            // never on screen refreshes when it first is, and neither refreshes twice.
            assertTrue(vm.shouldRefresh(RequestFilter.All, after))
            assertFalse(vm.shouldRefresh(RequestFilter.All, after))
            assertTrue(vm.shouldRefresh(RequestFilter.Pending, after))
        }

    @Test
    fun `a plain requester's list is scoped to their own requests, and its chips carry no server-wide counts`() =
        runTest {
            server(REQUEST)
            val vm = viewModel()
            // request/count counts the whole server, so a list of their own requests would not match it (#977).
            assertEquals(null, vm.awaitReady { true }.counts)

            vm.requests(RequestFilter.Pending).asSnapshot()
            assertTrue(received.none { it.url.encodedPath == "/api/v1/request/count" })

            val list = received.last { it.url.encodedPath == "/api/v1/request" }.url
            assertEquals("7", list.queryParameter("requestedBy"))
            assertEquals("pending", list.queryParameter("filter"))
            assertTrue(
                !vm
                    .awaitReady { true }
                    .scope.permissions.canManageRequests,
            )
        }

    /** A Jellyseerr 1.x upgraded in place to 2.x offers block-on-decline on the next arrival, with no reconnect (#1074). */
    @Test
    fun `becoming visible re-reads the profile, so a server upgraded in place offers its blocklist`() =
        runTest {
            serverVersion.set("1.9.0")
            server(ADMIN)
            val vm = viewModel()
            assertFalse(vm.awaitReady { it.scope.permissions.canManageRequests }.scope.hasBlocklist)

            serverVersion.set("2.7.0")
            vm.setScreenVisible(true)

            assertTrue(vm.awaitReady { it.scope.hasBlocklist }.scope.hasBlocklist)
        }

    @Test
    fun `becoming visible re-reads the scope, so a permission granted on the server lands`() =
        runTest {
            server(REQUEST)
            val vm = viewModel()
            assertFalse(
                vm
                    .awaitReady { true }
                    .scope.permissions.canManageRequests,
            )

            // Granted in the web client while this screen was elsewhere; the cached auth/me would miss it.
            viewerPermissions.set(ADMIN)
            vm.setScreenVisible(true)

            // Now seeing every request, the chips count them.
            assertTrue(vm.awaitReady { it.scope.permissions.canManageRequests && it.counts != null }.counts != null)
        }

    @Test
    fun `a transient auth failure resolves once the screen becomes visible again`() =
        runTest {
            val authShouldFail = AtomicBoolean(false)
            val authFailed = CompletableDeferred<Unit>()
            seerr.dispatcher = { request ->
                received += request
                when (request.url.encodedPath) {
                    "/api/v1/auth/me" ->
                        if (authShouldFail.get()) {
                            FakeResponse(code = 500).also { authFailed.complete(Unit) }
                        } else {
                            json("""{"id":7,"displayName":"Scott","permissions":$REQUEST}""")
                        }
                    "/api/v1/status" -> json("""{"version":"3.1.0"}""")
                    "/api/v1/settings/public" -> json("""{"mediaServerType":2}""")
                    "/api/v1/request/count" -> json("""{"total":3,"pending":1,"approved":2,"processing":1,"available":1}""")
                    else -> FakeResponse(code = 404)
                }
            }

            val connection =
                SeerrConnection(
                    store =
                        CredentialStore(
                            PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("r.preferences_pb") },
                            PlainCipher,
                        ),
                    apis = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher),
                )
            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()

            // Fail only the ViewModel's own resolve, not the connect() probe above.
            authShouldFail.set(true)
            val probes = authReads()
            val vm = RequestsViewModel(connection, TitleCache(FakeTitleDao()), FakeRequestStore(), mainDispatcherRule.dispatcher)
            viewModels.put("requests", vm)
            // Every emission, not the current value: Loading is also stateIn's seed, so sampling
            // uiState cannot tell "held at Loading" apart from "has not propagated yet".
            val seen = CopyOnWriteArrayList<RequestsUiState>()
            backgroundScope.launch { vm.uiState.collect { seen += it } }

            authFailed.await()
            awaitAuthReads(moreThan = probes)
            assertTrue(seen.none { it is RequestsUiState.Ready })

            authShouldFail.set(false)
            vm.setScreenVisible(true)

            val ready = vm.awaitReady { true }
            assertFalse(ready.scope.permissions.canManageRequests)
            // The timing-free half: a guessed scope is the all-permissive one, and this user has
            // only REQUEST, so a Ready carrying moderation could only have come from a guess.
            assertTrue(seen.none { it is RequestsUiState.Ready && it.scope.permissions.canManageRequests })
        }

    @Test
    fun `an auth failure on entry is an error the screen can show, and retry recovers from it`() =
        runTest {
            val authStatus = AtomicInteger(HTTP_SERVER_ERROR)
            seerr.dispatcher = { request ->
                received += request
                when (request.url.encodedPath) {
                    "/api/v1/auth/me" ->
                        if (authStatus.get() == HTTP_OK) {
                            json("""{"id":7,"displayName":"Scott","permissions":$REQUEST}""")
                        } else {
                            FakeResponse(code = authStatus.get())
                        }
                    "/api/v1/status" -> json("""{"version":"3.1.0"}""")
                    "/api/v1/settings/public" -> json("""{"mediaServerType":2}""")
                    "/api/v1/request/count" -> json("""{"total":3,"pending":1,"approved":2,"processing":1,"available":1}""")
                    else -> FakeResponse(code = 404)
                }
            }
            val connection = connectedWhile(authStatus)
            val vm = RequestsViewModel(connection, TitleCache(FakeTitleDao()), FakeRequestStore(), mainDispatcherRule.dispatcher)
            viewModels.put("requests", vm)
            backgroundScope.launch { vm.uiState.collect {} }
            vm.setScreenVisible(true)

            assertEquals(RequestsUiState.Error(SeerrError.Server), vm.uiState.first { it is RequestsUiState.Error })

            authStatus.set(HTTP_UNAUTHORIZED)
            vm.retry()
            assertEquals(
                RequestsUiState.Error(SeerrError.Unauthorized),
                vm.uiState.first {
                    it ==
                        RequestsUiState.Error(SeerrError.Unauthorized)
                },
            )

            authStatus.set(HTTP_OK)
            vm.retry()
            assertFalse(
                vm
                    .awaitReady { true }
                    .scope.permissions.canManageRequests,
            )
        }

    /** A connection whose `connect()` probe passes, then reads `auth/me` as [authStatus] says. */
    private suspend fun TestScope.connectedWhile(authStatus: AtomicInteger): SeerrConnection {
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("r.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher),
            )
        val failing = authStatus.getAndSet(HTTP_OK)
        connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
        authStatus.set(failing)
        return connection
    }

    private fun authReads() = received.count { it.url.encodedPath == "/api/v1/auth/me" }

    /** The resolve lands on OkHttp's threads after the dispatcher answered it; this waits in real time. */
    private suspend fun awaitAuthReads(moreThan: Int) =
        withContext(Dispatchers.Default) {
            withTimeout(REQUEST_WAIT_MILLIS) { while (authReads() <= moreThan) delay(POLL_MILLIS) }
        }

    private fun json(body: String) = FakeResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
