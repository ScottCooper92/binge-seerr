package com.cooper.binge.companion.seerr.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cooper.binge.companion.seerr.auth.LegacyCredentialStore
import com.cooper.binge.companion.seerr.auth.PlexPinFlow
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.demo.DEMO_ORIGIN
import com.cooper.binge.companion.seerr.handoff.AddressHandOffSession
import com.cooper.binge.companion.seerr.handoff.AddressHandOffs
import com.cooper.binge.companion.seerr.handoff.HAND_OFF_SESSION_MODE
import com.cooper.binge.companion.seerr.handoff.HandOffCredentials
import com.cooper.binge.companion.seerr.handoff.HandOffOpening
import com.cooper.binge.companion.seerr.handoff.HandOffProgress
import com.cooper.binge.companion.seerr.seerr.PlexClientIdentity
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.SetupViewModelHandOffTest.FakeSession
import com.cooper.binge.companion.seerr.util.FakeResponse
import com.cooper.binge.companion.seerr.util.FakeSeerrServer
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.PlainCipher
import com.cooper.binge.companion.seerr.util.RecordingAnalytics
import com.cooper.homelab.core.server.network.DataStoreCleartextConsent
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.core.testing.InMemoryDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl
import okhttp3.Interceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * Setup on the shared connect machine, behind the shared-setup flag: the same two steps as [SetupViewModelTest], read through the
 * screens' own [SetupUiState]. The machine's own rules (the plain-HTTP opt-in, the port fallback, re-entrancy) are the shared
 * library's tests; what is here is what this app adds to them: its probe, its sign-ins, its errors and the television's hand-off.
 */
class SharedSetupViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()
    private val viewModels = ViewModelStore()
    private val analytics = RecordingAnalytics()
    private val cleartext = DataStoreCleartextConsent(InMemoryDataStore()) { null }
    private var stores = 0
    private lateinit var connection: SeerrConnection

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.awaitIdle()
    }

    private fun TestScope.viewModel(
        scripted: Boolean = true,
        unreachable: (HttpUrl) -> Boolean = { false },
        localNetwork: LocalNetworkPermission = LocalNetworkPermission.AlwaysGranted,
        handOffs: AddressHandOffs = AddressHandOffs { HandOffOpening.NoLocalNetwork },
    ): SharedSetupViewModel {
        connection =
            SeerrConnection(
                store =
                    LegacyCredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c${stores++}.preferences_pb") },
                        PlainCipher,
                        UnconfinedTestDispatcher(testScheduler),
                    ),
                apis =
                    if (scripted) {
                        SeerrApiFactory(
                            logRequests = false,
                            testTransport = { cookies ->
                                val scripted = seerr.interceptor(cookies)
                                Interceptor { chain ->
                                    if (unreachable(chain.request().url)) throw IOException("unreachable")
                                    scripted.intercept(chain)
                                }
                            },
                            testDispatcher = seerr::newInlineDispatcher,
                        )
                    } else {
                        SeerrApiFactory(logRequests = false)
                    },
                cleartext = cleartext,
            )
        val vm =
            SharedSetupViewModel(
                connection = connection,
                plex =
                    PlexPinFlow(
                        identity = { PlexClientIdentity("cid", "Seerr Companion", "0.1.0", "Pixel") },
                        apis = { error("Plex is not used") },
                    ),
                savedState = SavedStateHandle(),
                cipher = PlainCipher,
                handOffs = handOffs,
                cleartext = cleartext,
                localNetwork = localNetwork,
                dispatcher = mainDispatcherRule.dispatcher,
                analytics = analytics,
            )
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun SharedSetupViewModel.awaitAddress(match: (SetupUiState.Address) -> Boolean = { true }): SetupUiState.Address =
        uiState.first { it is SetupUiState.Address && match(it) } as SetupUiState.Address

    private suspend fun SharedSetupViewModel.awaitSignIn(match: (SetupUiState.SignIn) -> Boolean = { true }): SetupUiState.SignIn =
        uiState.first { it is SetupUiState.SignIn && match(it) } as SetupUiState.SignIn

    private suspend fun SharedSetupViewModel.awaitConnected(): SetupUiState.Connected =
        uiState.first { it is SetupUiState.Connected } as SetupUiState.Connected

    private suspend fun SharedSetupViewModel.awaitNotSetUp(): SetupUiState.NotSetUp =
        uiState.first { it is SetupUiState.NotSetUp } as SetupUiState.NotSetUp

    /** Scripts the three unauthenticated calls the address step makes, then reads the server. */
    private suspend fun SharedSetupViewModel.read(
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
    fun `the address step reads the server and offers the sign-ins it accepts`() =
        runTest {
            val vm = viewModel()

            val signIn =
                vm.read(
                    status = """{"version":"3.4.0"}""",
                    settings = """{"mediaServerType":2,"localLogin":true,"jellyfinServerName":"Home"}""",
                    backdrops = """["/a.jpg"]""",
                )

            assertEquals(
                listOf(SeerrSignInMode.Jellyfin, SeerrSignInMode.QuickConnect, SeerrSignInMode.Local, SeerrSignInMode.ApiKey),
                signIn.server.modes,
            )
            assertEquals("Seerr", signIn.server.title)
            assertEquals(SeerrVariant.Seerr, signIn.server.variant)
            assertEquals("Home", signIn.server.mediaServerName)
            assertEquals("https://image.tmdb.org/t/p/w1280/a.jpg", signIn.server.backdropUrl)
            assertEquals(SeerrSignInMode.Jellyfin, signIn.form.mode)
            assertFalse(signIn.form.canSubmit)
        }

    @Test
    fun `plex is offered on a plex-backed server`() =
        runTest {
            val signIn = viewModel().read("""{"version":"2.7.0"}""", """{"mediaServerType":1}""")

            assertEquals(listOf(SeerrSignInMode.Plex, SeerrSignInMode.Local, SeerrSignInMode.ApiKey), signIn.server.modes)
        }

    @Test
    fun `an address that is not a Seerr server is said so, and typing clears it`() =
        runTest {
            seerr.enqueue(FakeResponse(code = 404))
            seerr.enqueue(FakeResponse(code = 404))
            val vm = viewModel()
            vm.awaitAddress()
            vm.editAddress(seerr.url("/"))

            vm.inspect()

            assertEquals(SetupError.NotSeerr, vm.awaitAddress { it.error != null }.error)
            vm.editAddress("other")
            assertNull(vm.awaitAddress { it.serverUrl == "other" }.error)
        }

    @Test
    fun `a portless address is tried at the default Seerr port next`() =
        runTest {
            val vm = viewModel(unreachable = { it.port != 5055 })
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            vm.awaitAddress()
            vm.editAddress("http://192.168.1.10")

            vm.inspect()

            assertEquals("http://192.168.1.10:5055/", vm.awaitSignIn().server.baseUrl)
        }

    @Test
    fun `an address a phone sent that cannot be reached says so for an address off the home network`() =
        runTest {
            val session = FakeSession()
            val vm = viewModel(unreachable = { true }) { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }

            session.address.complete("https://203.0.113.9")
            vm.awaitAddress { it.awaitingConfirm }
            vm.inspect()

            assertEquals(SetupError.UnreachableNotLocal, vm.awaitAddress { it.error != null }.error)
            vm.editAddress("https://203.0.113.9")
            assertEquals(
                SetupError.Unreachable,
                run {
                    vm.inspect()
                    vm.awaitAddress { it.error != null }.error
                },
            )
        }

    @Test
    fun `a malformed address never reaches the network`() =
        runTest {
            val vm = viewModel()
            vm.awaitAddress()
            vm.editAddress("not a url")

            vm.inspect()

            assertEquals(SetupError.InvalidUrl, vm.awaitAddress { it.error != null }.error)
            assertEquals(0, seerr.requestCount)
        }

    @Test
    fun `a server that is not set up gets a message, and changing server goes back to the address`() =
        runTest {
            val vm = viewModel()
            seerr.enqueueProfile(json("""{"version":"3.5.1"}"""), json("""{"initialized":false,"mediaServerType":null}"""))
            vm.awaitAddress()
            vm.editAddress(seerr.url("/"))
            vm.inspect()

            val notSetUp = vm.awaitNotSetUp()

            assertFalse(notSetUp.server.setUp)
            assertNull(notSetUp.error)
            vm.changeServer()
            vm.awaitAddress()
        }

    @Test
    fun `a key connect saves the connection and drops the key from the form`() =
        runTest {
            val vm = viewModel()
            vm.read("""{"version":"3.0.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.ApiKey, apiKey = " k3y ") }
            seerr.enqueue(json("""{"id":1,"permissions":2}"""))
            seerr.enqueueProfile(json("""{"version":"3.0.0"}"""), json("""{"mediaServerType":2}"""))

            vm.connect()

            assertEquals(SeerrVariant.Seerr, vm.awaitConnected().credentials.variant)
            assertEquals(listOf("sign_in" to mapOf("method" to "ApiKey", "success" to true)), analytics.events)

            connection.disconnect()

            val back = vm.awaitSignIn()
            assertEquals("", back.form.apiKey)
        }

    @Test
    fun `a rejected key is reported as rejected and kept for the retry, and editing clears the error`() =
        runTest {
            val vm = viewModel()
            vm.read("""{"version":"3.0.0"}""", """{"mediaServerType":2}""")
            vm.editForm { copy(mode = SeerrSignInMode.ApiKey, apiKey = "bad") }
            seerr.enqueue(FakeResponse(code = 401))

            vm.connect()

            val rejected = vm.awaitSignIn { it.error != null }
            assertEquals(SetupError.Rejected, rejected.error)
            assertEquals("bad", rejected.form.apiKey)
            assertEquals(listOf("sign_in" to mapOf("method" to "ApiKey", "success" to false)), analytics.events)
            vm.editForm { copy(apiKey = "better") }
            assertNull(vm.awaitSignIn { it.form.apiKey == "better" }.error)
        }

    @Test
    fun `a local login sends the email and keeps the session`() =
        runTest {
            val vm = viewModel()
            vm.read("""{"version":"2.0.0"}""", """{"mediaServerType":2,"localLogin":true}""")
            vm.editForm { copy(mode = SeerrSignInMode.Local, email = " s@example.com ", password = "pw") }
            repeat(3) { seerr.takeRequest() }
            seerr.enqueue(json("""{"id":42}""", headersOf("Set-Cookie", "connect.sid=s; Path=/")))
            seerr.enqueueProfile(json("""{"version":"2.0.0"}"""), json("""{"mediaServerType":2}"""))

            vm.connect()

            assertEquals(SeerrVariant.Jellyseerr, vm.awaitConnected().credentials.variant)
            assertEquals("/api/v1/auth/local", seerr.takeRequest().url.encodedPath)
        }

    @Test
    fun `trying the demo connects and reports one sign_in as the demo`() =
        runTest {
            val vm = viewModel(scripted = false)
            vm.awaitAddress()

            vm.tryDemo()
            val connected = vm.awaitConnected()

            assertEquals(DEMO_ORIGIN, connected.credentials.baseUrl)
            val signIn = mapOf("method" to "Demo", "success" to true)
            assertEquals(listOf("sign_in" to signIn), analytics.events.filter { it.first == "sign_in" })
        }

    @Test
    fun `an address a phone sends waits for the TV's user, and the page says so`() =
        runTest {
            val session = FakeSession()
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }

            session.address.complete(seerr.url("/"))

            val waiting = vm.awaitAddress { it.serverUrl == seerr.url("/") }
            assertTrue(waiting.awaitingConfirm)
            assertTrue(waiting.received)
            assertFalse(waiting.canTryDemo)
            assertEquals(HandOffProgress.ConfirmOnTv, session.progress())
            assertEquals(0, seerr.requestCount)

            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            vm.inspect()

            assertTrue(vm.awaitSignIn().server.setUp)
            assertTrue(session.progress() is HandOffProgress.SignIn)
        }

    @Test
    fun `typing over a phone's address ends the phone's hold on the field`() =
        runTest {
            val session = FakeSession()
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }
            session.address.complete(seerr.url("/"))
            vm.awaitAddress { it.received }

            vm.editAddress("typed")

            val typed = vm.awaitAddress { it.serverUrl == "typed" }
            assertFalse(typed.received)
            assertTrue(session.closed)
        }

    @Test
    fun `credentials a phone sent sign the TV in as if typed, and the page hears how it went`() =
        runTest {
            val session = FakeSession()
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            session.address.complete(seerr.url("/"))
            vm.awaitAddress { it.awaitingConfirm }
            vm.inspect()
            vm.awaitSignIn()

            seerr.enqueue(FakeResponse(code = 401, headers = headersOf("Content-Type", "application/json"), body = """{"message":"no"}"""))
            session.sendCredentials(HandOffCredentials(mode = "Local", email = "ana@example.com", password = "wrong"))

            val refused = vm.awaitSignIn { it.error != null }
            assertEquals("ana@example.com", refused.form.email)
            assertEquals(SetupError.Rejected, refused.error)
            val progress = session.progress() as HandOffProgress.SignIn
            assertTrue(progress.failed)
            assertEquals(1, progress.attempt)
        }

    @Test
    fun `a phone's session is kept while the TV asks for the local network, and adopted once it is granted`() =
        runTest {
            val session = FakeSession().apply { handedSession = "ph0n3" }
            var granted = false
            val vm = viewModel(unreachable = { !granted }, localNetwork = { granted }) { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }

            session.address.complete("http://192.168.1.20:5055")
            assertEquals(SetupError.LocalNetworkDenied, vm.awaitAddress { it.error != null }.error)
            // The prompt is the TV's user's to answer and the session waits for it: the phone must not be told the hand-off failed,
            // or it stops following a TV that goes on to sign in.
            assertFalse(session.progress() is HandOffProgress.Failed)
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            seerr.enqueue(json("""{"id":9,"permissions":2}"""))
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))

            granted = true
            vm.localNetworkResult()
            val connected = vm.awaitConnected()

            assertEquals(SeerrAuth.Session(cookie = "ph0n3", userId = 9, shared = true), connected.credentials.auth)
            assertEquals(HandOffProgress.Connected, session.progress())
        }

    @Test
    fun `a handed session is dropped when the server is not set up, so a later read cannot adopt it`() =
        runTest {
            val session = FakeSession().apply { handedSession = "ph0n3" }
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"initialized":false}"""))

            session.address.complete(seerr.url("/"))
            vm.awaitNotSetUp()
            assertEquals("the phone is told the server is not set up, not that it failed", HandOffProgress.NotSetUp, session.progress())

            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"initialized":true,"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            vm.inspect()
            val signIn = vm.awaitSignIn()

            assertNull("no stale session was tried against the set-up server", signIn.error)
            assertTrue(seerr.requests.none { it.url.encodedPath.endsWith("/auth/me") })
            assertEquals(
                "the one attempt the phone was told about, not one per check",
                1,
                (session.progress() as HandOffProgress.SignIn).attempt,
            )
        }

    @Test
    fun `a declined permission does not hide a later refusal from the phone once the session is gone`() =
        runTest {
            val session = FakeSession().apply { handedSession = "ph0n3" }
            var granted = false
            val vm = viewModel(unreachable = { !granted }, localNetwork = { granted }) { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }
            session.address.complete("http://192.168.1.20:5055")
            vm.awaitAddress { it.error == SetupError.LocalNetworkDenied }
            assertFalse(session.progress() is HandOffProgress.Failed)

            granted = true
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            vm.editAddress(seerr.url("/"))
            vm.inspect()
            vm.awaitSignIn()
            seerr.enqueue(FakeResponse(code = 401, headers = headersOf("Content-Type", "application/json"), body = """{"message":"no"}"""))
            session.sendCredentials(HandOffCredentials(mode = "Local", email = "ana@example.com", password = "wrong"))
            vm.awaitSignIn { it.error != null }

            val progress = session.progress() as HandOffProgress.SignIn
            assertTrue("the session is gone, so the refusal is the phone's to hear", progress.failed)
        }

    @Test
    fun `a session sent from the sign-in step after a refused attempt is adopted and counted once`() =
        runTest {
            val session = FakeSession()
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            session.address.complete(seerr.url("/"))
            vm.awaitAddress { it.awaitingConfirm }
            vm.inspect()
            vm.awaitSignIn()
            seerr.enqueue(FakeResponse(code = 401, headers = headersOf("Content-Type", "application/json"), body = """{"message":"no"}"""))
            session.sendCredentials(HandOffCredentials(mode = "Local", email = "ana@example.com", password = "wrong"))
            vm.awaitSignIn { it.error != null }

            // The form holds an error from the refused attempt. A session sent now reads the server once, and takes the session once.
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            seerr.enqueue(FakeResponse(code = 401, headers = headersOf("Content-Type", "application/json"), body = """{"message":"no"}"""))
            session.sendCredentials(HandOffCredentials(mode = HAND_OFF_SESSION_MODE, session = "st4l3"))

            val refused = vm.awaitSignIn { it.error == SetupError.HandOffSessionRejected }
            assertEquals(SetupError.HandOffSessionRejected, refused.error)
            assertEquals(
                "the refused typed attempt, then the session, and no more",
                2,
                (session.progress() as HandOffProgress.SignIn).attempt,
            )
        }

    @Test
    fun `changing server drops a handed session that was still waiting`() =
        runTest {
            val session = FakeSession().apply { handedSession = "ph0n3" }
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"initialized":false}"""))
            session.address.complete(seerr.url("/"))
            vm.awaitNotSetUp()

            vm.changeServer()
            vm.awaitAddress()
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"initialized":true,"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            vm.editAddress(seerr.url("/"))
            vm.inspect()

            assertNull(vm.awaitSignIn().error)
            assertTrue(seerr.requests.none { it.url.encodedPath.endsWith("/auth/me") })
        }

    @Test
    fun `an address that comes with the phone's session signs the TV straight in, without its form`() =
        runTest {
            val session = FakeSession().apply { handedSession = "ph0n3" }
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            seerr.enqueue(json("""{"id":9,"permissions":2}"""))
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            val states = mutableListOf<SetupUiState>()
            backgroundScope.launch { vm.uiState.collect { states += it } }

            session.address.complete(seerr.url("/"))
            val connected = vm.awaitConnected()

            assertEquals(SeerrAuth.Session(cookie = "ph0n3", userId = 9, shared = true), connected.credentials.auth)
            assertTrue("the form never shows on the way", states.none { it is SetupUiState.SignIn })
        }

    @Test
    fun `the listener outlives the setup once the TV is signed in, so the phone can still read Connected`() =
        runTest {
            val session = FakeSession().apply { handedSession = "ph0n3" }
            val handOffs = RecordingHandOffs(session)
            val vm = viewModel(handOffs = handOffs)
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            seerr.enqueue(json("""{"id":9,"permissions":2}"""))
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            session.address.complete(seerr.url("/"))
            vm.awaitConnected()

            // The home replaces the setup the moment the session is saved, which clears this view model before the phone has polled.
            viewModels.clear()
            advanceUntilIdle()

            assertSame("handed on to outlive the screen", session, handOffs.lingered)
            assertFalse("not closed under the phone", session.closed)
        }

    @Test
    fun `a setup left before the TV signed in closes the listener at once`() =
        runTest {
            val session = FakeSession()
            val handOffs = RecordingHandOffs(session)
            val vm = viewModel(handOffs = handOffs)
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }

            viewModels.clear()
            advanceUntilIdle()

            assertNull(handOffs.lingered)
            assertTrue(session.closed)
        }

    @Test
    fun `a handed session the server refuses leaves the form up saying so, and the phone hears it failed`() =
        runTest {
            val session = FakeSession().apply { handedSession = "st4l3" }
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            seerr.enqueue(FakeResponse(code = 401, headers = headersOf("Content-Type", "application/json"), body = """{"message":"no"}"""))

            session.address.complete(seerr.url("/"))
            val refused = vm.awaitSignIn { it.error != null }

            assertEquals(SetupError.HandOffSessionRejected, refused.error)
            val progress = session.progress() as HandOffProgress.SignIn
            assertTrue(progress.failed)
            assertEquals(1, progress.attempt)
            vm.editForm { copy(apiKey = "typed") }
            assertNull(vm.awaitSignIn { it.form.apiKey == "typed" }.error)
        }

    private fun json(
        body: String,
        headers: okhttp3.Headers = headersOf(),
    ): FakeResponse = FakeResponse(code = 200, headers = headers.newBuilder().add("Content-Type", "application/json").build(), body = body)

    /** An opener that records the listener handed on to outlive a screen, instead of serving it. */
    private class RecordingHandOffs(
        private val session: AddressHandOffSession,
    ) : AddressHandOffs {
        @Volatile
        var lingered: AddressHandOffSession? = null

        override fun open(): HandOffOpening = HandOffOpening.Opened(session)

        override fun linger(
            session: AddressHandOffSession,
            within: kotlin.time.Duration,
        ) {
            lingered = session
        }
    }
}
