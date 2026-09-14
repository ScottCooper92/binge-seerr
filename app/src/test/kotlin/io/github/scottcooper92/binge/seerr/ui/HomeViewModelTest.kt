package io.github.scottcooper92.binge.seerr.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.ConnectionCarrier
import io.github.scottcooper92.binge.seerr.auth.ConnectionRestore
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Which screen the home lands on. The third state is the one that matters: nothing saved is not an
 * answer until a restore has been tried, or a transferred device is shown setup and asked to sign
 * in again for the second before the restore lands.
 */
private const val SETTLE_MILLIS = 500L

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val viewModels = ViewModelStore()

    @After
    fun tearDown() = viewModels.clear()

    private fun TestScope.store() =
        CredentialStore(
            dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("creds.preferences_pb") },
            cipher = ReversingCipher,
        )

    private fun TestScope.viewModel(
        store: CredentialStore,
        restore: ConnectionRestore,
    ): HomeViewModel {
        val connection = SeerrConnection(store, SeerrApiFactory(logRequests = false))
        val vm = HomeViewModel(connection, restore)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.isConnected.collect {} }
        return vm
    }

    private fun TestScope.restore(
        store: CredentialStore,
        carrier: ConnectionCarrier,
    ) = ConnectionRestore(store, SeerrApiFactory(logRequests = false), carrier, backgroundScope)

    @Test
    fun `nothing saved and no restore tried yet is not yet an answer`() =
        runTest {
            val store = store()

            val vm = viewModel(store, restore(store, EmptyCarrier))

            // The store has answered, so a home that keyed off it alone would be showing setup by
            // now. Waited for in real time because DataStore reads on a dispatcher of its own,
            // which a virtual clock cannot advance — and a test that checked too early would pass
            // against the very logic this pins.
            assertNull(store.credentials.first())
            assertNull(withContext(Dispatchers.Default) { withTimeoutOrNull(SETTLE_MILLIS) { vm.isConnected.first { it != null } } })
        }

    @Test
    fun `nothing saved once the restore has settled is the setup screen`() =
        runTest {
            val store = store()
            val restore = restore(store, EmptyCarrier)
            val vm = viewModel(store, restore)

            restore.run()

            assertEquals(false, withContext(Dispatchers.Default) { vm.isConnected.first { it != null } })
        }

    @Test
    fun `a saved connection is the hub, without waiting on a restore`() =
        runTest {
            val store = store()
            store.save(SeerrCredentials("https://seerr.example/", SeerrAuth.ApiKey("k3y")))

            val vm = viewModel(store, restore(store, EmptyCarrier))

            assertEquals(true, withContext(Dispatchers.Default) { vm.isConnected.first { it != null } })
        }

    /** A device with nothing transferred to it: the restore settles without reaching a server. */
    private object EmptyCarrier : ConnectionCarrier {
        override suspend fun put(credentials: SeerrCredentials) = Unit

        override suspend fun read(): SeerrCredentials? = null

        override suspend fun clear() = Unit
    }

    private object ReversingCipher : SecretCipher {
        override fun encrypt(plaintext: String): String = plaintext.reversed()

        override fun decrypt(ciphertext: String): String = ciphertext.reversed()
    }
}
