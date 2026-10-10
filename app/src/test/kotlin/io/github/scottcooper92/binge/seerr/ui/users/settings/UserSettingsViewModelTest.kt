package io.github.scottcooper92.binge.seerr.ui.users.settings

import androidx.lifecycle.ViewModelStore
import io.github.scottcooper92.binge.seerr.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The index follows the web client's menu rules for who sees which page. */
class UserSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val seerr = ScriptedSeerr(folder)
    private val viewModels = ViewModelStore()

    @Before
    fun setUp() {
        seerr.start()
        seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","permissions":$REQUEST,"userType":3}""")
    }

    @After
    fun tearDown() {
        viewModels.clear()
        seerr.close()
    }

    private suspend fun TestScope.pages(userId: Int = 8): List<UserSettingsPage> {
        val vm = UserSettingsViewModel(seerr.connection(this), mainDispatcherRule.dispatcher, userId)
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
                    UserSettingsPage.LinkedAccounts,
                    UserSettingsPage.Notifications,
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

    @Test
    fun `an admin who is not the owner is not offered another admin's password, which the server would refuse`() =
        runTest {
            seerr.serve("GET /api/v1/user/8", """{"id":8,"displayName":"Ana","permissions":$ADMIN,"userType":3}""")
            seerr.viewer(id = 2, permissions = ADMIN)
            assertEquals(listOf(UserSettingsPage.General, UserSettingsPage.Notifications, UserSettingsPage.Permissions), pages())

            seerr.viewer(id = 1, permissions = ADMIN)
            assertEquals(
                listOf(UserSettingsPage.General, UserSettingsPage.Password, UserSettingsPage.Notifications, UserSettingsPage.Permissions),
                pages(),
            )
        }

    @Test
    fun `an admin who is not the owner may set a plain user's password`() =
        runTest {
            seerr.viewer(id = 2, permissions = ADMIN)
            assertEquals(
                listOf(UserSettingsPage.General, UserSettingsPage.Password, UserSettingsPage.Notifications, UserSettingsPage.Permissions),
                pages(),
            )
        }

    /** The server refuses every settings save to user 1 from anyone else, as the web client's own menu knows (#1005). */
    @Test
    fun `nobody but the owner is offered the owner's settings`() =
        runTest {
            seerr.serve("GET /api/v1/user/1", """{"id":1,"displayName":"Ana","permissions":$ADMIN,"userType":3}""")

            seerr.viewer(id = 2, permissions = MANAGE_USERS)
            assertEquals(emptyList<UserSettingsPage>(), pages(userId = 1))

            seerr.viewer(id = 3, permissions = ADMIN)
            assertEquals(emptyList<UserSettingsPage>(), pages(userId = 1))
        }

    /** The server refuses a permissions write to oneself or to user 1, so the owner's own index has no Permissions page (#1006). */
    @Test
    fun `the owner is not offered their own permissions`() =
        runTest {
            seerr.serve("GET /api/v1/user/1", """{"id":1,"displayName":"Ana","permissions":$ADMIN,"userType":3}""")
            seerr.viewer(id = 1, permissions = ADMIN)

            assertFalse(UserSettingsPage.Permissions in pages(userId = 1))
        }
}
