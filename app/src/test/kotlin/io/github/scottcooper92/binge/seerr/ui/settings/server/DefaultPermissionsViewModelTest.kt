package io.github.scottcooper92.binge.seerr.ui.settings.server

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import io.github.scottcooper92.binge.seerr.ui.users.settings.ADMIN
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorEvent
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorUiState
import io.github.scottcooper92.binge.seerr.ui.users.settings.PermissionSettings
import io.github.scottcooper92.binge.seerr.ui.users.settings.ScriptedSeerr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultPermissionsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.viewer(id = 1, permissions = ADMIN)
        seerr.serve("GET /api/v1/settings/main", """{"applicationTitle":"Home","defaultPermissions":32}""")
        seerr.serve("POST /api/v1/settings/main", """{"applicationTitle":"Home","defaultPermissions":48}""")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): DefaultPermissionsViewModel {
        val vm = DefaultPermissionsViewModel(seerr.connection(this))
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun DefaultPermissionsViewModel.awaitReady(): EditorUiState.Ready<PermissionSettings> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<PermissionSettings>

    @Test
    fun `the defaults decode from the main settings, with the blocklist toggles for this lineage`() =
        runTest {
            val draft = viewModel().awaitReady().draft

            assertEquals(setOf(ManageablePermission.Request), draft.selected)
            assertEquals(32, draft.original)
            assertTrue(ManageablePermission.ManageBlocklist in draft.offered)
            assertTrue(draft.locked.isEmpty())
        }

    @Test
    fun `saving sends only the default permission bits, and adopts the server's`() =
        runTest {
            val vm = viewModel()
            vm.awaitReady()

            vm.toggle(ManageablePermission.ManageRequests)
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())

            val sent = Json.parseToJsonElement(seerr.body("POST", "/api/v1/settings/main")).jsonObject
            assertEquals("48", sent.getValue("defaultPermissions").jsonPrimitive.content)
            assertNull(sent["applicationTitle"])
            assertEquals(48, vm.awaitReady().saved.original)
        }
}
