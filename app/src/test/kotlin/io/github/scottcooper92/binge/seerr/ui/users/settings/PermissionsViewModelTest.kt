package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.data.FakeUserStore
import io.github.scottcooper92.binge.seerr.data.UserEntity
import io.github.scottcooper92.binge.seerr.seerr.ManageablePermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val UNMANAGED_BIT = 1 shl 25

class PermissionsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()
    private val cache = FakeUserStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.serve("GET /api/v1/user/8/settings/permissions", """{"permissions":${REQUEST or UNMANAGED_BIT}}""")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): PermissionsViewModel {
        val vm = PermissionsViewModel(seerr.connection(this), cache, 8)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun PermissionsViewModel.awaitReady(): EditorUiState.Ready<PermissionSettings> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<PermissionSettings>

    @Test
    fun `the owner may flip everything, another admin everything but Admin, and a manager only what they hold`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            val owner = viewModel().awaitReady().draft
            assertEquals(setOf(ManageablePermission.Request), owner.selected)
            assertTrue(owner.locked.isEmpty())
            assertTrue(ManageablePermission.ManageBlocklist in owner.offered)

            seerr.viewer(id = 2, permissions = ADMIN)
            assertEquals(setOf(ManageablePermission.Admin), viewModel().awaitReady().draft.locked)

            seerr.viewer(id = 2, permissions = MANAGE_USERS or REQUEST, version = "1.33.0", settings = "{}")
            val manager = viewModel().awaitReady().draft
            assertTrue(ManageablePermission.Request4k !in manager.locked)
            assertTrue(ManageablePermission.ManageRequests in manager.locked)
            assertFalse(ManageablePermission.ManageBlocklist in manager.offered)
        }

    @Test
    fun `a toggle on a locked permission is ignored, and a save keeps the bits the editor does not manage`() =
        runTest {
            seerr.viewer(id = 2, permissions = ADMIN)
            cache.refresh("created", listOf(cachedUser(8)), nextSkip = null)
            val vm = viewModel()
            vm.awaitReady()

            vm.toggle(ManageablePermission.Admin)
            assertFalse(vm.awaitReady().dirty)

            vm.toggle(ManageablePermission.ManageIssues)
            val expected = REQUEST or UNMANAGED_BIT or ManageablePermission.ManageIssues.bit
            seerr.serve("POST /api/v1/user/8/settings/permissions", """{"permissions":$expected}""")
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())

            assertEquals("""{"permissions":$expected}""", seerr.body("POST", "/api/v1/user/8/settings/permissions"))
            assertEquals(expected, vm.awaitReady().saved.original)
            assertEquals(expected, cache.rows.single().permissions)
        }

    private fun cachedUser(id: Int) =
        UserEntity(
            listKey = "created",
            id = id,
            name = "User $id",
            email = null,
            handle = null,
            avatarUrl = null,
            origin = "Local",
            permissions = REQUEST,
            requestCount = 0,
            createdAtMillis = null,
            orderIndex = id,
        )
}
