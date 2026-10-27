package com.cooper.binge.companion.seerr.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import com.cooper.binge.companion.seerr.auth.ConnectionRestore
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.NoConnectionCarrier
import com.cooper.binge.companion.seerr.auth.SecretCipher
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.auth.SeerrConnectionHealthMonitor
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrCredentials
import com.cooper.binge.companion.seerr.util.LostKeyCipher
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.OkHttpDrain
import com.cooper.binge.companion.seerr.util.ReversingCipher
import com.cooper.binge.companion.seerr.util.enqueueProfile
import com.cooper.binge.companion.seerr.util.routeProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Which screen the home lands on. The third state is the one that matters: nothing saved is not an
 * answer until a restore has been tried, or a transferred device is shown setup and asked to sign
 * in again for the second before the restore lands.
 */
private const val SETTLE_MILLIS = 500L

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val viewModels = ViewModelStore()

    @After
    fun tearDown() = viewModels.clear()

    private fun TestScope.store(cipher: SecretCipher = ReversingCipher) =
        CredentialStore(
            dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("creds.preferences_pb") },
            cipher = cipher,
            io = UnconfinedTestDispatcher(testScheduler),
        )

    private fun TestScope.viewModel(
        store: CredentialStore,
        restore: ConnectionRestore,
    ): HomeViewModel {
        val connection = SeerrConnection(store, SeerrApiFactory(logRequests = false))
        val vm = HomeViewModel(connection, restore)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    /** [NoConnectionCarrier] is the production carrier for a device with nothing to carry: it settles without reaching a server. */
    private fun TestScope.restore(store: CredentialStore) =
        ConnectionRestore(store, SeerrApiFactory(logRequests = false), NoConnectionCarrier, backgroundScope)

    @Test
    fun `nothing saved and no restore tried yet is not yet an answer`() =
        runTest {
            val store = store()

            val vm = viewModel(store, restore(store))

            // The store has answered, so a home that keyed off it alone would be showing setup by
            // now. Waited for in real time because DataStore reads on a dispatcher of its own,
            // which a virtual clock cannot advance — and a test that checked too early would pass
            // against the very logic this pins.
            assertNull(store.credentials.first())
            assertNull(
                withContext(Dispatchers.Default) { withTimeoutOrNull(SETTLE_MILLIS) { vm.uiState.first { it != HomeUiState.Resolving } } },
            )
        }

    @Test
    fun `nothing saved once the restore has settled is the setup screen`() =
        runTest {
            val store = store()
            val restore = restore(store)
            val vm = viewModel(store, restore)

            assertEquals(HomeUiState.Resolving, vm.uiState.value)
            restore.run()

            assertEquals(HomeUiState.Setup, withContext(Dispatchers.Default) { vm.uiState.first { it != HomeUiState.Resolving } })
        }

    @Test
    fun `a saved connection is the hub, without waiting on a restore`() =
        runTest {
            val store = store()
            store.save(SeerrCredentials("https://seerr.example/", SeerrAuth.ApiKey("k3y")))

            val vm = viewModel(store, restore(store))

            assertEquals(HomeUiState.Resolving, vm.uiState.value)
            assertEquals(HomeUiState.Connected, withContext(Dispatchers.Default) { vm.uiState.first { it != HomeUiState.Resolving } })
        }

    @Test
    fun `a saved connection whose key is lost is the setup screen, not the hub`() =
        runTest {
            val store = store(LostKeyCipher)
            store.save(SeerrCredentials("https://seerr.example/", SeerrAuth.ApiKey("k3y")))
            val restore = restore(store)
            val vm = viewModel(store, restore)

            assertEquals(HomeUiState.Resolving, vm.uiState.value)
            restore.run()

            assertEquals(HomeUiState.Setup, withContext(Dispatchers.Default) { vm.uiState.first { it != HomeUiState.Resolving } })
        }

    @Test
    fun `a rejection auth me confirms is the sign-in again, and a new sign-in brings the hub back`() =
        runTest {
            val server = MockWebServer().routeProfiles().apply { start() }
            val drain = OkHttpDrain()
            try {
                val monitor = SeerrConnectionHealthMonitor()
                val store = store()
                val connection =
                    SeerrConnection(
                        store = store,
                        apis = SeerrApiFactory(logRequests = false, health = monitor, testDispatcher = drain::newDispatcher),
                        healthMonitor = monitor,
                    )
                val baseUrl = server.url("/").toString()

                suspend fun signIn() {
                    server.enqueue(json("""{"id":1,"permissions":2}"""))
                    server.enqueueProfile(json("""{"version":"3.1.0"}"""), json("""{"initialized":true}"""))
                    connection.connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()
                }
                signIn()
                val vm = HomeViewModel(connection, restore(store))
                viewModels.put("home", vm)
                backgroundScope.launch { vm.uiState.collect {} }
                withContext(Dispatchers.Default) { vm.uiState.first { it == HomeUiState.Connected } }

                server.enqueue(MockResponse(code = 401))
                // The interceptor's own probe: auth/me refuses too, so the 401 is the session (#997).
                server.enqueue(MockResponse(code = 401))
                runCatching { connection.api().requests(take = 1) }
                server.enqueue(MockResponse(code = 401))
                withContext(Dispatchers.Default) { vm.uiState.first { it == HomeUiState.Reconnect } }

                signIn()
                assertEquals(HomeUiState.Connected, withContext(Dispatchers.Default) { vm.uiState.first { it == HomeUiState.Connected } })
            } finally {
                viewModels.clear()
                drain.awaitIdle()
                server.close()
            }
        }

    private fun json(body: String) = MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)
}
