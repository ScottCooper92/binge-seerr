package io.github.scottcooper92.binge.seerr.ui.requests

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.paging.testing.asSnapshot
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
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
import java.util.concurrent.CopyOnWriteArrayList

private const val ADMIN = 2
private const val REQUEST = 32

/** The browser over a real connection into a path-scripted Seerr; Main is real-time, as for the hub. */
class RequestsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer()
    private val received = CopyOnWriteArrayList<RecordedRequest>()
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
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

    private fun server(permissions: Int) {
        seerr.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    return when (request.url.encodedPath) {
                        "/api/v1/auth/me" -> json("""{"id":7,"displayName":"Scott","permissions":$permissions}""")
                        "/api/v1/status" -> json("""{"version":"3.1.0"}""")
                        "/api/v1/settings/public" -> json("""{"mediaServerType":2}""")
                        "/api/v1/request/count" -> json("""{"total":3,"pending":1,"approved":2,"processing":1,"available":1}""")
                        "/api/v1/request" ->
                            json(
                                """{"pageInfo":{"pages":1,"results":1},"results":[{"id":11,"status":2,"media":{"tmdbId":100,"mediaType":"movie","status":3}}]}""",
                            )
                        "/api/v1/movie/100" -> json("""{"title":"Heat","posterPath":"/heat.jpg","releaseDate":"1995-12-15"}""")
                        else -> MockResponse(code = 404)
                    }
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
                apis = SeerrApiFactory(logRequests = false),
            )
        connection.connect(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
        val vm = RequestsViewModel(connection, TitleCache())
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
            val list = received.last { it.url.encodedPath == "/api/v1/request" }.url
            assertNull(list.queryParameter("requestedBy"))
            assertEquals("added", list.queryParameter("sort"))

            vm.setSort(RequestSort.Modified)
            vm.awaitReady { it.sort == RequestSort.Modified }
            vm.requests(RequestFilter.All).asSnapshot()
            assertEquals("modified", received.last { it.url.encodedPath == "/api/v1/request" }.url.queryParameter("sort"))
        }

    @Test
    fun `a plain requester's list is scoped to their own requests`() =
        runTest {
            server(REQUEST)
            val vm = viewModel()
            vm.awaitReady { it.counts != null }

            vm.requests(RequestFilter.Pending).asSnapshot()

            val list = received.last { it.url.encodedPath == "/api/v1/request" }.url
            assertEquals("7", list.queryParameter("requestedBy"))
            assertEquals("pending", list.queryParameter("filter"))
            assertTrue(
                !vm
                    .awaitReady { true }
                    .scope.permissions.canManageRequests,
            )
        }

    private fun json(body: String) = MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
