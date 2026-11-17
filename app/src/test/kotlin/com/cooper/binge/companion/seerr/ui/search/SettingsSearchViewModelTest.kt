package com.cooper.binge.companion.seerr.ui.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.auth.LegacyCredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.auth.SeerrConnectionHealthMonitor
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsPage
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

    private var store: RecentSearches? = null

    /** One store for the test, so a second ViewModel over it sees what the first remembered. */
    private fun TestScope.recents(): RecentSearches =
        store
            ?: RecentSearches(PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("r.preferences_pb") }).also {
                store =
                    it
            }

    private suspend fun TestScope.connection(): SeerrConnection {
        val monitor = SeerrConnectionHealthMonitor()
        return SeerrConnection(
            store =
                LegacyCredentialStore(
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

            val state = SettingsSearchViewModel(connection, recents()).settled() as SettingsSearchUiState.Ready

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

            val state = SettingsSearchViewModel(connection, recents()).settled() as SettingsSearchUiState.Ready

            assertFalse(state.access.isAdmin)
        }

    @Test
    fun `a connection that was never made is an error that a retry tries again`() =
        runTest {
            val connection = connection()
            val viewModel = SettingsSearchViewModel(connection, recents())
            assertEquals(SettingsSearchUiState.Error(SeerrError.NotConnected), viewModel.settled())

            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
            viewModel.retry()

            assertTrue(viewModel.settled() is SettingsSearchUiState.Ready)
        }

    private val logs = settingsIndex.single { it.target == SearchTarget.Server(ServerSettingsPage.Logs) }
    private val password = settingsIndex.single { it.target == SearchTarget.Account(UserSettingsPage.Password) }

    @Test
    fun `an entry opened is remembered, newest first, and shows as a recent`() =
        runTest {
            val connection = connection()
            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
            val viewModel = SettingsSearchViewModel(connection, recents())
            viewModel.settled()

            viewModel.remember(logs)
            viewModel.remember(password)

            val ready = viewModel.uiState.first { (it as? SettingsSearchUiState.Ready)?.recent?.size == 2 } as SettingsSearchUiState.Ready
            assertEquals(listOf(password, logs), ready.recent)
        }

    @Test
    fun `a recent the viewer may no longer open is left out`() =
        runTest {
            permissions = 32
            val connection = connection()
            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
            val recents = recents()
            recents.remember(logs.id)
            recents.remember(password.id)
            recents.remember("a.page.the.app.dropped")

            val ready = SettingsSearchViewModel(connection, recents).settled() as SettingsSearchUiState.Ready

            assertEquals("the server's Logs is an admin's, and the dropped id names nothing", listOf(password), ready.recent)
        }

    @Test
    fun `clearing empties the recents`() =
        runTest {
            val connection = connection()
            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
            val viewModel = SettingsSearchViewModel(connection, recents())
            viewModel.settled()
            viewModel.remember(logs)
            viewModel.uiState.first { (it as? SettingsSearchUiState.Ready)?.recent?.size == 1 }

            viewModel.clearRecent()

            val ready =
                viewModel.uiState.first {
                    (it as? SettingsSearchUiState.Ready)?.recent?.isEmpty() == true
                } as SettingsSearchUiState.Ready
            assertEquals(emptyList<SettingEntry>(), ready.recent)
        }
}
