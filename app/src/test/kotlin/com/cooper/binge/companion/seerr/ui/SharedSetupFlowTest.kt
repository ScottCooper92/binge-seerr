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
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.plexTvApi
import com.cooper.binge.companion.seerr.util.FakeRequest
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.PlainCipher
import com.cooper.homelab.core.server.network.DataStoreCleartextConsent
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.core.testing.InMemoryDataStore
import com.cooper.homelab.core.testing.RecordingAnalytics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import okhttp3.Interceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The setup screen's two steps over an in-memory connection: DataStore in a temp file, HTTP into a
 * scripted Seerr and, for the Plex flow, a scripted plex.tv. Every outcome that crosses a thread is
 * awaited by its shape rather than read off the state, which would read whatever was there last.
 */
class SharedSetupFlowTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()
    private val plex = FakeSeerrServer()

    /** Every ViewModel goes in here and is cleared on teardown, so no link poll outlives its test. */
    private val viewModels = ViewModelStore()

    /** Counts the Quick Connect initiates, so a resume can be told from a second sign-in started. */
    private val initiates = AtomicInteger(0)

    private val analytics = RecordingAnalytics()

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.awaitIdle()
        plex.awaitIdle()
    }

    private lateinit var connection: SeerrConnection

    /** Flipped to make the server unanswering, as a network the app is refused or a server that is off looks. */
    private val serverDown = AtomicBoolean(false)

    private fun transport(cookies: okhttp3.CookieJar): Interceptor {
        val scripted = seerr.interceptor(cookies)
        return Interceptor { chain ->
            if (serverDown.get()) throw IOException("unreachable")
            scripted.intercept(chain)
        }
    }

    /** Real consent over an in-memory store, with nothing saved to grandfather in. */
    private val cleartext = DataStoreCleartextConsent(InMemoryDataStore()) { null }

    /** The state is shared WhileSubscribed, so a collector is kept open for the test's life. */
    private fun TestScope.viewModel(
        reuseConnection: Boolean = false,
        savedState: SavedStateHandle = SavedStateHandle(),
        cipher: SecretCipher = PlainCipher,
        seen: MutableList<SetupUiState>? = null,
        handOffs: AddressHandOffs = AddressHandOffs { HandOffOpening.NoLocalNetwork },
        localNetwork: LocalNetworkPermission = LocalNetworkPermission.AlwaysGranted,
    ): SharedSetupViewModel {
        if (!reuseConnection) {
            connection =
                SeerrConnection(
                    store =
                        LegacyCredentialStore(
                            PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c.preferences_pb") },
                            PlainCipher,
                            UnconfinedTestDispatcher(testScheduler),
                        ),
                    apis = SeerrApiFactory(logRequests = false, testTransport = ::transport, testDispatcher = seerr::newDispatcher),
                    quickConnectPollInterval = 10.milliseconds,
                    cleartext = cleartext,
                )
        }
        val vm =
            SharedSetupViewModel(
                connection = connection,
                plex =
                    PlexPinFlow(
                        identity = {
                            PlexClientIdentity(
                                identifier = "cid",
                                product = "Seerr Companion",
                                version = "0.1.0",
                                device = "Pixel",
                            )
                        },
                        apis = {
                            plexTvApi(
                                it,
                                plex.url("/"),
                                testTransport = plex.interceptor(),
                                testDispatcher = plex.newDispatcher(),
                            )
                        },
                        pollInterval = 10.milliseconds,
                    ),
                savedState = savedState,
                cipher = cipher,
                handOffs = handOffs,
                cleartext = cleartext,
                dispatcher = mainDispatcherRule.dispatcher,
                analytics = analytics,
                localNetwork = localNetwork,
            )
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect { state -> seen?.add(state) } }
        return vm
    }

    private suspend fun SharedSetupViewModel.awaitAddress(match: (SetupUiState.Address) -> Boolean = { true }): SetupUiState.Address =
        uiState.first { it is SetupUiState.Address && match(it) } as SetupUiState.Address

    private suspend fun SharedSetupViewModel.awaitSignIn(match: (SetupUiState.SignIn) -> Boolean = { true }): SetupUiState.SignIn =
        uiState.first { it is SetupUiState.SignIn && match(it) } as SetupUiState.SignIn

    /** [awaitSignIn] without suspending, so the test scheduler cannot advance virtual time while it waits. */
    private fun SharedSetupViewModel.blockUntilSignIn(match: (SetupUiState.SignIn) -> Boolean): SetupUiState.SignIn {
        val deadline = System.nanoTime() + 5.seconds.inWholeNanoseconds
        while (System.nanoTime() < deadline) {
            (uiState.value as? SetupUiState.SignIn)?.takeIf(match)?.let { return it }
            Thread.sleep(5)
        }
        throw AssertionError("sign-in state never matched; last was ${uiState.value}")
    }

    /**
     * Waits for [count] analytics events without suspending. The view model saves the credentials, which shows as Connected,
     * and only then reports the sign-in, so a test that read the events on seeing Connected could read none.
     */
    private fun blockUntilEvents(count: Int) {
        val deadline = System.nanoTime() + 5.seconds.inWholeNanoseconds
        while (analytics.events.size < count && System.nanoTime() < deadline) Thread.sleep(5)
    }

    private suspend fun SharedSetupViewModel.awaitConnected(): SetupUiState.Connected =
        uiState.first { it is SetupUiState.Connected } as SetupUiState.Connected

    /** Scripts the three unauthenticated calls the address step makes, then reads the server. */
    private suspend fun SharedSetupViewModel.inspect(
        status: String,
        settings: String,
        backdrops: String = "[]",
    ): SetupUiState.SignIn {
        seerr.enqueueProfile(json(status), json(settings))
        seerr.enqueue(json(backdrops))
        awaitAddress()
        editAddress(seerr.url("/"))
        inspect()
        return awaitSignIn()
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
    fun `a local address asks for the local network permission while it is refused, and a public one never does`() =
        runTest {
            var granted = false
            val vm = viewModel(localNetwork = { granted })
            vm.awaitAddress()

            vm.editAddress("192.168.1.10:5055")
            assertTrue(vm.awaitAddress { it.serverUrl == "192.168.1.10:5055" }.needsLocalNetwork)

            vm.editAddress("https://seerr.example.com")
            assertFalse(vm.awaitAddress { it.serverUrl.endsWith("example.com") }.needsLocalNetwork)

            vm.editAddress("seerr.lan")
            assertTrue(vm.awaitAddress { it.serverUrl == "seerr.lan" }.needsLocalNetwork)
            granted = true
            vm.localNetworkResult()
            assertFalse(vm.awaitAddress { !it.needsLocalNetwork }.needsLocalNetwork)
        }

    @Test
    fun `an unreachable local server is the permission when it is refused, and unreachable when it is not`() =
        runTest {
            serverDown.set(true)
            var granted = false
            val vm = viewModel(localNetwork = { granted })
            vm.awaitAddress()
            vm.editAddress(seerr.url("/"))

            vm.inspect()
            assertEquals(SetupError.LocalNetworkDenied, vm.awaitAddress { it.error != null }.error)

            // The grant reads the server again by itself, and now it is simply down.
            granted = true
            vm.localNetworkResult()
            assertEquals(SetupError.Unreachable, vm.awaitAddress { it.error != null }.error)
        }

    /** Allowing access and continuing are one tap: the grant reads the server without a second press (#1099). */
    @Test
    fun `a grant while the address waits on the local network reads the server at once`() =
        runTest {
            var granted = false
            val vm = viewModel(localNetwork = { granted })
            vm.awaitAddress()
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            vm.editAddress(seerr.url("/"))
            assertTrue(vm.awaitAddress { it.serverUrl == seerr.url("/") }.needsLocalNetwork)

            granted = true
            vm.localNetworkResult()

            vm.awaitSignIn()
        }

    @Test
    fun `a refusal leaves the address waiting, and reads nothing`() =
        runTest {
            val vm = viewModel(localNetwork = { false })
            vm.awaitAddress()
            vm.editAddress(seerr.url("/"))

            vm.localNetworkResult()

            val address = vm.awaitAddress { it.serverUrl == seerr.url("/") }
            assertTrue(address.needsLocalNetwork)
            assertFalse(address.isInspecting)
            assertEquals(0, seerr.requestCount)
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
    fun `a public http address is not read until the user opts in, and the opt-in is held against that host`() =
        runTest {
            val vm = viewModel()
            vm.awaitAddress()
            vm.editAddress("http://seerr.example.com:8080/")

            vm.inspect()

            val refused = vm.awaitAddress { it.insecure }
            assertFalse(refused.canContinue)
            assertFalse(refused.isInspecting)
            assertEquals(0, seerr.requestCount)

            vm.allowCleartext(true)
            assertTrue(vm.awaitAddress { it.cleartextAllowed }.canContinue)

            vm.editAddress("http://other.example.com:8080/")
            assertFalse(vm.awaitAddress { it.serverUrl.contains("other") }.cleartextAllowed)
            assertFalse(cleartext.allows("seerr.example.com"))
        }

    @Test
    fun `an opted-in public host is read, kept for the saved server, and dropped on disconnect`() =
        runTest {
            val vm = viewModel()
            vm.awaitAddress()
            seerr.enqueueProfile(json("""{"version":"3.0.0"}"""), json("""{"mediaServerType":2}"""))
            seerr.enqueue(json("[]"))
            vm.editAddress("http://seerr.example.com:8080/")
            vm.allowCleartext(true)

            vm.inspect()
            vm.awaitSignIn()
            assertTrue(cleartext.allows("seerr.example.com"))

            vm.editForm { copy(mode = SeerrSignInMode.ApiKey, apiKey = "k3y") }
            seerr.enqueue(json("""{"id":1,"permissions":2}"""))
            seerr.enqueueProfile(json("""{"version":"3.0.0"}"""), json("""{"mediaServerType":2}"""))
            vm.connect()
            vm.awaitConnected()
            assertTrue(cleartext.allows("seerr.example.com"))

            connection.disconnect()
            assertFalse(cleartext.allows("seerr.example.com"))
        }

    @Test
    fun `editing a connection already opted in to plain http keeps its tick`() =
        runTest {
            cleartext.grant("seerr.example.com")
            val vm = viewModel()
            vm.awaitAddress()
            seerr.enqueue(json("""{"id":1,"permissions":2}"""))
            seerr.enqueueProfile(json("""{"version":"3.0.0"}"""), json("""{"mediaServerType":2}"""))
            connection.connect("http://seerr.example.com:8080/", SeerrAuth.ApiKey("k3y")).getOrThrow()
            vm.awaitConnected()
            seerr.enqueueProfile(json("""{"version":"3.0.0"}"""), json("""{"mediaServerType":2}"""))
            seerr.enqueue(json("[]"))

            vm.beginEdit()

            assertEquals("http://seerr.example.com:8080/", vm.awaitSignIn().server.baseUrl)
        }

    /** The app put the form up itself because the server rejected the session (#810), so the form says why. */
    @Test
    fun `a sign-in again for a rejected session lands on the saved server's sign-in, with the reason`() =
        runTest {
            val vm = viewModel()
            vm.awaitAddress()
            seerr.enqueue(json("""{"id":1,"permissions":2}"""))
            seerr.enqueueProfile(json("""{"version":"3.0.0"}"""), json("""{"mediaServerType":2}"""))
            connection.connect("http://seerr.lan:5055/", SeerrAuth.ApiKey("k3y")).getOrThrow()
            vm.awaitConnected()
            seerr.enqueueProfile(json("""{"version":"3.0.0"}"""), json("""{"mediaServerType":2}"""))
            seerr.enqueue(json("[]"))

            vm.beginEdit(notice = SetupNotice.SessionRejected)

            val signIn = vm.awaitSignIn()
            assertEquals("http://seerr.lan:5055/", signIn.server.baseUrl)
            assertEquals(SetupNotice.SessionRejected, signIn.notice)
        }

    /** Someone signing out of a shared television means to sign in again, so setup opens on that server's sign-in. */
    @Test
    fun `after a sign-out setup opens on the server it left, at its sign-in`() =
        runTest {
            val vm = viewModel()
            vm.awaitAddress()
            seerr.enqueue(json("""{"id":1,"permissions":2}"""))
            seerr.enqueueProfile(json("""{"version":"3.0.0"}"""), json("""{"mediaServerType":2}"""))
            val saved = connection.connect(seerr.url("/").toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
            vm.awaitConnected()
            connection.signOut()
            seerr.enqueueProfile(json("""{"version":"3.0.0"}"""), json("""{"mediaServerType":2}"""))
            seerr.enqueue(json("[]"))

            val signIn = viewModel(reuseConnection = true).awaitSignIn()

            assertEquals(saved.baseUrl, signIn.server.baseUrl)
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
            assertTrue(reset.body.contains("s@example.com"))
            assertEquals(listOf("password_reset_requested" to mapOf("success" to true)), analytics.events)
        }

    /** Each request voids the last link, so a second press within the minute would break the email on its way. */
    @Test
    fun `forgot password waits a minute before it will send another link`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"1.33.2"}""", """{"localLogin":true,"emailEnabled":true}""")
            vm.editForm { copy(mode = SeerrSignInMode.Local, email = "s@example.com") }
            repeat(3) { seerr.takeRequest() }
            seerr.enqueue(json("""{"status":"ok"}"""))
            seerr.enqueue(json("""{"status":"ok"}"""))

            vm.requestPasswordReset()
            // Blocked rather than suspended, here and up to the end of the minute: a test suspended on real I/O lets the
            // scheduler skip virtual time, and the cooldown's delay, armed from OkHttp's thread, can be skipped before
            // the test sees it began.
            assertTrue(vm.blockUntilSignIn { it.notice != null }.resetCoolingDown)
            vm.requestPasswordReset()
            advanceTimeBy(RESET_COOLDOWN - 1.seconds)
            assertTrue(vm.awaitSignIn().resetCoolingDown)
            assertEquals(1, analytics.events.size)

            advanceTimeBy(2.seconds)
            vm.awaitSignIn { !it.resetCoolingDown }
            vm.requestPasswordReset()
            vm.awaitSignIn { analytics.events.size == 2 }
            repeat(2) { assertEquals("/api/v1/auth/reset-password", seerr.takeRequest().url.encodedPath) }
        }

    @Test
    fun `a resume that cannot reach the server says so on the address, and reports a failed sign-in for the mode it was resuming`() =
        runTest {
            val approved = AtomicBoolean(false)
            seerr.dispatcher = quickConnectServer(approved)
            val saved = SavedStateHandle()
            val vm = viewModel(savedState = saved)
            vm.awaitAddress()
            vm.editAddress(seerr.url("/"))
            vm.inspect()
            vm.awaitSignIn()
            vm.editForm { copy(mode = SeerrSignInMode.QuickConnect) }
            vm.connect()
            vm.awaitSignIn { it.link != null }

            // The process dies mid-approval, and the server cannot be reached once it comes back. The resume counts as a failed
            // sign-in with the mode it was resuming, as the old setup counted it.
            viewModels.clear()
            seerr.dispatcher = null

            val restored = viewModel(reuseConnection = true, savedState = saved)

            assertEquals(SetupError.NotSeerr, restored.awaitAddress { it.error != null }.error)
            blockUntilEvents(1)
            assertEquals(listOf("sign_in_failed" to mapOf("method" to "QuickConnect", "reason" to "failed")), analytics.events)
        }

    @Test
    fun `a resume of an edit-connection link stays on the form rather than leaving for the hub`() =
        runTest {
            val approved = AtomicBoolean(false)
            seerr.dispatcher = quickConnectServer(approved)
            // The first sign-in keeps a saved state of its own, as the setup screen's entry does in the
            // app. `Connected` shows as soon as the credentials are saved, and its link is forgotten
            // only after that. Sharing `saved` with it let a slow runner remove the edit's link after
            // the edit had stored it, and the resume below then waited for a link that was gone (#705).
            val vm = viewModel()
            vm.awaitAddress()
            vm.editAddress(seerr.url("/"))
            vm.inspect()
            vm.awaitSignIn()
            vm.editForm { copy(mode = SeerrSignInMode.QuickConnect) }
            vm.connect()
            approved.set(true)
            vm.awaitConnected()

            // Editing the live connection, then the process dies mid-approval.
            approved.set(false)
            val saved = SavedStateHandle()
            val editing = viewModel(reuseConnection = true, savedState = saved)
            editing.beginEdit()
            editing.awaitSignIn()
            editing.editForm { copy(mode = SeerrSignInMode.QuickConnect) }
            editing.connect()
            editing.awaitSignIn { it.link != null }
            viewModels.clear()

            // Credentials are saved, so a resume that restored `editing` any later than it does
            // would read them as connected and drop the user on the hub, taking the link with it.
            val seen = mutableListOf<SetupUiState>()
            val restored = viewModel(reuseConnection = true, savedState = saved, seen = seen)
            assertEquals(LinkFlow.QuickConnect("123456"), restored.awaitSignIn { it.link != null }.link)
            assertTrue(seen.none { it is SetupUiState.Connected })
        }

    /** Quick Connect end to end, by path: initiate, the poll [approved] answers, then the session. */
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

    /**
     * A Quick Connect initiate plus a check that never approves, scripted by path rather than a
     * fixed queue depth. The poll loop's `delay` shares the test's own virtual clock, so any number
     * of extra iterations can run before a test reads state or cancels — with a fixed-size queue,
     * one running dry mid-test drains into an unscripted 404, which `SeerrConnection` reads as the
     * code having expired and forgets the pending link out from under the assertion (#378). Scripted
     * by path, an extra poll iteration is just another "not yet" rather than a queue underrun.
     */
    private fun scriptPendingQuickConnect(
        code: String = "123456",
        secret: String = "abcdef12",
    ) {
        seerr.dispatcher = { request ->
            when (request.url.encodedPath) {
                "/api/v1/auth/jellyfin/quickconnect/initiate" -> json("""{"code":"$code","secret":"$secret"}""")
                else -> json("""{"authenticated":false}""")
            }
        }
    }

    @Test
    fun `a cancelled link is forgotten, so the next start does not resume into it`() =
        runTest {
            val saved = SavedStateHandle()
            val vm = viewModel(savedState = saved)
            vm.inspect("""{"version":"3.4.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.QuickConnect) }
            scriptPendingQuickConnect()
            vm.connect()
            vm.awaitSignIn { it.link != null }

            vm.links.cancel()
            viewModels.clear()

            val restarted = viewModel(reuseConnection = true, savedState = saved)
            assertEquals("", restarted.awaitAddress().serverUrl)
        }

    @Test
    fun `cancelling a link stops the wait and shows no error`() =
        runTest {
            val vm = viewModel()
            vm.inspect("""{"version":"3.4.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.QuickConnect) }
            scriptPendingQuickConnect()
            vm.connect()
            vm.awaitSignIn { it.link != null }

            vm.links.cancel()

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
            seerr.enqueueProfile(json("""{"version":"3.0.0"}"""), json("""{"mediaServerType":2}"""))
            vm.connect()
            val original = vm.awaitConnected().credentials
            repeat(6) { seerr.takeRequest() }

            // Edit connection opens on its own ViewModel over the same connection, as the entry does.
            val editor = viewModel(reuseConnection = true)
            seerr.enqueueProfile(json("""{"version":"3.0.0"}"""), json("""{"mediaServerType":2}"""))
            seerr.enqueue(json("[]"))
            editor.beginEdit()
            val editing = editor.awaitSignIn { !it.isConnecting }
            assertEquals(original.baseUrl, editing.server.baseUrl)

            seerr.enqueue(FakeResponse(code = 401))
            editor.editForm { copy(mode = SeerrSignInMode.ApiKey, apiKey = "wrong") }
            editor.connect()
            assertEquals(SetupError.Rejected, editor.awaitSignIn { it.error != null }.error)
            assertEquals(original, connection.credentials.first())

            seerr.enqueue(json("""{"id":1,"permissions":2}"""))
            seerr.enqueueProfile(json("""{"version":"3.0.0"}"""), json("""{"mediaServerType":2}"""))
            editor.editForm { copy(apiKey = "n3w") }
            editor.connect()
            assertEquals(SeerrAuth.ApiKey("n3w"), editor.awaitConnected().credentials.auth)
        }

    private suspend fun SharedSetupViewModel.awaitNotSetUp(match: (SetupUiState.NotSetUp) -> Boolean = { true }): SetupUiState.NotSetUp =
        uiState.first { it is SetupUiState.NotSetUp && match(it) } as SetupUiState.NotSetUp

    @Test
    fun `a server that says it is set up offers its sign-in as before`() =
        runTest {
            val signIn =
                viewModel().inspect(status = """{"version":"3.5.1"}""", settings = """{"initialized":true,"mediaServerType":2}""")
            assertTrue(signIn.server.setUp)
        }

    @Test
    fun `a server that says nothing about it, as an older one does, is taken to be set up`() =
        runTest {
            val signIn = viewModel().inspect(status = """{"version":"3.4.0"}""", settings = """{"mediaServerType":2}""")
            assertTrue(signIn.server.setUp)
        }

    @Test
    fun `checking again reads the server once more, and the sign-in appears once it is set up`() =
        runTest {
            val vm = viewModel()
            seerr.enqueueProfile(json("""{"version":"3.5.1"}"""), json("""{"initialized":false}"""))
            vm.awaitAddress()
            vm.editAddress(seerr.url("/"))
            vm.inspect()
            vm.awaitNotSetUp()

            // Still not set up: the same message, read again.
            seerr.enqueueProfile(json("""{"version":"3.5.1"}"""), json("""{"initialized":false}"""))
            vm.inspect()
            // Settled: read twice by now and no longer checking.
            vm.awaitNotSetUp { !it.isChecking && seerr.requests.count { r -> r.url.encodedPath == "/api/v1/settings/public" } == 2 }

            // The administrator finishes setup in the web page.
            seerr.enqueueProfile(json("""{"version":"3.5.1"}"""), json("""{"initialized":true,"mediaServerType":2}"""))
            seerr.enqueue(json("[]"))
            vm.inspect()
            assertTrue(vm.awaitSignIn().server.setUp)
        }

    private fun json(
        body: String,
        headers: okhttp3.Headers = headersOf(),
    ): FakeResponse = FakeResponse(code = 200, headers = headers.newBuilder().add("Content-Type", "application/json").build(), body = body)

    /** A Keystore that will not encrypt, as a flaky vendor keymaster is. */
    private object FailingCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = throw GeneralSecurityException("keymaster")

        override fun decrypt(ciphertext: String): String? = null
    }
}
