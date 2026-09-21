package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.paging.testing.asSnapshot
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.FakeIssueStore
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.util.FakeRequest
import io.github.scottcooper92.binge.seerr.util.FakeResponse
import io.github.scottcooper92.binge.seerr.util.FakeSeerrServer
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.awaitEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

private const val ADMIN = 2
private const val CREATE_ISSUES = 1 shl 22

/** The browser over an in-memory connection into a path-scripted Seerr, paging through the fake cache. */
class IssuesViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()
    private val received = CopyOnWriteArrayList<FakeRequest>()
    private val viewModels = ViewModelStore()

    /** The viewer's permissions as the server currently has them; a test can change them mid-run. */
    private val viewerPermissions = AtomicInteger(0)

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.awaitIdle()
    }

    private fun server(permissions: Int) {
        viewerPermissions.set(permissions)
        seerr.dispatcher = { request ->
            received += request
            when (request.url.encodedPath) {
                "/api/v1/auth/me" -> json("""{"id":7,"displayName":"Scott","permissions":${viewerPermissions.get()}}""")
                "/api/v1/status" -> json("""{"version":"3.1.0"}""")
                "/api/v1/settings/public" -> json("""{"mediaServerType":2}""")
                "/api/v1/issue/count" -> json("""{"total":3,"open":2,"closed":1}""")
                "/api/v1/issue" ->
                    json(
                        """{"pageInfo":{"pages":1,"results":1},"results":[{"id":31,"issueType":3,"status":1,
                           "createdBy":{"displayName":"ana"},"media":{"tmdbId":100,"mediaType":"movie"}}]}""",
                    )
                "/api/v1/movie/100" -> json("""{"title":"Heat","posterPath":"/heat.jpg","releaseDate":"1995-12-15"}""")
                "/api/v1/issue/31/resolved", "/api/v1/issue/31/open" -> json("""{"id":31,"status":2}""")
                "/api/v1/issue/31" -> if (request.method == "DELETE") FakeResponse(code = 204) else FakeResponse(code = 404)
                else -> FakeResponse(code = 404)
            }
        }
    }

    private suspend fun TestScope.viewModel(): IssuesViewModel {
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("i.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher),
            )
        connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
        val vm = IssuesViewModel(connection, TitleCache(), FakeIssueStore(), mainDispatcherRule.dispatcher)
        viewModels.put("issues", vm)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setScreenVisible(true)
        return vm
    }

    private suspend fun IssuesViewModel.awaitReady(match: (IssuesUiState.Ready) -> Boolean): IssuesUiState.Ready =
        uiState.first { it is IssuesUiState.Ready && match(it) } as IssuesUiState.Ready

    @Test
    fun `a manager sees everyone's issues with the chip counts, titled through the cache, and the sort re-queries`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()

            val ready = vm.awaitReady { it.counts != null && it.scope.permissions.canManageIssues }
            assertEquals(IssueCounts(total = 3, open = 2, resolved = 1), ready.counts)
            assertNull(ready.scope.createdBy)

            val rows = vm.issues(IssueFilter.Open).asSnapshot()

            val heat = rows.single()
            assertEquals("Heat", heat.title)
            assertEquals("https://image.tmdb.org/t/p/w342/heat.jpg", heat.posterUrl)
            assertEquals(IssueType.Subtitles, heat.type)
            assertEquals(IssueStatus.Open, heat.status)
            assertEquals("ana", heat.reportedBy)
            val listed = received.first { it.url.encodedPath == "/api/v1/issue" }.url
            assertEquals("open", listed.queryParameter("filter"))
            assertNull(listed.queryParameter("createdBy"))

            vm.setSort(IssueSort.Modified)
            vm.awaitReady { it.sort == IssueSort.Modified }
            vm.issues(IssueFilter.Open).asSnapshot()
            assertTrue(received.any { it.url.encodedPath == "/api/v1/issue" && it.url.queryParameter("sort") == "modified" })
        }

    @Test
    fun `becoming visible re-reads the scope, so a permission granted on the server lands`() =
        runTest {
            server(CREATE_ISSUES)
            val vm = viewModel()
            assertEquals(7, vm.awaitReady { it.scope.currentUserId != null }.scope.createdBy)

            // Granted in the web client while this screen was elsewhere; the cached auth/me would miss it.
            viewerPermissions.set(ADMIN)
            vm.setScreenVisible(true)

            assertNull(vm.awaitReady { it.scope.permissions.canManageIssues }.scope.createdBy)
        }

    @Test
    fun `a reporter without the view permission is narrowed to their own issues`() =
        runTest {
            server(CREATE_ISSUES)
            val vm = viewModel()

            val ready = vm.awaitReady { it.scope.currentUserId != null }
            assertEquals(7, ready.scope.createdBy)

            vm.issues(IssueFilter.All).asSnapshot()

            val listed = received.last { it.url.encodedPath == "/api/v1/issue" }.url
            assertEquals("7", listed.queryParameter("createdBy"))
            assertEquals("all", listed.queryParameter("filter"))
        }

    @Test
    fun `resolving from the row posts the status, moves the cached row, and reports once`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()
            vm.awaitReady { it.counts != null }
            val heat = vm.issues(IssueFilter.Open).asSnapshot().single()
            val event = awaitEvent(vm.events)

            vm.resolve(heat)

            assertEquals(IssueListEvent.Resolved, event.await())
            assertTrue(received.any { it.method == "POST" && it.url.encodedPath == "/api/v1/issue/31/resolved" })
            vm.awaitReady { it.actingIds.isEmpty() }
        }

    @Test
    fun `deleting from the row removes it from the server and the cache`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()
            vm.awaitReady { it.counts != null }
            val heat = vm.issues(IssueFilter.Open).asSnapshot().single()
            val event = awaitEvent(vm.events)

            vm.delete(heat)

            assertEquals(IssueListEvent.Deleted, event.await())
            assertTrue(received.any { it.method == "DELETE" && it.url.encodedPath == "/api/v1/issue/31" })
        }

    @Test
    fun `a refused action reports the failure and leaves the row free to try again`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()
            vm.awaitReady { it.counts != null }
            val heat = vm.issues(IssueFilter.Open).asSnapshot().single()
            val event = awaitEvent(vm.events)

            vm.reopen(heat.copy(id = 99))

            assertTrue(event.await() is IssueListEvent.Failed)
            vm.awaitReady { it.actingIds.isEmpty() }
        }

    private fun json(body: String) = FakeResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
