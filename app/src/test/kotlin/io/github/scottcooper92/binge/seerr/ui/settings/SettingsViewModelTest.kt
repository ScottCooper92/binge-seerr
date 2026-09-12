package io.github.scottcooper92.binge.seerr.ui.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrDefaultAccess
import io.github.scottcooper92.binge.seerr.seerr.SeerrLoginRequest
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
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
private const val REQUEST = 32

/** Settings over a real connection into a Seerr scripted by path; Main is real-time, as for the hub. */
class SettingsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer()
    private val responses = mutableMapOf<String, () -> MockResponse>()
    private val viewModels = ViewModelStore()
    private lateinit var connection: SeerrConnection

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    responses[request.url.encodedPath]?.invoke() ?: MockResponse(code = 404)
            }
        seerr.start()
    }

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
        seerr.close()
    }

    private fun serve(
        path: String,
        body: String,
        headers: okhttp3.Headers = headersOf(),
    ) {
        responses[path] =
            { MockResponse(code = 200, headers = headers.newBuilder().add("Content-Type", "application/json").build(), body = body) }
    }

    private fun server(permissions: Int) {
        serve("/api/v1/status", """{"version":"2.7.0","updateAvailable":true,"commitsBehind":3}""")
        serve("/api/v1/settings/public", """{"applicationTitle":"Family","mediaServerType":2}""")
        serve(
            "/api/v1/auth/me",
            """{"id":1,"displayName":"Scott","permissions":$permissions}""",
            headersOf("Set-Cookie", "connect.sid=s; Path=/"),
        )
        serve(
            "/api/v1/settings/main",
            """{"apiKey":"never-shown","applicationTitle":"Family","applicationUrl":"https://seerr.example.com/","appLanguage":"en","hideAvailable":true,
               "defaultPermissions":32,"defaultQuotas":{"movie":{"quotaLimit":5,"quotaDays":7},"tv":{"quotaLimit":0,"quotaDays":7}}}""",
        )
        serve("/api/v1/settings/about", """{"version":"2.7.0","totalRequests":120,"totalMediaItems":900}""")
        serve(
            "/api/v1/settings/jobs",
            """[{"id":"plex-full-scan","name":"Plex Full Library Scan","running":true},{"id":"download-sync","name":"Download Sync","nextExecutionTime":"2099-01-01T00:00:00.000Z"}]""",
        )
        serve(
            "/api/v1/settings/radarr",
            """[{"id":1,"name":"Radarr","hostname":"10.0.0.4","port":7878,"activeProfileName":"HD-1080p","activeDirectory":"/movies","isDefault":true}]""",
        )
        serve("/api/v1/settings/sonarr", """[{"id":2,"name":"Sonarr 4K","externalUrl":"https://sonarr.example.com","is4k":true}]""")
        serve("/api/v1/settings/notifications/email", """{"enabled":true}""")
        serve("/api/v1/settings/notifications/discord", """{"enabled":false}""")
        serve(
            "/api/v1/auth/local",
            """{"id":1,"displayName":"Scott","permissions":$permissions}""",
            headersOf("Set-Cookie", "connect.sid=s; Path=/"),
        )
    }

    private suspend fun TestScope.viewModel(session: Boolean = false): SettingsViewModel {
        connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("s.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false),
            )
        if (session) {
            connection.logIn(seerr.url("/").toString(), SeerrLoginRequest.Local("s@example.com", "pw")).getOrThrow()
        } else {
            connection.connect(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
        }
        val vm = SettingsViewModel(connection, SettingsLoader(connection))
        viewModels.put("settings", vm)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.setScreenVisible(true)
        return vm
    }

    private suspend fun SettingsViewModel.awaitReady(match: (SettingsUiState.Ready) -> Boolean): SettingsUiState.Ready =
        uiState.first { it is SettingsUiState.Ready && match(it) } as SettingsUiState.Ready

    @Test
    fun `an admin sees the connection, the server, and every configuration group`() =
        runTest {
            server(ADMIN)
            val vm = viewModel()

            val ready = vm.awaitReady { it.config?.system != null && it.config?.services != null && it.connection.userName != null }

            assertEquals(SignInKind.ApiKey, ready.connection.signInKind)
            assertEquals("Scott", ready.connection.userName)
            assertEquals(SeerrVariant.Jellyseerr, ready.server.variant)
            assertEquals("2.7.0", ready.server.versionLabel)
            assertEquals(3, ready.server.commitsBehind)
            val config = checkNotNull(ready.config)
            assertEquals(GeneralSettings("Family", "https://seerr.example.com", "en", true), config.general)
            assertEquals(RequestPolicy(SeerrDefaultAccess.RequestWithApproval, RequestLimit(5, 7), null), config.requestPolicy)
            assertEquals(NotificationAgents(emailEnabled = true, discordEnabled = false), config.agents)
            val system = checkNotNull(config.system)
            assertEquals("2.7.0", system.version)
            assertEquals(120, system.totalRequests)
            assertEquals(listOf("Plex Full Library Scan", "Download Sync"), system.jobs.map { it.name })
            assertTrue(system.jobs[0].running)
            assertEquals(
                listOf("http://10.0.0.4:7878", "https://sonarr.example.com"),
                checkNotNull(config.services).map { it.url },
            )
        }

    @Test
    fun `a plain user gets the connection and server but no configuration, and no settings call is made`() =
        runTest {
            server(REQUEST)
            val vm = viewModel(session = true)

            val ready = vm.awaitReady { it.connection.userName != null }

            assertEquals(SignInKind.Session, ready.connection.signInKind)
            assertNull(ready.config)
            responses["/api/v1/settings/main"] = { error("A restricted user must not read the settings") }
            vm.setScreenVisible(true)
            assertNull(vm.awaitReady { it.connection.userName != null }.config)
        }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
