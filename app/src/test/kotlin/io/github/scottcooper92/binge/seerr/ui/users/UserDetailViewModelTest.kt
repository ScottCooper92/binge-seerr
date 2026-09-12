package io.github.scottcooper92.binge.seerr.ui.users

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.paging.testing.asSnapshot
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.FakeUserStore
import io.github.scottcooper92.binge.seerr.data.UserEntity
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.TitleCache
import io.github.scottcooper92.binge.seerr.ui.hub.HubQuotaBucket
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val ADMIN = 2
private const val MANAGE_USERS = 1 shl 3
private const val REQUEST = 1 shl 5

/** The user page over a real connection into a path-scripted Seerr; Main is real-time, as for the hub. */
class UserDetailViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer()
    private val received = mutableListOf<RecordedRequest>()
    private val responses = mutableMapOf<String, () -> MockResponse>()
    private val viewModels = ViewModelStore()
    private val cache = FakeUserStore()
    private var stores = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    return responses[request.method + " " + request.url.encodedPath]?.invoke() ?: MockResponse(code = 404)
                }
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
        key: String,
        body: String,
    ) {
        responses[key] = { MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body) }
    }

    private fun server(
        viewerId: Int,
        permissions: Int,
    ) {
        serve("GET /api/v1/auth/me", """{"id":$viewerId,"displayName":"Viewer","permissions":$permissions}""")
        serve("GET /api/v1/status", """{"version":"3.1.0"}""")
        serve("GET /api/v1/settings/public", """{"mediaServerType":2}""")
        serve(
            "GET /api/v1/user/8",
            """{"id":8,"displayName":"Ana","email":"ana@example.com","jellyfinUsername":"ana","permissions":${REQUEST or (1 shl 21)},
               "userType":3,"requestCount":3,"createdAt":"2026-01-05T10:00:00.000Z"}""",
        )
        serve("GET /api/v1/user/8/quota", """{"movie":{"days":7,"limit":10,"used":3,"remaining":7},"tv":{"days":7,"limit":0}}""")
        serve("GET /api/v1/user/8/watch_data", """{"playCount":42,"recentlyWatched":[{"id":1,"tmdbId":100,"mediaType":"movie"}]}""")
        serve("GET /api/v1/user/8/watchlist", """{"page":1,"totalPages":1,"totalResults":1,"results":[{"tmdbId":200,"mediaType":"tv"}]}""")
        serve(
            "GET /api/v1/user/8/requests",
            """{"pageInfo":{"pages":1,"results":1},"results":[{"id":11,"status":2,"media":{"tmdbId":100,"mediaType":"movie","status":3}}]}""",
        )
        serve("GET /api/v1/movie/100", """{"title":"Heat","posterPath":"/heat.jpg","releaseDate":"1995-12-15"}""")
        serve("GET /api/v1/tv/200", """{"name":"Severance","posterPath":"/sev.jpg","firstAirDate":"2022-02-18"}""")
        serve("DELETE /api/v1/user/8", "{}")
    }

    private suspend fun TestScope.viewModel(userId: Int = 8): UserDetailViewModel {
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("d${stores++}.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false),
            )
        connection.connect(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
        val vm = UserDetailViewModel(connection, TitleCache(), cache, userId)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun UserDetailViewModel.awaitReady(): UserDetailUiState.Ready =
        uiState.first { it is UserDetailUiState.Ready } as UserDetailUiState.Ready

    @Test
    fun `the page reads as the profile, the quota, the permissions, and the sections the server returned, titled`() =
        runTest {
            server(viewerId = 1, permissions = ADMIN)
            val vm = viewModel()

            val detail = vm.awaitReady().detail

            assertEquals("Ana", detail.item.name)
            assertEquals("ana", detail.item.handle)
            assertEquals(UserOrigin.Jellyfin, detail.item.origin)
            assertEquals(setOf(ManageablePermission.Request, ManageablePermission.ViewIssues), detail.permissions)
            assertEquals(HubQuotaBucket(limit = 10, remaining = 7, days = 7), detail.quota?.movie)
            assertNull(detail.quota?.tv)
            assertEquals(42, detail.watch?.playCount)
            assertEquals(
                TitleCardItem(100, RequestMediaType.Movie, "Heat", "https://image.tmdb.org/t/p/w342/heat.jpg"),
                detail.watch?.recentlyWatched?.single(),
            )
            assertEquals("Severance", detail.watchlist.single().title)
            assertFalse(detail.isSelf)
            assertTrue(detail.canDelete)
            assertEquals(seerr.url("/").toString() + "users/8", detail.webUrl)

            val requests = vm.requests.asSnapshot()
            assertEquals("Heat", requests.single().title)
            assertEquals("20", received.first { it.url.encodedPath == "/api/v1/user/8/requests" }.url.queryParameter("take"))
        }

    @Test
    fun `a page without watch data or a watchlist still shows, and the delete guard follows the server's rules`() =
        runTest {
            server(viewerId = 8, permissions = MANAGE_USERS)
            responses.remove("GET /api/v1/user/8/watch_data")
            responses.remove("GET /api/v1/user/8/watchlist")
            val own = viewModel().awaitReady().detail
            assertNull(own.watch)
            assertTrue(own.watchlist.isEmpty())
            assertTrue(own.isSelf)
            assertFalse(own.canDelete)

            serve("GET /api/v1/auth/me", """{"id":2,"displayName":"Manager","permissions":$MANAGE_USERS}""")
            assertTrue(viewModel().awaitReady().detail.canDelete)

            serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","permissions":$ADMIN}""")
            assertFalse(viewModel().awaitReady().detail.canDelete)

            serve("GET /api/v1/auth/me", """{"id":1,"displayName":"Owner","permissions":$ADMIN}""")
            assertTrue(viewModel().awaitReady().detail.canDelete)
        }

    @Test
    fun `deleting removes the user from the server and the cache, then pops`() =
        runTest {
            server(viewerId = 1, permissions = ADMIN)
            cache.refresh("created", listOf(cachedUser(8), cachedUser(9)), nextSkip = null)
            val vm = viewModel()
            vm.awaitReady()

            vm.deleteUser()

            assertEquals(UserDetailEvent.UserDeleted, vm.events.first())
            assertTrue(received.any { it.method == "DELETE" && it.url.encodedPath == "/api/v1/user/8" })
            assertEquals(listOf(9), cache.rows.map { it.id })
        }

    private fun cachedUser(id: Int) =
        UserEntity(
            listKey = "created",
            id = id,
            name = "User $id",
            email = null,
            handle = null,
            avatarUrl = null,
            origin = "Local",
            permissions = REQUEST,
            requestCount = 0,
            createdAtMillis = null,
            orderIndex = id,
        )

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
