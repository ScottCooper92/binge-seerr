package com.cooper.binge.companion.seerr.ui.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.auth.SeerrConnectionHealthMonitor
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.PlainCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The search page reads who the viewer is and what the server has, and says so when it cannot. */
class SettingsSearchViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()
    private var permissions = 2

    @Before
    fun setUp() {
        seerr.dispatcher = { request ->
            val body =
                when (request.url.encodedPath) {
                    "/api/v1/status" -> """{"version":"3.4.0"}"""
                    "/api/v1/settings/public" -> """{"applicationTitle":"Family","mediaServerType":1}"""
                    "/api/v1/auth/me" -> """{"id":7,"displayName":"Sam","permissions":$permissions}"""
                    else -> null
                }
            body?.let { FakeResponse(headers = headersOf("Content-Type", "application/json"), body = it) } ?: FakeResponse(code = 404)
        }
    }

    @After
    fun tearDown() {
        seerr.awaitIdle()
    }

    private suspend fun TestScope.connection(): SeerrConnection {
        val monitor = SeerrConnectionHealthMonitor()
        return SeerrConnection(
            store =
                CredentialStore(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("p.preferences_pb") },
                    PlainCipher,
                    UnconfinedTestDispatcher(testScheduler),
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
    }

    private suspend fun SettingsSearchViewModel.settled() = uiState.first { it != SettingsSearchUiState.Loading }

    @Test
    fun `an admin's access carries their id and the server's profile`() =
        runTest {
            val connection = connection()
            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()

            val state = SettingsSearchViewModel(connection).settled() as SettingsSearchUiState.Ready

            assertEquals(7, state.access.userId)
            assertTrue(state.access.isAdmin)
            assertTrue(state.access.profile.hasNetworkSettings)
        }

    @Test
    fun `a viewer without the admin bit is not an admin`() =
        runTest {
            permissions = 32
            val connection = connection()
            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()

            val state = SettingsSearchViewModel(connection).settled() as SettingsSearchUiState.Ready

            assertFalse(state.access.isAdmin)
        }

    @Test
    fun `a connection that was never made is an error that a retry tries again`() =
        runTest {
            val connection = connection()
            val viewModel = SettingsSearchViewModel(connection)
            assertEquals(SettingsSearchUiState.Error, viewModel.settled())

            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
            viewModel.retry()

            assertTrue(viewModel.settled() is SettingsSearchUiState.Ready)
        }
}
