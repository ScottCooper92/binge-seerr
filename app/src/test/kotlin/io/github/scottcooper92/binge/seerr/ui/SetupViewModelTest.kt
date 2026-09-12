package io.github.scottcooper92.binge.seerr.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
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

/**
 * The setup screen's state over a real connection: DataStore in a temp file, HTTP into a scripted
 * server. Every outcome that crosses a thread — the first DataStore read, an HTTP answer, a write —
 * is awaited by its shape rather than read off the state, which would read whatever was there last.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = MockWebServer().apply { start() }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        seerr.close()
    }

    /** The state is shared WhileSubscribed, so a collector is kept open for the test's life. */
    private fun TestScope.viewModel(): SetupViewModel {
        val vm =
            SetupViewModel(
                SeerrConnection(
                    store =
                        CredentialStore(
                            PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c.preferences_pb") },
                            PlainCipher,
                        ),
                    apis = SeerrApiFactory(logRequests = false),
                ),
            )
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun SetupViewModel.awaitForm(match: (SetupForm) -> Boolean = { true }): SetupUiState.Disconnected =
        uiState.first { it is SetupUiState.Disconnected && match(it.form) } as SetupUiState.Disconnected

    private suspend fun SetupViewModel.awaitError(): SetupError? =
        (uiState.first { (it as? SetupUiState.Disconnected)?.error != null } as SetupUiState.Disconnected).error

    private suspend fun SetupViewModel.awaitConnected(): SetupUiState.Connected =
        uiState.first { it is SetupUiState.Connected } as SetupUiState.Connected

    @Test
    fun `the form cannot submit until it is filled in`() =
        runTest {
            val vm = viewModel()

            assertFalse(vm.awaitForm().form.canSubmit)
            vm.edit { copy(serverUrl = "seerr.local") }
            assertFalse(vm.awaitForm { it.serverUrl == "seerr.local" }.form.canSubmit)
            vm.edit { copy(apiKey = "k3y") }
            assertTrue(vm.awaitForm { it.apiKey == "k3y" }.form.canSubmit)
        }

    @Test
    fun `a successful connect shows the connection and drops the secret from the form`() =
        runTest {
            seerr.enqueue(json("""{"id":1,"permissions":2}"""))
            seerr.enqueue(json("""{"version":"3.0.0"}"""))
            val vm = viewModel()
            vm.awaitForm()
            vm.edit { copy(serverUrl = seerr.url("/").toString(), apiKey = "k3y") }

            vm.connect()

            assertEquals(SeerrVariant.Seerr, vm.awaitConnected().credentials.variant)

            vm.disconnect()

            assertEquals("", vm.awaitForm().form.apiKey)
        }

    @Test
    fun `a rejected key is reported as rejected, and editing clears it`() =
        runTest {
            seerr.enqueue(MockResponse(code = 401))
            val vm = viewModel()
            vm.awaitForm()
            vm.edit { copy(serverUrl = seerr.url("/").toString(), apiKey = "bad") }

            vm.connect()

            assertEquals(SetupError.Rejected, vm.awaitError())
            vm.edit { copy(apiKey = "better") }
            assertNull(vm.awaitForm { it.apiKey == "better" }.error)
        }

    @Test
    fun `a login sends the account and keeps the session`() =
        runTest {
            seerr.enqueue(json("""{"id":42}""", headersOf("Set-Cookie", "connect.sid=s; Path=/")))
            seerr.enqueue(json("""{"version":"2.0.0"}"""))
            val vm = viewModel()
            vm.awaitForm()
            vm.edit { copy(serverUrl = seerr.url("/").toString(), mode = AuthMode.Jellyfin, username = "scott", password = "pw") }

            vm.connect()

            assertEquals(SeerrVariant.Jellyseerr, vm.awaitConnected().credentials.variant)
            assertEquals("/api/v1/auth/jellyfin", seerr.takeRequest().url.encodedPath)
        }

    @Test
    fun `a malformed address never reaches the network`() =
        runTest {
            val vm = viewModel()
            vm.awaitForm()
            vm.edit { copy(serverUrl = "http://", apiKey = "k3y") }

            vm.connect()

            assertEquals(SetupError.InvalidUrl, vm.awaitError())
            assertEquals(0, seerr.requestCount)
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
