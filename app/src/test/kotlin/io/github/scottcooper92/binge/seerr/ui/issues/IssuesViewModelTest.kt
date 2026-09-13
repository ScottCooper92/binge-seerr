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
private const val CREATE_ISSUES = 1 shl 22

/** The browser over a real connection into a path-scripted Seerr, paging through the fake cache; Main is real-time. */
class IssuesViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer()
    private val received = mutableListOf<RecordedRequest>()
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
                        "/api/v1/issue/count" -> json("""{"total":3,"open":2,"closed":1}""")
                        "/api/v1/issue" ->
                            json(
                                """{"pageInfo":{"pages":1,"results":1},"results":[{"id":31,"issueType":3,"status":1,
                                   "createdBy":{"displayName":"ana"},"media":{"tmdbId":100,"mediaType":"movie"}}]}""",
                            )
                        "/api/v1/movie/100" -> json("""{"title":"Heat","posterPath":"/heat.jpg","releaseDate":"1995-12-15"}""")
                        else -> MockResponse(code = 404)
                    }
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
                apis = SeerrApiFactory(logRequests = false),
            )
        connection.connect(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
        val vm = IssuesViewModel(connection, TitleCache(), FakeIssueStore())
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
            assertNull(ready.scope.requestedBy)

            val rows = vm.issues(IssueFilter.Open).asSnapshot()

            val heat = rows.single()
            assertEquals("Heat", heat.title)
            assertEquals("https://image.tmdb.org/t/p/w342/heat.jpg", heat.posterUrl)
            assertEquals(IssueType.Subtitles, heat.type)
            assertEquals(IssueStatus.Open, heat.status)
            assertEquals("ana", heat.reportedBy)
            val listed = received.first { it.url.encodedPath == "/api/v1/issue" }.url
            assertEquals("open", listed.queryParameter("filter"))
            assertNull(listed.queryParameter("requestedBy"))

            vm.setSort(IssueSort.Modified)
            vm.awaitReady { it.sort == IssueSort.Modified }
            vm.issues(IssueFilter.Open).asSnapshot()
            assertTrue(received.any { it.url.encodedPath == "/api/v1/issue" && it.url.queryParameter("sort") == "modified" })
        }

    @Test
    fun `a reporter without the view permission is narrowed to their own issues`() =
        runTest {
            server(CREATE_ISSUES)
            val vm = viewModel()

            val ready = vm.awaitReady { it.scope.currentUserId != null }
            assertEquals(7, ready.scope.requestedBy)

            vm.issues(IssueFilter.All).asSnapshot()

            val listed = received.last { it.url.encodedPath == "/api/v1/issue" }.url
            assertEquals("7", listed.queryParameter("requestedBy"))
            assertEquals("all", listed.queryParameter("filter"))
        }

    private fun json(body: String) = MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
