package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealth
import io.github.scottcooper92.binge.seerr.auth.SeerrConnectionHealthMonitor
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import io.github.scottcooper92.binge.seerr.util.OkHttpDrain
import io.github.scottcooper92.binge.seerr.util.PlainCipher
import io.github.scottcooper92.binge.seerr.util.enqueueProfile
import io.github.scottcooper92.binge.seerr.util.routeProfiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** What the television home shows: setup, the rail, or the sign-in again once the server has rejected the session (#810). */
class TvHomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer().routeProfiles().apply { start() }
    private val baseUrl = server.url("/").toString()
    private val viewModels = ViewModelStore()

    /** Every client's calls, drained before `MainDispatcherRule` resets Main (#903, as #807 did for `ScriptedSeerr`). */
    private val drain = OkHttpDrain()

    @After
    fun tearDown() {
        viewModels.clear()
        drain.awaitIdle()
        server.close()
    }

    private fun TestScope.connection(): SeerrConnection {
        val monitor = SeerrConnectionHealthMonitor()
        return SeerrConnection(
            store =
                CredentialStore(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("tv.preferences_pb") },
                    PlainCipher,
                ),
            apis = SeerrApiFactory(logRequests = false, health = monitor, testDispatcher = drain::newDispatcher),
            healthMonitor = monitor,
        )
    }

    private fun TestScope.viewModel(connection: SeerrConnection): TvHomeViewModel {
        val vm = TvHomeViewModel(connection)
        viewModels.put("home", vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun SeerrConnection.connectHealthy() {
        server.enqueue(json("""{"id":1,"permissions":2}"""))
        server.enqueueProfile(json("""{"version":"3.1.0"}"""), json("""{"initialized":true}"""))
        connect(baseUrl, SeerrAuth.ApiKey("k3y")).getOrThrow()
    }

    @Test
    fun `nothing saved is setup, and a saved server that takes the session is the rail`() =
        runTest {
            val connection = connection()
            val vm = viewModel(connection)
            assertEquals(TvHomeUiState.Setup, vm.uiState.first { it != TvHomeUiState.Loading })

            connection.connectHealthy()

            assertEquals(TvHomeUiState.Connected, vm.uiState.first { it == TvHomeUiState.Connected })
        }

    @Test
    fun `a rejection auth me confirms is the sign-in again, and a new sign-in brings the rail back`() =
        runTest {
            val connection = connection()
            connection.connectHealthy()
            val vm = viewModel(connection)
            vm.uiState.first { it == TvHomeUiState.Connected }

            server.enqueue(MockResponse(code = 401))
            // The interceptor's own probe: auth/me refuses too, so the 401 is the session (#997).
            server.enqueue(MockResponse(code = 401))
            runCatching { connection.api().requests(take = 1) }
            server.enqueue(MockResponse(code = 401))
            vm.uiState.first { it == TvHomeUiState.Reconnect }

            connection.connectHealthy()
            assertEquals(TvHomeUiState.Connected, vm.uiState.first { it == TvHomeUiState.Connected })
        }

    @Test
    fun `a stray 401 that auth me contradicts keeps the rail`() =
        runTest {
            val connection = connection()
            connection.connectHealthy()
            val vm = viewModel(connection)
            vm.uiState.first { it == TvHomeUiState.Connected }

            server.enqueue(MockResponse(code = 401))
            server.enqueue(json("""{"id":1,"permissions":2}"""))
            runCatching { connection.api().requests(take = 1) }
            connection.health.first { it == SeerrConnectionHealth.Healthy }

            assertEquals(TvHomeUiState.Connected, vm.uiState.value)
        }

    private fun json(body: String) = MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)
}
