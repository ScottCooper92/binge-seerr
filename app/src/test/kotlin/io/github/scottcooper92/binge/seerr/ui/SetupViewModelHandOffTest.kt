package io.github.scottcooper92.binge.seerr.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.DataStoreCleartextConsent
import io.github.scottcooper92.binge.seerr.auth.PlexPinFlow
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.handoff.AddressHandOffSession
import io.github.scottcooper92.binge.seerr.handoff.AddressHandOffs
import io.github.scottcooper92.binge.seerr.handoff.HAND_OFF_SESSION_MODE
import io.github.scottcooper92.binge.seerr.handoff.HandOffCredentials
import io.github.scottcooper92.binge.seerr.handoff.HandOffOpening
import io.github.scottcooper92.binge.seerr.handoff.HandOffProgress
import io.github.scottcooper92.binge.seerr.seerr.PlexClientIdentity
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.plexTvApi
import io.github.scottcooper92.binge.seerr.util.FakeResponse
import io.github.scottcooper92.binge.seerr.util.FakeSeerrServer
import io.github.scottcooper92.binge.seerr.util.InMemoryDataStore
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.Headers.Companion.headersOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

/**
 * The television's "send the address from your phone" (#323), as the setup ViewModel drives it: an
 * address a phone sends goes exactly where a typed one does, and the listener is closed on every way
 * out. The listener itself is faked here; `AddressHandOffListenerTest` covers it over real sockets.
 */
class SetupViewModelHandOffTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seerr = FakeSeerrServer()
    private val viewModels = ViewModelStore()

    /** Each ViewModel gets a store file of its own: DataStore refuses two on one file. */
    private var stores = 0
    private val cleartext = DataStoreCleartextConsent(InMemoryDataStore()) { null }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.awaitIdle()
    }

    private fun TestScope.viewModel(handOffs: AddressHandOffs): SetupViewModel {
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c${stores++}.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false, testTransport = seerr::interceptor, testDispatcher = seerr::newDispatcher),
                quickConnectPollInterval = 10.milliseconds,
                cleartext = cleartext,
            )
        val vm =
            SetupViewModel(
                connection = connection,
                plex =
                    PlexPinFlow(
                        identity = { PlexClientIdentity(identifier = "cid", product = "Binge Seerr", version = "0.1.0", device = "TV") },
                        apis = { plexTvApi(it, "http://plex.invalid/") },
                        pollInterval = 10.milliseconds,
                    ),
                savedState = SavedStateHandle(),
                cipher = PlainCipher,
                handOffs = handOffs,
                dispatcher = mainDispatcherRule.dispatcher,
            )
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun SetupViewModel.awaitAddress(match: (SetupUiState.Address) -> Boolean = { true }): SetupUiState.Address =
        uiState.first { it is SetupUiState.Address && match(it) } as SetupUiState.Address

    @Test
    fun `the plate carries the listener's url, and an address a phone sends is read as if typed`() =
        runTest {
            val session = FakeSession()
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()

            vm.showHandOff(true)

            assertEquals(
                AddressHandOff.Listening(session.url, session.scanUrl, session.pin),
                vm.awaitAddress { it.handOff != null }.handOff,
            )
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            session.address.complete(seerr.url("/"))

            val signIn = vm.uiState.first { it is SetupUiState.SignIn } as SetupUiState.SignIn
            assertEquals(seerr.url("/"), signIn.server.baseUrl)
        }

    /** #907: a phone's plain-HTTP public address waits for the opt-in on the TV, and agreeing goes straight on to read it. */
    @Test
    fun `a public plain-HTTP address from a phone waits for the opt-in, and agreeing reads the server`() =
        runTest {
            val session = FakeSession()
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }

            session.address.complete("http://seerr.example.com:5055")

            val waiting = vm.awaitAddress { it.serverUrl == "http://seerr.example.com:5055" }
            assertTrue(waiting.awaitingCleartextConsent)
            assertEquals(0, seerr.requestCount)

            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            vm.allowCleartext(true)

            val signIn = vm.uiState.first { it is SetupUiState.SignIn } as SetupUiState.SignIn
            assertEquals("http://seerr.example.com:5055/", signIn.server.baseUrl)
        }

    @Test
    fun `the listener outlives the address so the page can follow the sign-in, and goes when nobody finishes it`() =
        runTest {
            val session = FakeSession()
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))

            session.address.complete(seerr.url("/"))
            val signIn = vm.uiState.first { it is SetupUiState.SignIn } as SetupUiState.SignIn

            // The plate going when the address arrived is the page moving on: it must not close the listener.
            vm.showHandOff(false)
            assertFalse(session.closed)
            val progress = session.progress() as HandOffProgress.SignIn
            assertEquals(signIn.server.title, progress.server)
            assertTrue("Local" in progress.modes)

            // Nobody signs in, so the virtual clock runs out the sign-in timeout and the listener is let go.
            withTimeout(HAND_OFF_SIGN_IN_TIMEOUT * 2) { while (!session.closed) delay(1_000) }
            assertTrue(session.closed)
        }

    @Test
    fun `asking again after a phone-sent address closes the old listener and shows a new code`() =
        runTest {
            val sessions = mutableListOf<FakeSession>()
            val vm =
                viewModel {
                    HandOffOpening.Opened(FakeSession(url = "http://192.168.1.20:41234/a/code${sessions.size}").also(sessions::add))
                }
            vm.awaitAddress()
            vm.showHandOff(true)
            val first = vm.awaitAddress { it.handOff != null }.handOff
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            sessions[0].address.complete(seerr.url("/"))
            vm.uiState.first { it is SetupUiState.SignIn && !it.isConnecting }

            // Back from the sign-in: the follow loop is still up, and the button must still work.
            vm.changeServer()
            vm.awaitAddress()
            vm.showHandOff(true)

            val second = vm.awaitAddress { it.handOff != null && it.handOff != first }.handOff
            assertTrue(second is AddressHandOff.Listening)
            assertTrue(sessions[0].closed)
            assertFalse(sessions.last().closed)

            // A code nobody uses is replaced for ever, and runTest drains the virtual clock on the way out.
            vm.showHandOff(false)
            assertTrue(sessions.last().closed)
        }

    @Test
    fun `asking again after typing over a phone-sent address that failed shows a new code`() =
        runTest {
            val sessions = mutableListOf<FakeSession>()
            val vm =
                viewModel {
                    HandOffOpening.Opened(FakeSession(url = "http://192.168.1.20:41234/a/code${sessions.size}").also(sessions::add))
                }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }
            sessions[0].address.complete("http://")
            assertEquals(SetupError.InvalidUrl, vm.awaitAddress { it.error != null && it.handOff == null }.error)

            // The user takes over at the TV: the phone's follow phase ends, and the button works again.
            vm.editAddress("seerr")
            assertTrue(sessions[0].closed)
            vm.showHandOff(true)

            assertTrue(vm.awaitAddress { it.handOff != null }.handOff is AddressHandOff.Listening)
            assertFalse(sessions.last().closed)

            vm.showHandOff(false)
            assertTrue(sessions.last().closed)
        }

    @Test
    fun `a public http address from a phone still waits for the user's opt-in`() =
        runTest {
            val session = FakeSession()
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }

            session.address.complete("http://seerr.example.com:5055/")

            val address = vm.awaitAddress { it.serverUrl == "http://seerr.example.com:5055/" && it.handOff == null }
            assertTrue(address.insecure)
            assertFalse(address.canContinue)
            assertFalse(address.isInspecting)
            assertEquals(0, seerr.requestCount)
            assertFalse(cleartext.allows("seerr.example.com"))
        }

    @Test
    fun `a code nobody uses is replaced by a fresh one and the old listener closes`() =
        runTest {
            val sessions = mutableListOf<FakeSession>()
            val vm =
                viewModel {
                    HandOffOpening.Opened(FakeSession(url = "http://192.168.1.20:41234/a/code${sessions.size}").also(sessions::add))
                }
            vm.awaitAddress()

            vm.showHandOff(true)

            // Nothing completes the address, so the virtual clock runs on to the timeout, and then to the next one.
            val first = vm.awaitAddress { it.handOff != null }.handOff
            val second = vm.awaitAddress { it.handOff != null && it.handOff != first }.handOff
            assertTrue(sessions[0].closed)
            assertFalse(sessions.last().closed)
            assertTrue(second is AddressHandOff.Listening)
            assertNull(vm.awaitAddress().error)

            // Left running, a code that renews itself would keep the virtual clock turning for ever.
            vm.showHandOff(false)
            assertTrue(sessions.last().closed)
        }

    @Test
    fun `cancelling takes the plate down and closes the listener at once`() =
        runTest {
            val session = FakeSession()
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }

            vm.showHandOff(false)

            val address = vm.awaitAddress { it.handOff == null }
            assertNull(address.error)
            assertTrue(session.closed)
        }

    @Test
    fun `a television with no local network says why, and opens nothing`() =
        runTest {
            val vm = viewModel { HandOffOpening.NoLocalNetwork }
            vm.awaitAddress()

            vm.showHandOff(true)

            assertEquals(
                AddressHandOff.Unavailable(AddressHandOff.Reason.NoLocalNetwork),
                vm.awaitAddress { it.handOff != null }.handOff,
            )
            vm.showHandOff(false)
            assertNull(vm.awaitAddress { it.handOff == null }.handOff)
        }

    @Test
    fun `a listener that cannot open, or fails while open, reads as could not listen`() =
        runTest {
            val unopened = viewModel { throw IOException("bind") }
            unopened.awaitAddress()
            unopened.showHandOff(true)
            assertEquals(
                AddressHandOff.Unavailable(AddressHandOff.Reason.CouldNotListen),
                unopened.awaitAddress { it.handOff != null }.handOff,
            )

            val session = FakeSession()
            val failing = viewModel { HandOffOpening.Opened(session) }
            failing.awaitAddress()
            failing.showHandOff(true)
            failing.awaitAddress { it.handOff is AddressHandOff.Listening }
            session.address.completeExceptionally(IOException("network gone"))
            assertEquals(
                AddressHandOff.Unavailable(AddressHandOff.Reason.CouldNotListen),
                failing.awaitAddress { it.handOff is AddressHandOff.Unavailable }.handOff,
            )
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
            vm.uiState.first { it is SetupUiState.SignIn }
            val progress = session.progress() as HandOffProgress.SignIn
            assertEquals(listOf("Local"), progress.modes.filter { it == "Local" })

            // A refused sign-in: the page is told, and the form is the phone's to fill again.
            seerr.enqueue(FakeResponse(code = 401, headers = headersOf("Content-Type", "application/json"), body = """{"message":"no"}"""))
            session.sendCredentials(HandOffCredentials(mode = "Local", email = "ana@example.com", password = "wrong"))
            val refused = vm.uiState.first { it is SetupUiState.SignIn && it.error != null } as SetupUiState.SignIn
            assertEquals("ana@example.com", refused.form.email)
            assertTrue((session.progress() as HandOffProgress.SignIn).failed)
        }

    @Test
    fun `an address that comes with the phone's session signs the TV straight in, without its sign-in form`() =
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
            val connected = vm.uiState.first { it is SetupUiState.Connected } as SetupUiState.Connected

            assertEquals(SeerrAuth.Session(cookie = "ph0n3", userId = 9, shared = true), connected.credentials.auth)
            assertTrue("the form never shows on the way", states.none { it is SetupUiState.SignIn })
        }

    @Test
    fun `a handed session the server refuses leaves the sign-in form up, saying so, and the phone hears it failed`() =
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
            val refused = vm.uiState.first { it is SetupUiState.SignIn && it.error != null } as SetupUiState.SignIn

            assertEquals(SetupError.HandOffSessionRejected, refused.error)
            val progress = session.progress() as HandOffProgress.SignIn
            assertTrue(progress.failed)
            assertEquals(1, progress.attempt)
        }

    @Test
    fun `a session sent from the sign-in step is counted even when the server cannot be read`() =
        runTest {
            val session = FakeSession()
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            session.address.complete(seerr.url("/"))
            vm.uiState.first { it is SetupUiState.SignIn }

            // The server goes quiet: the phone told the TV would count this as attempt 1, and it must.
            seerr.enqueue(
                FakeResponse(code = 500, headers = headersOf("Content-Type", "application/json"), body = """{"message":"down"}"""),
            )
            session.sendCredentials(HandOffCredentials(mode = HAND_OFF_SESSION_MODE, session = "ph0n3"))
            vm.uiState.first { it is SetupUiState.SignIn && it.error != null }

            val progress = session.progress() as HandOffProgress.SignIn
            assertTrue(progress.failed)
            assertEquals(1, progress.attempt)
        }

    @Test
    fun `credentials for a mode the server does not offer, or before there is a server, are ignored`() =
        runTest {
            val session = FakeSession()
            val vm = viewModel { HandOffOpening.Opened(session) }
            vm.awaitAddress()
            vm.showHandOff(true)
            vm.awaitAddress { it.handOff != null }

            session.sendCredentials(HandOffCredentials(mode = "Local", email = "ana@example.com", password = "x"))
            assertTrue(vm.uiState.value is SetupUiState.Address)

            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            session.address.complete(seerr.url("/"))
            val signIn = vm.uiState.first { it is SetupUiState.SignIn } as SetupUiState.SignIn
            val untouched = signIn.form

            session.sendCredentials(HandOffCredentials(mode = "Emby", username = "ana", password = "x"))
            session.sendCredentials(HandOffCredentials(mode = "Plex"))
            session.sendCredentials(HandOffCredentials(mode = "NotAMode"))

            assertEquals(untouched, (vm.uiState.value as SetupUiState.SignIn).form)
        }

    private class FakeSession(
        override val url: String = "http://192.168.1.20:41234/a/aaaaaaaa",
    ) : AddressHandOffSession {
        override val scanUrl: String get() = "$url#k=${"A".repeat(43)}"

        override val pin: String = "4821"

        val address = CompletableDeferred<String>()

        /** The session a phone sent with the address (#772), opened as the listener would. */
        @Volatile
        var handedSession: String? = null

        /** Where a test hands the VM credentials, the way the listener would after opening them. */
        @Volatile
        var sendCredentials: (HandOffCredentials) -> Unit = {}

        /** What the VM told the page when it last asked, for a test to read. */
        @Volatile
        var progress: () -> HandOffProgress = { HandOffProgress.Waiting }

        @Volatile
        var closed = false

        override suspend fun serve(
            progress: () -> HandOffProgress,
            onAddress: (address: String, session: String?) -> Unit,
            onCredentials: (HandOffCredentials) -> Unit,
        ) {
            this.progress = progress
            sendCredentials = onCredentials
            onAddress(address.await(), handedSession)
            awaitCancellation()
        }

        override fun close() {
            closed = true
        }
    }

    private fun json(body: String): FakeResponse =
        FakeResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
