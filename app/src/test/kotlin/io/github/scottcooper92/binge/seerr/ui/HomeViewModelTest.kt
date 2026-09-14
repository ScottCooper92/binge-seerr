package io.github.scottcooper92.binge.seerr.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.auth.ConnectionRestore
import io.github.scottcooper92.binge.seerr.auth.CredentialStore
import io.github.scottcooper92.binge.seerr.auth.NoConnectionCarrier
import io.github.scottcooper92.binge.seerr.auth.SecretCipher
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.seerr.SeerrApiFactory
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The home's three-state answer: null until a restore has settled, then whether a server is
 * saved. `connection` and `restore` are real, over a temp-file [CredentialStore], so the
 * combine is exercised the same way the screen sees it rather than against a stub flow.
 */
class HomeViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val viewModels = ViewModelStore()

    @After
    fun tearDown() = viewModels.clear()

    private fun TestScope.store(): CredentialStore =
        CredentialStore(
            dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("home.preferences_pb") },
            cipher = PlainCipher,
        )

    private fun TestScope.restore(credentialStore: CredentialStore): ConnectionRestore =
        ConnectionRestore(credentialStore, SeerrApiFactory(logRequests = false), NoConnectionCarrier, backgroundScope)

    private fun TestScope.homeViewModel(
        credentialStore: CredentialStore,
        restore: ConnectionRestore = restore(credentialStore),
    ): HomeViewModel {
        val connection = SeerrConnection(store = credentialStore, apis = SeerrApiFactory(logRequests = false))
        val vm = HomeViewModel(connection, restore)
        viewModels.put("home", vm)
        backgroundScope.launch { vm.isConnected.collect {} }
        return vm
    }

    @Test
    fun `nothing saved and no restore tried yet answers null`() =
        runTest {
            val credentialStore = store()
            val vm = homeViewModel(credentialStore)

            // Forces the store's real (DataStore-backed) flow to have emitted before asserting,
            // so this pins the combine's answer rather than just its stateIn seed value.
            assertNull(credentialStore.credentials.first())
            assertNull(vm.isConnected.value)
        }

    @Test
    fun `nothing saved once the restore has settled answers false`() =
        runTest {
            val credentialStore = store()
            val restore = restore(credentialStore)
            val vm = homeViewModel(credentialStore, restore)

            restore.run()

            assertEquals(false, vm.isConnected.first { it != null })
        }

    @Test
    fun `a saved connection answers true, settled or not`() =
        runTest {
            val credentialStore = store()
            credentialStore.save(SeerrCredentials("https://saved.example/", SeerrAuth.ApiKey("k3y")))
            val vm = homeViewModel(credentialStore)

            assertEquals(true, vm.isConnected.first { it != null })
        }

    private object PlainCipher : SecretCipher {
        override fun encrypt(plaintext: String) = plaintext

        override fun decrypt(ciphertext: String): String = ciphertext
    }
}
