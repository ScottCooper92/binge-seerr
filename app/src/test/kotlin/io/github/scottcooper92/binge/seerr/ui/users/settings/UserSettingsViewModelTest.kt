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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The index follows the web client's menu rules for who sees which page. */
class UserSettingsViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        seerr.start()
        seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","permissions":$REQUEST,"userType":3}""")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.pages(userId: Int = 8): List<UserSettingsPage> {
        val vm = UserSettingsViewModel(seerr.connection(this), userId)
        viewModels.put(vm.hashCode().toString(), vm)
        backgroundScope.launch { vm.uiState.collect {} }
        val ready = vm.uiState.first { it is UserSettingsUiState.Ready } as UserSettingsUiState.Ready
        assertEquals("Ana", ready.index.userName)
        return ready.index.pages
    }

    @Test
    fun `the owner gets every page for another user but their linked accounts`() =
        runTest {
            seerr.viewer(id = 1, permissions = ADMIN)
            assertEquals(
                listOf(UserSettingsPage.General, UserSettingsPage.Password, UserSettingsPage.Notifications, UserSettingsPage.Permissions),
                pages(),
            )
        }

    @Test
    fun `a user gets their own pages, with linked accounts on a server that has them and never their own permissions`() =
        runTest {
            seerr.viewer(id = 8, permissions = REQUEST)
            assertEquals(
                listOf(
                    UserSettingsPage.General,
                    UserSettingsPage.Password,
                    UserSettingsPage.Notifications,
                    UserSettingsPage.LinkedAccounts,
                ),
                pages(),
            )

            seerr.viewer(id = 8, permissions = MANAGE_USERS, version = "1.33.0", settings = """{"localLogin":false}""")
            assertEquals(listOf(UserSettingsPage.General, UserSettingsPage.Notifications), pages())
        }

    @Test
    fun `a manager cannot set the password of an admin they are not, and a plain user gets nothing for another`() =
        runTest {
            seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","permissions":$ADMIN,"userType":3}""")
            seerr.viewer(id = 2, permissions = MANAGE_USERS)
            assertEquals(listOf(UserSettingsPage.General, UserSettingsPage.Notifications, UserSettingsPage.Permissions), pages())

            seerr.viewer(id = 3, permissions = REQUEST)
            assertEquals(emptyList<UserSettingsPage>(), pages())
        }
}
