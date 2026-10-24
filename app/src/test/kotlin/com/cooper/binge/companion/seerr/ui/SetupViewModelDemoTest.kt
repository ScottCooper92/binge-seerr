package com.cooper.binge.companion.seerr.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.PlexPinFlow
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.demo.DEMO_ORIGIN
import com.cooper.binge.companion.seerr.handoff.AddressHandOffs
import com.cooper.binge.companion.seerr.handoff.HandOffOpening
import com.cooper.binge.companion.seerr.seerr.PlexClientIdentity
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.ui.tv.tvActions
import com.cooper.binge.companion.seerr.util.MainDispatcherRule
import com.cooper.binge.companion.seerr.util.PlainCipher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Trying the demo from the address step (#1705): no server exists, so nothing here is scripted. */
class SetupViewModelDemoTest {
    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val viewModels = ViewModelStore()

    @After
    fun tearDown() = viewModels.clear()

    private lateinit var connection: SeerrConnection

    private fun TestScope.viewModel(): SetupViewModel {
        connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = backgroundScope) { folder.newFile("c.preferences_pb") },
                        PlainCipher,
                    ),
                apis = SeerrApiFactory(logRequests = false),
            )
        val vm =
            SetupViewModel(
                connection = connection,
                plex =
                    PlexPinFlow(
                        identity = { PlexClientIdentity(identifier = "cid", product = "Binge Seerr", version = "0.1.0", device = "Pixel") },
                        apis = { error("Plex is not used") },
                    ),
                savedState = SavedStateHandle(),
                cipher = PlainCipher,
                handOffs = AddressHandOffs { HandOffOpening.NoLocalNetwork },
                dispatcher = mainDispatcherRule.dispatcher,
            )
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `the address step offers the demo`() =
        runTest {
            val state = viewModel().uiState.first { it is SetupUiState.Address } as SetupUiState.Address

            assertTrue(state.canTryDemo)
        }

    @Test
    fun `trying the demo saves the demo connection and signs in`() =
        runTest {
            val vm = viewModel()
            vm.uiState.first { it is SetupUiState.Address }

            vm.tryDemo()
            val connected = vm.uiState.first { it is SetupUiState.Connected } as SetupUiState.Connected

            assertEquals(DEMO_ORIGIN, connected.credentials.baseUrl)
            assertEquals(DEMO_ORIGIN, connection.credentials.first()?.baseUrl)
            assertEquals(2, connection.authenticatedUser().permissions)
        }

    @Test
    fun `the television's actions run the demo`() =
        runTest {
            val vm = viewModel()
            vm.uiState.first { it is SetupUiState.Address }

            vm.tvActions().onTryDemo()
            val connected = vm.uiState.first { it is SetupUiState.Connected } as SetupUiState.Connected

            assertEquals(DEMO_ORIGIN, connected.credentials.baseUrl)
        }

    @Test
    fun `a state built without the offer does not show it`() {
        assertFalse(SetupUiState.Address("a", insecure = false, isInspecting = false, error = null).canTryDemo)
    }
}
