package io.github.scottcooper92.binge.seerr.ui.users

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.paging.testing.asSnapshot
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.data.FakeUserStore
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
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

/** The browser over a real connection into a path-scripted Seerr, paging through the fake cache; Main is real-time. */
class UsersViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer()
    private val received = mutableListOf<RecordedRequest>()
    private val viewModels = ViewModelStore()
    private val cache = FakeUserStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    return when (request.method + " " + request.url.encodedPath) {
                        "GET /api/v1/auth/me" -> json("""{"id":7,"displayName":"Scott","permissions":$ADMIN}""")
                        "GET /api/v1/status" -> json("""{"version":"3.1.0"}""")
                        "GET /api/v1/settings/public" -> json("""{"mediaServerType":2}""")
                        "GET /api/v1/user" ->
                            json(
                                """{"pageInfo":{"pages":1,"results":2},"results":[
                                   {"id":7,"displayName":"Scott","permissions":2,"userType":3,"requestCount":12},
                                   {"id":8,"displayName":"Ana","email":"ana@example.com","permissions":32,"userType":3,"requestCount":3}]}""",
                            )
                        "PUT /api/v1/user" -> json("[]")
                        else -> MockResponse(code = 404)
                    }
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

    private suspend fun TestScope.viewModel(): UsersViewModel {
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("u.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false),
            )
        connection.connect(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
        val vm = UsersViewModel(connection, cache)
        viewModels.put("users", vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun UsersViewModel.awaitReady(match: (UsersUiState.Ready) -> Boolean = { true }): UsersUiState.Ready =
        uiState.first { it is UsersUiState.Ready && match(it) } as UsersUiState.Ready

    @Test
    fun `the list reads through the cache in the chosen order, and the jellyseerr lineage offers the blocklist toggles`() =
        runTest {
            val vm = viewModel()
            val ready = vm.awaitReady { ManageablePermission.ViewBlocklist in it.offered }
            assertTrue(ManageablePermission.ViewBlocklist in ready.offered)

            val rows = vm.users.asSnapshot()

            assertEquals(listOf("Scott", "Ana"), rows.map { it.name })
            assertTrue(rows.first().isAdmin)
            assertFalse(rows.last().isAdmin)
            assertEquals(UserOrigin.Jellyfin, rows.last().origin)
            assertEquals("created", received.first { it.url.encodedPath == "/api/v1/user" }.url.queryParameter("sort"))

            vm.setSort(UserSort.Requests)
            vm.awaitReady { it.sort == UserSort.Requests }
            vm.users.asSnapshot()
            assertTrue(received.any { it.url.encodedPath == "/api/v1/user" && it.url.queryParameter("sort") == "requests" })
        }

    @Test
    fun `a bulk edit writes one permission set to every selected user and moves their cached rows`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()
            vm.users.asSnapshot()

            vm.toggleSelected(8)
            vm.toggleSelected(7)
            vm.toggleSelected(7)
            assertEquals(setOf(8), vm.awaitReady { it.selection == setOf(8) }.selection)
            vm.startBulkEdit()
            vm.togglePermission(ManageablePermission.Request)
            vm.togglePermission(ManageablePermission.ManageIssues)
            assertEquals(
                setOf(ManageablePermission.Request, ManageablePermission.ManageIssues),
                vm.awaitReady { it.edit?.selected?.size == 2 }.edit?.selected,
            )

            vm.applyBulkEdit()

            assertEquals(UsersEvent.PermissionsSaved(1), vm.events.first())
            val put =
                received
                    .single { it.method == "PUT" }
                    .body
                    ?.utf8()
                    .orEmpty()
            assertTrue(put, put.contains("\"ids\":[8]"))
            assertTrue(put, put.contains("\"permissions\":${ManageablePermission.Request.bit or ManageablePermission.ManageIssues.bit}"))
            val settled = vm.awaitReady { it.edit == null }
            assertTrue(settled.selection.isEmpty())
            assertNull(settled.edit)
            assertEquals(
                ManageablePermission.Request.bit or ManageablePermission.ManageIssues.bit,
                cache.rows.first { it.id == 8 }.permissions,
            )
            assertEquals(ADMIN, cache.rows.first { it.id == 7 }.permissions)
        }

    private fun json(body: String) = MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
