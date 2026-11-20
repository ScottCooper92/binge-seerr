package com.cooper.binge.companion.seerr.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.binge.companion.kit.SecretCipher
import com.cooper.binge.companion.seerr.auth.LegacyCredentialStore
import com.cooper.binge.companion.seerr.auth.PlexPinFlow
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.handoff.AddressHandOffs
import com.cooper.binge.companion.seerr.handoff.HandOffOpening
import com.cooper.binge.companion.seerr.seerr.PlexClientIdentity
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.seerr.plexTvApi
import com.cooper.binge.companion.seerr.util.FakeRequest
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.PlainCipher
import com.cooper.binge.companion.seerr.util.ReversingCipher
import com.cooper.homelab.core.presentation.connect.ConnectError
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.server.network.DataStoreCleartextConsent
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.core.testing.InMemoryDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.GeneralSecurityException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

/**
 * Plex and Quick Connect on the shared setup, over the library's link flows: the code, the wait, the television's short PIN, and the
 * wait picked up again after the process died while the user was away approving.
 */
class SharedSetupLinksTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = FakeSeerrServer()
    private val plex = FakeSeerrServer()
    private val viewModels = ViewModelStore()
    private val initiates = AtomicInteger(0)
    private val cleartext = DataStoreCleartextConsent(InMemoryDataStore()) { null }
    private var stores = 0
    private lateinit var connection: SeerrConnection

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.awaitIdle()
        plex.awaitIdle()
    }

    private fun TestScope.viewModel(
        reuseConnection: Boolean = false,
        savedState: SavedStateHandle = SavedStateHandle(),
        cipher: SecretCipher = PlainCipher,
    ): SharedSetupViewModel {
        if (!reuseConnection) {
            connection =
                SeerrConnection(
                    store =
                        LegacyCredentialStore(
                            PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c${stores++}.preferences_pb") },
                            PlainCipher,
                            UnconfinedTestDispatcher(testScheduler),
                        ),
                    apis = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher),
                    quickConnectPollInterval = 10.milliseconds,
                    cleartext = cleartext,
                )
        }
        val vm =
            SharedSetupViewModel(
                connection = connection,
                plex =
                    PlexPinFlow(
                        identity = { PlexClientIdentity("cid", "Seerr Companion", "0.1.0", "Pixel") },
                        apis = { plexTvApi(it, plex.url("/"), testTransport = plex.interceptor(), testDispatcher = plex.newDispatcher()) },
                        pollInterval = 10.milliseconds,
                    ),
                savedState = savedState,
                cipher = cipher,
                handOffs = AddressHandOffs { HandOffOpening.NoLocalNetwork },
                cleartext = cleartext,
                localNetwork = LocalNetworkPermission.AlwaysGranted,
                dispatcher = mainDispatcherRule.dispatcher,
            )
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        backgroundScope.launch { vm.connectUi.collect {} }
        return vm
    }

    private suspend fun SharedSetupViewModel.awaitSignIn(match: (SetupUiState.SignIn) -> Boolean = { true }): SetupUiState.SignIn =
        uiState.first { it is SetupUiState.SignIn && match(it) } as SetupUiState.SignIn

    private suspend fun SharedSetupViewModel.awaitLink(): SeerrConnectUi = connectUi.first { it.link != null }

    private suspend fun SharedSetupViewModel.awaitConnected(): SetupUiState.Connected =
        uiState.first { it is SetupUiState.Connected } as SetupUiState.Connected

    /** Scripts the address step's reads, then reads the server. */
    private suspend fun SharedSetupViewModel.inspect(
        status: String,
        settings: String,
    ): SetupUiState.SignIn {
        seerr.enqueueProfile(json(status), json(settings))
        seerr.enqueue(json("[]"))
        uiState.first { it is SetupUiState.Address }
        editAddress(seerr.url("/"))
        inspect()
        return awaitSignIn()
    }

    private fun quickConnectServer(approved: AtomicBoolean): (FakeRequest) -> FakeResponse =
        { request ->
            when (request.url.encodedPath) {
                "/api/v1/status" -> json("""{"version":"3.4.0"}""")
                "/api/v1/settings/public" -> json("""{"mediaServerType":2}""")
                "/api/v1/backdrops" -> json("[]")
                "/api/v1/auth/jellyfin/quickconnect/initiate" -> {
                    initiates.incrementAndGet()
                    json("""{"code":"123456","secret":"abcdef12"}""")
                }
                "/api/v1/auth/jellyfin/quickconnect/check" -> json("""{"authenticated":${approved.get()}}""")
                "/api/v1/auth/jellyfin/quickconnect/authenticate" ->
                    json("""{"id":7}""", headersOf("Set-Cookie", "connect.sid=qc; Path=/"))
                else -> json("{}")
            }
        }

    private fun scriptPendingQuickConnect() {
        seerr.dispatcher = { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/jellyfin/quickconnect/initiate" -> json("""{"code":"123456","secret":"abcdef12"}""")
                else -> json("""{"authenticated":false}""")
            }
        }
    }

    @Test
    fun `a server's plex and quick connect are offered beside its own sign-ins`() =
        runTest {
            val vm = viewModel()

            val signIn = vm.inspect("""{"version":"3.4.0"}""", """{"mediaServerType":2,"localLogin":true}""")

            assertTrue(SeerrSignInMode.QuickConnect in signIn.server.modes)
            assertTrue(SeerrSignInMode.Jellyfin in signIn.server.modes)
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
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2}"""))

            vm.connect()

            assertEquals("123456", vm.awaitLink().link?.code)
            assertEquals(SeerrVariant.Seerr, vm.awaitConnected().credentials.variant)
            assertEquals("/api/v1/auth/jellyfin/quickconnect/initiate", seerr.takeRequest().url.encodedPath)
        }

    @Test
    fun `the pending link is encrypted whole before it is written to the saved state`() =
        runTest {
            val saved = SavedStateHandle()
            val vm = viewModel(savedState = saved, cipher = ReversingCipher)
            vm.inspect("""{"version":"3.4.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.QuickConnect) }
            scriptPendingQuickConnect()

            vm.connect()
            vm.awaitLink()

            val stored = saved.get<String>("sharedPendingLink").orEmpty()
            assertFalse(stored.contains("abcdef12"))
            assertTrue(stored.contains("abcdef12".reversed()))
            assertNull(saved.get<String>("pendingLink"))

            vm.links.cancel()
        }

    @Test
    fun `a keystore that cannot encrypt costs the resume, not the sign-in`() =
        runTest {
            val saved = SavedStateHandle()
            val vm = viewModel(savedState = saved, cipher = FailingCipher)
            vm.inspect("""{"version":"3.4.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.QuickConnect) }
            repeat(3) { seerr.takeRequest() }
            seerr.enqueue(json("""{"code":"123456","secret":"abcdef12"}"""))
            seerr.enqueue(json("""{"authenticated":true}"""))
            seerr.enqueue(json("""{"id":7}""", headersOf("Set-Cookie", "connect.sid=qc; Path=/")))
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2}"""))

            vm.connect()

            vm.awaitConnected()
            assertNull(saved.get<String>("sharedPendingLink"))
        }

    @Test
    fun `an expired quick connect code is reported and the code taken down`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"3.4.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.QuickConnect) }
            repeat(3) { seerr.takeRequest() }
            seerr.enqueue(json("""{"code":"123456","secret":"abcdef12"}"""))
            seerr.enqueue(FakeResponse(code = 404))

            vm.connect()

            val failed = vm.connectUi.first { (it.state as? ConnectState.SignIn)?.error != null }
            assertEquals(ConnectError.LinkExpired, (failed.state as ConnectState.SignIn).error)
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
            seerr.enqueueProfile(json("""{"version":"1.33.2"}"""), json("""{"localLogin":true}"""))
            vm.editForm { copy(mode = SeerrSignInMode.Plex) }

            vm.connect()

            val link = requireNotNull(vm.awaitLink().link)
            assertEquals("ABCD", link.code)
            assertTrue(link.launchPending)
            assertTrue(requireNotNull(link.openUrl).startsWith("https://app.plex.tv/auth#?clientID=cid&code=ABCD"))
            vm.links.launched()
            assertEquals(SeerrVariant.Overseerr, vm.awaitConnected().credentials.variant)
            val login = seerr.takeRequest()
            assertEquals("/api/v1/auth/plex", login.url.encodedPath)
            assertTrue(login.body.contains("tok3n"))
        }

    @Test
    fun `the television's connect mints plex's short link pin, not the browser's long one`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"1.33.2"}""", """{"localLogin":true}""")
            repeat(3) { seerr.takeRequest() }
            plex.enqueue(json("""{"id":41,"code":"JKLM","expiresAt":"2099-01-01T00:00:00Z"}"""))
            plex.enqueue(json("""{"id":41,"code":"JKLM","authToken":"tok3n"}"""))
            seerr.enqueue(json("""{"id":9}""", headersOf("Set-Cookie", "connect.sid=plx; Path=/")))
            seerr.enqueueProfile(json("""{"version":"1.33.2"}"""), json("""{"localLogin":true}"""))
            vm.editForm { copy(mode = SeerrSignInMode.Plex) }

            vm.connect(forTelevision = true)

            val link = vm.awaitSignIn { it.link != null }.link as LinkFlow.Plex
            assertEquals("JKLM", link.code)
            val mint = plex.takeRequest()
            assertEquals("/api/v2/pins?strong=false", mint.url.encodedPath + "?" + mint.url.encodedQuery)
            assertEquals(SeerrVariant.Overseerr, vm.awaitConnected().credentials.variant)
        }

    @Test
    fun `a plex sign-in the process died during is picked up where the user left it`() =
        runTest {
            val token = AtomicReference<String?>(null)
            plex.dispatcher = { request ->
                if (request.method == "POST") {
                    json("""{"id":41,"code":"ABCD","expiresAt":"2099-01-01T00:00:00Z"}""")
                } else {
                    json("""{"id":41,"code":"ABCD","authToken":${token.get()?.let { "\"$it\"" } ?: "null"}}""")
                }
            }
            val saved = SavedStateHandle()
            val vm = viewModel(savedState = saved)
            vm.inspect("""{"version":"1.33.2"}""", """{"localLogin":true}""")
            repeat(3) { seerr.takeRequest() }
            vm.editForm { copy(mode = SeerrSignInMode.Plex) }
            vm.connect()
            vm.awaitLink()

            viewModels.clear()
            seerr.enqueueProfile(json("""{"version":"1.33.2"}"""), json("""{"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            seerr.enqueue(json("""{"id":9}""", headersOf("Set-Cookie", "connect.sid=plx; Path=/")))
            seerr.enqueueProfile(json("""{"version":"1.33.2"}"""), json("""{"localLogin":true}"""))
            val restored = viewModel(reuseConnection = true, savedState = saved)

            val link = requireNotNull(restored.awaitLink().link)
            assertEquals("ABCD", link.code)
            assertTrue(requireNotNull(link.openUrl).startsWith("https://app.plex.tv/auth#?clientID=cid&code=ABCD"))
            // The user has just come back to the app: the page is theirs to reopen, not reopened over them.
            assertFalse(link.launchPending)

            token.set("tok3n")
            assertEquals(SeerrVariant.Overseerr, restored.awaitConnected().credentials.variant)
        }

    @Test
    fun `a quick connect sign-in the process died during is picked up without a second initiate`() =
        runTest {
            val approved = AtomicBoolean(false)
            seerr.dispatcher = quickConnectServer(approved)
            val saved = SavedStateHandle()
            val vm = viewModel(savedState = saved)
            uiState(vm)
            vm.editAddress(seerr.url("/"))
            vm.inspect()
            vm.awaitSignIn()
            vm.editForm { copy(mode = SeerrSignInMode.QuickConnect) }
            vm.connect()
            assertEquals("123456", vm.awaitLink().link?.code)

            viewModels.clear()
            val restored = viewModel(reuseConnection = true, savedState = saved)

            assertEquals("123456", restored.awaitLink().link?.code)
            approved.set(true)
            assertEquals(SeerrVariant.Seerr, restored.awaitConnected().credentials.variant)
            assertEquals(1, initiates.get())
        }

    @Test
    fun `changing server takes the code down and forgets the pending link`() =
        runTest {
            val saved = SavedStateHandle()
            val vm = viewModel(savedState = saved)
            vm.inspect("""{"version":"3.4.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.QuickConnect) }
            scriptPendingQuickConnect()
            vm.connect()
            vm.awaitLink()
            assertNotNull(saved.get<String>("sharedPendingLink"))

            vm.changeServer()

            vm.connectUi.first { it.link == null }
            assertNull(saved.get<String>("sharedPendingLink"))
        }

    @Test
    fun `a refused password reset reaches the connect screen's state`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"3.4.0"}""", """{"mediaServerType":2,"localLogin":true}""")
            vm.editForm { copy(mode = SeerrSignInMode.Local, email = "ana@example.com") }
            repeat(3) { seerr.takeRequest() }
            seerr.enqueue(FakeResponse(code = 500))

            vm.requestPasswordReset()

            assertEquals(SetupError.Unknown, vm.connectUi.first { it.resetError != null }.resetError)
        }

    private fun json(
        body: String,
        headers: okhttp3.Headers = headersOf(),
    ): FakeResponse = FakeResponse(code = 200, headers = headers.newBuilder().add("Content-Type", "application/json").build(), body = body)

    private suspend fun uiState(vm: SharedSetupViewModel) {
        vm.uiState.first { it is SetupUiState.Address }
    }

    private object FailingCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = throw GeneralSecurityException("keymaster")

        override fun decrypt(ciphertext: String): String? = null
    }
}
