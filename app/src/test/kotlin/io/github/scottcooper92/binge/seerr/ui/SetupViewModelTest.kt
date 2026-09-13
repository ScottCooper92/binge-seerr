package io.github.scottcooper92.binge.seerr.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.PlexPinFlow
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.PlexClientIdentity
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.seerr.plexTvApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
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
import kotlin.time.Duration.Companion.milliseconds

/**
 * The setup screen's two steps over a real connection: DataStore in a temp file, HTTP into a
 * scripted Seerr and, for the Plex flow, a scripted plex.tv. Every outcome that crosses a thread is
 * awaited by its shape rather than read off the state, which would read whatever was there last.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer().apply { start() }
    private val plex = MockWebServer().apply { start() }

    /** Every ViewModel goes in here and is cleared on teardown, so no link poll outlives its test. */
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    /**
     * Main is set on every setup and never reset: a callback still in flight at teardown would
     * otherwise dispatch into the unset window and be reported into whichever test runs next.
     */
    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
        plex.close()
    }

    private lateinit var connection: SeerrConnection

    /** The state is shared WhileSubscribed, so a collector is kept open for the test's life. */
    private fun TestScope.viewModel(reuseConnection: Boolean = false): SetupViewModel {
        if (!reuseConnection) {
            connection =
                SeerrConnection(
                    store =
                        CredentialStore(
                            PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c.preferences_pb") },
                            PlainCipher,
                        ),
                    apis = SeerrApiFactory(logRequests = false),
                    quickConnectPollInterval = 10.milliseconds,
                )
        }
        val vm =
            SetupViewModel(
                connection = connection,
                plex =
                    PlexPinFlow(
                        identity = { PlexClientIdentity(identifier = "cid", product = "Binge Seerr", version = "0.1.0", device = "Pixel") },
                        apis = { plexTvApi(it, plex.url("/").toString()) },
                        pollInterval = 10.milliseconds,
                    ),
            )
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun SetupViewModel.awaitAddress(match: (SetupUiState.Address) -> Boolean = { true }): SetupUiState.Address =
        uiState.first { it is SetupUiState.Address && match(it) } as SetupUiState.Address

    private suspend fun SetupViewModel.awaitSignIn(match: (SetupUiState.SignIn) -> Boolean = { true }): SetupUiState.SignIn =
        uiState.first { it is SetupUiState.SignIn && match(it) } as SetupUiState.SignIn

    private suspend fun SetupViewModel.awaitConnected(): SetupUiState.Connected =
        uiState.first { it is SetupUiState.Connected } as SetupUiState.Connected

    /** Scripts the three unauthenticated calls the address step makes, then reads the server. */
    private suspend fun SetupViewModel.inspect(
        status: String,
        settings: String,
        backdrops: String = "[]",
    ): SetupUiState.SignIn {
        seerr.enqueue(json(status))
        seerr.enqueue(json(settings))
        seerr.enqueue(json(backdrops))
        awaitAddress()
        editAddress(seerr.url("/").toString())
        inspect()
        return awaitSignIn()
    }

    @Test
    fun `the address step reads the server first, and offers only that server's sign-ins`() =
        runTest {
            val vm = viewModel()

            val signIn =
                vm.inspect(
                    status = """{"version":"3.4.0"}""",
                    settings = """{"mediaServerType":2,"localLogin":true,"jellyfinServerName":"Home"}""",
                    backdrops = """["/a.jpg","/b.jpg"]""",
                )

            val server = signIn.server
            assertEquals(
                listOf(SeerrSignInMode.Jellyfin, SeerrSignInMode.QuickConnect, SeerrSignInMode.Local, SeerrSignInMode.ApiKey),
                server.modes,
            )
            assertEquals("Seerr", server.title)
            assertEquals(SeerrVariant.Seerr, server.variant)
            assertEquals("3.4.0", server.versionLabel)
            assertEquals("Home", server.mediaServerName)
            assertEquals("https://image.tmdb.org/t/p/w1280/a.jpg", server.backdropUrl)
            assertFalse(server.canResetPassword)
            assertEquals(SeerrSignInMode.Jellyfin, signIn.form.mode)
            assertFalse(signIn.form.canSubmit)
        }

    @Test
    fun `overseerr offers plex, the local account and the key, and a reset where email is on`() =
        runTest {
            val vm = viewModel()

            val signIn = vm.inspect("""{"version":"1.33.2"}""", """{"applicationTitle":"Family","localLogin":true,"emailEnabled":true}""")

            assertEquals(listOf(SeerrSignInMode.Plex, SeerrSignInMode.Local, SeerrSignInMode.ApiKey), signIn.server.modes)
            assertEquals("Family", signIn.server.title)
            assertTrue(signIn.server.canResetPassword)
            assertTrue(signIn.form.canSubmit)
        }

    @Test
    fun `a plex-backed jellyseerr offers plex, and a server with media-server login off offers only its own`() =
        runTest {
            val vm = viewModel()
            val plexBacked = vm.inspect("""{"version":"2.7.0"}""", """{"mediaServerType":1}""")
            assertEquals(listOf(SeerrSignInMode.Plex, SeerrSignInMode.Local, SeerrSignInMode.ApiKey), plexBacked.server.modes)

            vm.changeServer()
            val localOnly = vm.inspect("""{"version":"3.1.0"}""", """{"mediaServerType":2,"mediaServerLogin":false}""")

            assertEquals(listOf(SeerrSignInMode.Local, SeerrSignInMode.ApiKey), localOnly.server.modes)
            assertEquals(SeerrSignInMode.Local, localOnly.form.mode)
        }

    @Test
    fun `an address that is not a Seerr server is said so before any credential is typed`() =
        runTest {
            seerr.enqueue(MockResponse(code = 404))
            seerr.enqueue(MockResponse(code = 404))
            val vm = viewModel()
            vm.awaitAddress()
            vm.editAddress(seerr.url("/").toString())

            vm.inspect()

            assertEquals(SetupError.NotSeerr, vm.awaitAddress { it.error != null }.error)
            assertEquals(2, seerr.requestCount)
            vm.editAddress("other")
            assertNull(vm.awaitAddress { it.serverUrl == "other" }.error)
        }

    @Test
    fun `a malformed address never reaches the network, and a public http one is flagged`() =
        runTest {
            val vm = viewModel()
            vm.awaitAddress()
            vm.editAddress("http://")

            vm.inspect()

            assertEquals(SetupError.InvalidUrl, vm.awaitAddress { it.error != null }.error)
            assertEquals(0, seerr.requestCount)
            vm.editAddress("http://seerr.example.com")
            assertTrue(vm.awaitAddress { it.serverUrl.endsWith("example.com") }.insecure)
        }

    @Test
    fun `a key connect saves the connection and drops the key from the form`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"3.0.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.ApiKey, apiKey = "k3y") }
            seerr.enqueue(json("""{"id":1,"permissions":2}"""))
            seerr.enqueue(json("""{"version":"3.0.0"}"""))
            seerr.enqueue(json("""{"mediaServerType":2}"""))

            vm.connect()

            assertEquals(SeerrVariant.Seerr, vm.awaitConnected().credentials.variant)

            connection.disconnect()

            val back = vm.awaitSignIn()
            assertEquals("", back.form.apiKey)
            assertEquals(SeerrSignInMode.ApiKey, back.form.mode)
        }

    @Test
    fun `a rejected key is reported as rejected, and editing clears it`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"3.0.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.ApiKey, apiKey = "bad") }
            seerr.enqueue(MockResponse(code = 401))

            vm.connect()

            assertEquals(SetupError.Rejected, vm.awaitSignIn { it.error != null }.error)
            vm.editForm { copy(apiKey = "better") }
            assertNull(vm.awaitSignIn { it.form.apiKey == "better" }.error)
        }

    @Test
    fun `a local login sends the email and keeps the session`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"2.0.0"}""", """{"mediaServerType":2,"localLogin":true}""")
            vm.editForm { copy(mode = SeerrSignInMode.Local, email = "s@example.com", password = "pw") }
            repeat(3) { seerr.takeRequest() }
            seerr.enqueue(json("""{"id":42}""", headersOf("Set-Cookie", "connect.sid=s; Path=/")))
            seerr.enqueue(json("""{"version":"2.0.0"}"""))
            seerr.enqueue(json("""{"mediaServerType":2}"""))

            vm.connect()

            assertEquals(SeerrVariant.Jellyseerr, vm.awaitConnected().credentials.variant)
            assertEquals("/api/v1/auth/local", seerr.takeRequest().url.encodedPath)
        }

    @Test
    fun `forgot password posts the address and says a link is on its way`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"1.33.2"}""", """{"localLogin":true,"emailEnabled":true}""")
            vm.editForm { copy(mode = SeerrSignInMode.Local, email = "s@example.com") }
            repeat(3) { seerr.takeRequest() }
            seerr.enqueue(json("""{"status":"ok"}"""))

            vm.requestPasswordReset()

            assertEquals(SetupNotice.ResetEmailSent, vm.awaitSignIn { it.notice != null }.notice)
            val reset = seerr.takeRequest()
            assertEquals("/api/v1/auth/reset-password", reset.url.encodedPath)
            assertTrue(
                reset.body
                    ?.utf8()
                    .orEmpty()
                    .contains("s@example.com"),
            )
        }

    @Test
    fun `quick connect shows the code, polls until approved, then signs in`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"3.4.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.QuickConnect) }
            repeat(3) { seerr.takeRequest() }
            seerr.enqueue(json("""{"code":"123456","secret":"abcdef12"}"""))
            seerr.enqueue(json("""{"authenticated":false}"""))
            seerr.enqueue(json("""{"authenticated":true}"""))
            seerr.enqueue(json("""{"id":7}""", headersOf("Set-Cookie", "connect.sid=qc; Path=/")))
            seerr.enqueue(json("""{"version":"3.4.0"}"""))
            seerr.enqueue(json("""{"mediaServerType":2}"""))

            vm.connect()

            assertEquals(LinkFlow.QuickConnect("123456"), vm.awaitSignIn { it.link != null }.link)
            assertEquals(
                7,
                vm
                    .awaitConnected()
                    .credentials.auth
                    .let { (it as io.github.scottcooper92.binge.seerr.seerr.SeerrAuth.Session).userId },
            )
            assertEquals("/api/v1/auth/jellyfin/quickconnect/initiate", seerr.takeRequest().url.encodedPath)
        }

    @Test
    fun `an expired quick connect code is reported and the code taken down`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"3.4.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.QuickConnect) }
            seerr.enqueue(json("""{"code":"123456","secret":"abcdef12"}"""))
            seerr.enqueue(MockResponse(code = 404))

            vm.connect()

            val failed = vm.awaitSignIn { it.error != null }
            assertEquals(SetupError.LinkExpired, failed.error)
            assertNull(failed.link)
        }

    @Test
    fun `plex mints a pin, opens its page once, and signs in with the token it is approved with`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"1.33.2"}""", """{"localLogin":true}""")
            repeat(3) { seerr.takeRequest() }
            plex.enqueue(json("""{"id":41,"code":"ABCD","expiresAt":"2099-01-01T00:00:00Z"}"""))
            plex.enqueue(json("""{"id":41,"code":"ABCD","authToken":null}"""))
            plex.enqueue(json("""{"id":41,"code":"ABCD","authToken":"tok3n"}"""))
            seerr.enqueue(json("""{"id":9}""", headersOf("Set-Cookie", "connect.sid=plx; Path=/")))
            seerr.enqueue(json("""{"version":"1.33.2"}"""))
            seerr.enqueue(json("""{"localLogin":true}"""))

            vm.connect()

            val link = vm.awaitSignIn { it.link != null }.link as LinkFlow.Plex
            assertEquals("ABCD", link.code)
            assertTrue(link.launchPending)
            assertTrue(link.authUrl.startsWith("https://app.plex.tv/auth#?clientID=cid&code=ABCD"))
            vm.plexLaunched()
            assertEquals(SeerrVariant.Overseerr, vm.awaitConnected().credentials.variant)
            val login = seerr.takeRequest()
            assertEquals("/api/v1/auth/plex", login.url.encodedPath)
            assertTrue(
                login.body
                    ?.utf8()
                    .orEmpty()
                    .contains("tok3n"),
            )
        }

    @Test
    fun `cancelling a link stops the wait and shows no error`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"3.4.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.QuickConnect) }
            seerr.enqueue(json("""{"code":"123456","secret":"abcdef12"}"""))
            seerr.enqueue(json("""{"authenticated":false}"""))
            vm.connect()
            vm.awaitSignIn { it.link != null }

            vm.cancelLink()

            val after = vm.awaitSignIn { it.link == null }
            assertNull(after.error)
            assertFalse(after.isConnecting)
        }

    @Test
    fun `editing the connection keeps the saved server until a new one is accepted`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"3.0.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.ApiKey, apiKey = "k3y") }
            seerr.enqueue(json("""{"id":1,"permissions":2}"""))
            seerr.enqueue(json("""{"version":"3.0.0"}"""))
            seerr.enqueue(json("""{"mediaServerType":2}"""))
            vm.connect()
            val original = vm.awaitConnected().credentials
            repeat(6) { seerr.takeRequest() }

            // Edit connection opens on its own ViewModel over the same connection, as the entry does.
            val editor = viewModel(reuseConnection = true)
            seerr.enqueue(json("""{"version":"3.0.0"}"""))
            seerr.enqueue(json("""{"mediaServerType":2}"""))
            seerr.enqueue(json("[]"))
            editor.beginEdit()
            val editing = editor.awaitSignIn { !it.isConnecting }
            assertEquals(original.baseUrl, editing.server.baseUrl)

            seerr.enqueue(MockResponse(code = 401))
            editor.editForm { copy(mode = SeerrSignInMode.ApiKey, apiKey = "wrong") }
            editor.connect()
            assertEquals(SetupError.Rejected, editor.awaitSignIn { it.error != null }.error)
            assertEquals(original, connection.credentials.first())

            seerr.enqueue(json("""{"id":1,"permissions":2}"""))
            seerr.enqueue(json("""{"version":"3.0.0"}"""))
            seerr.enqueue(json("""{"mediaServerType":2}"""))
            editor.editForm { copy(apiKey = "n3w") }
            editor.connect()
            assertEquals(SeerrAuth.ApiKey("n3w"), editor.awaitConnected().credentials.auth)
        }

    private fun json(
        body: String,
        headers: okhttp3.Headers = headersOf(),
    ): MockResponse = MockResponse(code = 200, headers = headers.newBuilder().add("Content-Type", "application/json").build(), body = body)

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
