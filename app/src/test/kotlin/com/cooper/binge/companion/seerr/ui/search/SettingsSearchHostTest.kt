package com.cooper.binge.companion.seerr.ui.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.auth.LegacyCredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.auth.SeerrHealth
import com.cooper.binge.companion.seerr.flags.LocalSearchTogglesFlag
import com.cooper.binge.companion.seerr.flags.NoSearchTogglesOverride
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.ui.settings.server.ServerSettingsPage
import com.cooper.binge.companion.seerr.ui.users.settings.UserSettingsPage
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.PlainCipher
import com.cooper.homelab.core.foundation.ServerId
import com.cooper.homelab.core.presentation.search.DataStoreRecentSearches
import com.cooper.homelab.core.presentation.search.SettingEntry
import com.cooper.homelab.core.presentation.search.SettingsSearchState
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The search reads who the viewer is and what the server has, offers what they may open, and says so when it cannot read it. */
@RunWith(RobolectricTestRunner::class)
class SettingsSearchHostTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()
    private var permissions = 2
    private var stores = 0

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

    private fun TestScope.data() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("h${stores++}.preferences_pb") }

    private suspend fun TestScope.connection(): SeerrConnection {
        val monitor = SeerrHealth.standalone()
        return SeerrConnection(
            store = LegacyCredentialStore(data(), PlainCipher, UnconfinedTestDispatcher(testScheduler)),
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

    private fun TestScope.host(
        connection: SeerrConnection,
        recents: DataStoreRecentSearches = DataStoreRecentSearches(data(), ServerId("server-1"), "recent_entries"),
    ) = SettingsSearchHost(
        context = ApplicationProvider.getApplicationContext(),
        connection = connection,
        recents = recents,
        flag = LocalSearchTogglesFlag(NoSearchTogglesOverride, backgroundScope),
        appScope = backgroundScope,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private suspend fun SettingsSearchHost.ready() = model.state.first { it != SettingsSearchState.Loading }

    private fun SettingsSearchHost.entry(id: String): SettingEntry<SeerrSearchTarget> = entries.single { it.id == id }

    private val logsId = "server.${ServerSettingsPage.Logs.name}|"
    private val passwordId = "account.${UserSettingsPage.Password.name}|"

    @Test
    fun `an admin is offered the server's pages, with their own id for the route`() =
        runTest {
            val connection = connection()
            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
            val host = host(connection)

            val state = host.ready() as SettingsSearchState.Ready<SeerrSearchTarget, SearchAccess>

            assertEquals(7, state.viewer.userId)
            assertTrue(state.available(host.entry(logsId)))
        }

    @Test
    fun `a viewer without the admin bit is not offered the server's pages`() =
        runTest {
            permissions = 32
            val connection = connection()
            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
            val host = host(connection)

            val state = host.ready() as SettingsSearchState.Ready<SeerrSearchTarget, SearchAccess>

            assertFalse(state.available(host.entry(logsId)))
            assertTrue(state.available(host.entry(passwordId)))
        }

    @Test
    fun `the search offers no switches unless the flag is on`() =
        runTest {
            val off = host(connection())
            assertFalse(off.searchTogglesEnabled.value)
        }

    @Test
    fun `a connection that was never made fails, and a retry tries again`() =
        runTest {
            val connection = connection()
            val host = host(connection)
            assertTrue(host.ready() is SettingsSearchState.Failed)

            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
            host.model.retry()

            assertTrue(host.ready() is SettingsSearchState.Ready)
        }

    @Test
    fun `a recent the viewer may no longer open is left out`() =
        runTest {
            permissions = 32
            val connection = connection()
            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
            val recents = DataStoreRecentSearches(data(), ServerId("server-1"), "recent_entries")
            recents.remember(logsId)
            recents.remember(passwordId)
            recents.remember("a.page.the.app.dropped")

            val ready = host(connection, recents).ready() as SettingsSearchState.Ready<SeerrSearchTarget, SearchAccess>

            assertEquals(
                "the server's Logs is an admin's, and the dropped id names nothing",
                listOf(passwordId),
                ready.recent.map { it.id },
            )
        }
}
