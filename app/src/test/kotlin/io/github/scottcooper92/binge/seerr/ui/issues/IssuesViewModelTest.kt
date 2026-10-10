package io.github.scottcooper92.binge.seerr.ui.issues

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.paging.testing.asSnapshot
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.FakeIssueStore
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.util.FakeRequest
import io.github.scottcooper92.binge.seerr.util.FakeResponse
import io.github.scottcooper92.binge.seerr.util.FakeSeerrServer
import io.github.scottcooper92.binge.seerr.util.FakeTitleDao
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.PlainCipher
import io.github.scottcooper92.binge.seerr.util.RecordingAnalytics
import io.github.scottcooper92.binge.seerr.util.awaitEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val ADMIN = 2
private const val CREATE_ISSUES = 1 shl 22
private const val HTTP_OK = 200
private const val HTTP_SERVER_ERROR = 500
private const val REQUEST_WAIT_MILLIS = 2_000L

/** The browser over an in-memory connection into a path-scripted Seerr, paging through the fake cache. */
class IssuesViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()
    private val received = CopyOnWriteArrayList<FakeRequest>()
    private val viewModels = ViewModelStore()
    private val analytics = RecordingAnalytics()

    /** The viewer's permissions as the server currently has them; a test can change them mid-run. */
    private val viewerPermissions = AtomicInteger(0)

    /** What `auth/me` answers once connected; anything but 200 fails the ViewModel's own read. */
    private val authStatus = AtomicInteger(HTTP_OK)

    /** The server's version and public settings, which a test can change mid-run, as an upgrade in place would. */
    private val serverVersion = AtomicReference("3.1.0")
    private val publicSettings = AtomicReference("""{"mediaServerType":2}""")

    /** The server's issue total, which a test can change mid-run to tell a fresh count from the last one. */
    private val countTotal = AtomicInteger(3)

    /** Set, a count read waits here until the test lets it go, so a test can act while one is running. */
    private val heldCount = AtomicReference<CountDownLatch?>(null)
    private val countStarted = CompletableDeferred<Unit>()

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
                "/api/v1/auth/me" ->
                    if (authStatus.get() == HTTP_OK) {
                        json("""{"id":7,"displayName":"Scott","permissions":${viewerPermissions.get()}}""")
                    } else {
                        FakeResponse(code = authStatus.get())
                    }
                "/api/v1/status" -> json("""{"version":"${serverVersion.get()}"}""")
                "/api/v1/settings/public" -> json(publicSettings.get())
                "/api/v1/issue/count" -> {
                    heldCount.get()?.let { release ->
                        countStarted.complete(Unit)
                        release.await(REQUEST_WAIT_MILLIS, TimeUnit.MILLISECONDS)
                    }
                    json("""{"total":${countTotal.get()},"open":2,"closed":1}""")
                }
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

    /** [authAfterConnect] is what `auth/me` answers once the `connect()` probe has passed. */
    private suspend fun TestScope.viewModel(authAfterConnect: Int = HTTP_OK): IssuesViewModel {
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
        authStatus.set(authAfterConnect)
        val vm = IssuesViewModel(connection, TitleCache(FakeTitleDao()), FakeIssueStore(), mainDispatcherRule.dispatcher, analytics)
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
    fun `a pull re-reads the chip counts once`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()
            vm.awaitReady { it.counts?.total == 3 }
            val before = countReads()

            countTotal.set(4)
            vm.refreshCounts()

            assertEquals(4, vm.awaitReady { it.counts?.total == 4 }.counts?.total)
            assertEquals(before + 1, countReads())
        }

    @Test
    fun `a pull while the counts are being read starts no second read`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()
            vm.awaitReady { it.counts?.total == 3 }
            val before = countReads()
            val release = CountDownLatch(1)
            heldCount.set(release)
            try {
                countTotal.set(4)
                vm.refreshCounts()
                countStarted.await()
                // A second pull while the first read is held: a restart would cancel it and read again (#1193).
                vm.refreshCounts()
                vm.refreshCounts()
            } finally {
                heldCount.set(null)
                release.countDown()
            }

            vm.awaitReady { it.counts?.total == 4 }
            assertEquals(before + 1, countReads())
        }

    /** An Overseerr upgraded in place to 1.30 has counts on the next arrival, with no reconnect (#1074). */
    @Test
    fun `becoming visible re-reads the profile, so a server upgraded in place shows its counts`() =
        runTest {
            serverVersion.set("1.29.0")
            publicSettings.set("{}")
            server(ADMIN)
            val vm = viewModel()
            val before = vm.awaitReady { it.scope.permissions.canManageIssues }
            assertFalse(before.scope.hasCounts)
            assertNull(before.counts)

            serverVersion.set("1.30.0")
            vm.setScreenVisible(true)

            assertEquals(IssueCounts(total = 3, open = 2, resolved = 1), vm.awaitReady { it.counts != null }.counts)
        }

    /** Not a list scoped to a viewer with no permissions, which reads as an empty server (#1073). */
    @Test
    fun `a failed auth me is an error the screen can show, and retry recovers from it`() =
        runTest {
            server(ADMIN)
            val seen = CopyOnWriteArrayList<IssuesUiState>()
            val vm = viewModel(authAfterConnect = HTTP_SERVER_ERROR)
            backgroundScope.launch { vm.uiState.collect { seen += it } }

            assertEquals(IssuesUiState.Error(SeerrError.Server), vm.uiState.first { it is IssuesUiState.Error })
            assertTrue(seen.none { it is IssuesUiState.Ready })

            authStatus.set(HTTP_OK)
            vm.retry()

            assertTrue(
                vm
                    .awaitReady { true }
                    .scope.permissions.canManageIssues,
            )
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
    fun `a reporter without the view permission is scoped to their own issues, which the server narrows itself`() =
        runTest {
            server(CREATE_ISSUES)
            val vm = viewModel()

            val ready = vm.awaitReady { it.scope.currentUserId != null }
            assertEquals(7, ready.scope.createdBy)

            vm.issues(IssueFilter.All).asSnapshot()

            val listed = received.last { it.url.encodedPath == "/api/v1/issue" }.url
            assertNull("Seerr's validator refuses createdBy", listed.queryParameter("createdBy"))
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
            assertEquals(listOf("issue_moderated" to mapOf("action" to "resolved")), analytics.events)
        }

    /** The server refuses a reporter's delete once someone has replied, so none is sent (#1151). */
    @Test
    fun `a reporter's delete is not sent once someone has replied, and is while nobody has`() =
        runTest {
            server(CREATE_ISSUES)
            val vm = viewModel()
            vm.awaitReady { true }
            val heat =
                vm
                    .issues(IssueFilter.Open)
                    .asSnapshot()
                    .single()
                    .copy(reportedById = 7)

            vm.delete(heat.copy(commentCount = 2))
            vm.awaitReady { it.actingIds.isEmpty() }
            assertTrue(received.none { it.method == "DELETE" })

            val event = awaitEvent(vm.events)
            vm.delete(heat.copy(commentCount = 1))
            assertEquals(IssueListEvent.Deleted, event.await())
            assertTrue(received.any { it.method == "DELETE" && it.url.encodedPath == "/api/v1/issue/31" })
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
            assertTrue(analytics.events.isEmpty())
        }

    private fun countReads() = received.count { it.url.encodedPath == "/api/v1/issue/count" }

    private fun json(body: String) = FakeResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)
}
