package com.cooper.binge.companion.seerr.ui.hub

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.auth.LegacyCredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.auth.SeerrHealth
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.PlainCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The account in a title bar reads the user and the quota alone, and is absent rather than failing where the user cannot be read (#2366). */
class AccountBarLoaderTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()
    private val paths = mutableListOf<String>()
    private var meCode = 200
    private var meName = "Scott"
    private lateinit var connection: SeerrConnection

    @After
    fun tearDown() {
        seerr.awaitIdle()
    }

    private suspend fun TestScope.loader(): HubOverviewLoader {
        seerr.dispatcher = { request ->
            paths += request.url.encodedPath
            val json = headersOf("Content-Type", "application/json")
            when (request.url.encodedPath) {
                "/api/v1/status" -> FakeResponse(headers = json, body = """{"version":"3.4.0"}""")
                "/api/v1/auth/me" ->
                    if (meCode == 200) {
                        FakeResponse(headers = json, body = """{"id":1,"displayName":"$meName","permissions":2}""")
                    } else {
                        FakeResponse(code = meCode)
                    }
                else -> FakeResponse(code = 404)
            }
        }
        val monitor = SeerrHealth.standalone()
        connection =
            SeerrConnection(
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
        connection.connect(seerr.url("/"), SeerrAuth.ApiKey("k3y")).getOrThrow()
        paths.clear()
        return HubOverviewLoader(connection)
    }

    @Test
    fun `it reads the user and a quota the server will not answer is left out`() =
        runTest {
            val bar = loader().accountBar()

            assertNotNull(bar)
            assertEquals("Scott", bar?.account?.name)
            assertNull(bar?.quota)
        }

    @Test
    fun `it reads no counts or downloads`() =
        runTest {
            loader().accountBar()

            assertEquals(emptyList<String>(), paths.filter { "count" in it || it.endsWith("/request") })
        }

    @Test
    fun `a user that cannot be read leaves the bar without an account`() =
        runTest {
            val loader = loader()
            meCode = 503

            assertNull(loader.accountBar())
        }

    @Test
    fun `the bar shows the new account after the connection is edited`() =
        runTest(UnconfinedTestDispatcher()) {
            val loader = loader()
            val viewModel = AccountBarViewModel(loader, connection)
            viewModel.uiState.launchIn(backgroundScope)
            val before = viewModel.uiState.first { it is AccountBarState.Ready } as AccountBarState.Ready
            assertEquals("Scott", before.bar.account.name)

            meName = "Sam"
            connection.connect(seerr.url("/"), SeerrAuth.ApiKey("an0ther")).getOrThrow()

            val after = viewModel.uiState.first { it is AccountBarState.Ready && it.bar.account.name == "Sam" }
            assertNotNull(after)
        }
}
