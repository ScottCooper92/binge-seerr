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
import io.github.scottcooper92.binge.seerr.handoff.HandOffOpening
import io.github.scottcooper92.binge.seerr.seerr.PlexClientIdentity
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.plexTvApi
import io.github.scottcooper92.binge.seerr.util.FakeResponse
import io.github.scottcooper92.binge.seerr.util.FakeSeerrServer
import io.github.scottcooper92.binge.seerr.util.InMemoryDataStore
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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

            assertEquals(AddressHandOff.Listening(session.url), vm.awaitAddress { it.handOff != null }.handOff)
            seerr.enqueueProfile(json("""{"version":"3.4.0"}"""), json("""{"mediaServerType":2,"localLogin":true}"""))
            seerr.enqueue(json("[]"))
            session.address.complete(seerr.url("/"))

            val signIn = vm.uiState.first { it is SetupUiState.SignIn } as SetupUiState.SignIn
            assertEquals(seerr.url("/"), signIn.server.baseUrl)
            assertTrue(session.closed)
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

    private class FakeSession(
        override val url: String = "http://192.168.1.20:41234/a/aaaaaaaa",
    ) : AddressHandOffSession {
        val address = CompletableDeferred<String>()

        @Volatile
        var closed = false

        override suspend fun awaitAddress(): String = address.await()

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
