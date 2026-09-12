package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModelStore
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

class PasswordViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.serve("GET /api/v1/user/8/settings/password", """{"hasPassword":true}""")
        seerr.serve("POST /api/v1/user/8/settings/password", "")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.viewModel(): PasswordViewModel {
        val vm = PasswordViewModel(seerr.connection(this), 8)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        return vm
    }

    private suspend fun PasswordViewModel.awaitReady(): EditorUiState.Ready<PasswordSettings> =
        uiState.first { it is EditorUiState.Ready && !it.saving } as EditorUiState.Ready<PasswordSettings>

    @Test
    fun `a user changing their own password must give the current one, and the fields clear on success`() =
        runTest {
            seerr.viewer(id = 8, permissions = REQUEST)
            val vm = viewModel()
            assertTrue(vm.awaitReady().draft.currentRequired)

            vm.edit { it.copy(new = "longenough", confirm = "longenough") }
            vm.save()
            assertEquals(0, seerr.count("POST", "/api/v1/user/8/settings/password"))

            vm.edit { it.copy(current = "old") }
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())
            assertEquals(
                """{"currentPassword":"old","newPassword":"longenough","confirmPassword":"longenough"}""",
                seerr.body("POST", "/api/v1/user/8/settings/password"),
            )
            val after = vm.awaitReady().draft
            assertEquals("", after.new)
            assertTrue(after.hasPassword)
        }

    @Test
    fun `a manager sets another's password outright, and a mismatch or a short one never sends`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            seerr.serve("GET /api/v1/user/8/settings/password", """{"hasPassword":false}""")
            val vm = viewModel()
            assertFalse(vm.awaitReady().draft.currentRequired)

            vm.edit { it.copy(new = "short", confirm = "short") }
            vm.save()
            vm.edit { it.copy(new = "longenough", confirm = "different") }
            vm.save()
            assertEquals(0, seerr.count("POST", "/api/v1/user/8/settings/password"))

            vm.edit { it.copy(confirm = "longenough") }
            vm.save()
            assertEquals(EditorEvent.Saved, vm.events.first())
            assertEquals(
                """{"newPassword":"longenough","confirmPassword":"longenough"}""",
                seerr.body("POST", "/api/v1/user/8/settings/password"),
            )
        }
}
