package io.github.scottcooper92.binge.seerr.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.binge.designsystem.theme.BingeExpressiveTheme
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.util.createSeerrComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The phone setup on the step flow: Back from the sign-in is changing server, and the address step has no Back of its own. */
@RunWith(RobolectricTestRunner::class)
class SetupStepFlowTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var changedServer = 0
    private var left = 0
    private var disconnected = 0
    private var dispatcher: OnBackPressedDispatcher? = null

    private fun actions(onDisconnect: (() -> Unit)? = null) =
        SetupActions(
            onEditAddress = {},
            onInspect = {},
            onChangeServer = { changedServer++ },
            onEditForm = {},
            onConnect = {},
            onPlexLaunched = {},
            onCancelLink = {},
            onRequestPasswordReset = {},
            onDisconnect = onDisconnect,
        )

    private fun show(
        state: SetupUiState,
        actions: SetupActions = actions(),
        title: String? = null,
    ) = rule.setContent {
        dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        BingeExpressiveTheme(dynamicColor = false) {
            SetupScreen(state = state, actions = actions, title = title, onBack = { left += 1 }.takeIf { title != null })
        }
    }

    private fun systemBack() = rule.runOnIdle { dispatcher?.onBackPressed() }

    @Test
    fun `the arrow on the sign-in returns to the address`() {
        show(signIn())

        rule.onNodeWithContentDescription("Back").performClick()

        rule.runOnIdle { assertEquals(1, changedServer) }
    }

    @Test
    fun `system back on the sign-in returns to the address`() {
        show(signIn())

        systemBack()

        rule.runOnIdle { assertEquals(1, changedServer) }
    }

    @Test
    fun `the address step has no back of its own`() {
        show(address())

        rule.onNodeWithContentDescription("Back").assertDoesNotExist()
        systemBack()

        rule.runOnIdle { assertEquals(0, changedServer) }
    }

    @Test
    fun `edit connection keeps its title and its way out`() {
        show(address(), title = "Edit connection")

        rule.onNodeWithText("Edit connection").assertExists()
        rule.onNodeWithContentDescription("Back").performClick()

        rule.runOnIdle {
            assertEquals(1, left)
            assertEquals(0, changedServer)
        }
    }

    /** #1185: the title sits in the flow's own bar, so the sign-in has one Back, and it steps back rather than leaving. */
    @Test
    fun `edit connection's sign-in has one back, and it returns to the address`() {
        show(signIn(), title = "Edit connection")

        rule.onAllNodesWithContentDescription("Back").assertCountEquals(1)
        rule.onNodeWithContentDescription("Back").performClick()

        rule.runOnIdle {
            assertEquals(1, changedServer)
            assertEquals(0, left)
        }
    }

    @Test
    fun `reconnect offers disconnect under the sign-in`() {
        show(signIn(), actions = actions(onDisconnect = { disconnected++ }))

        rule.onNodeWithText("Disconnect").performClick()
        rule.onNode(hasText("Disconnect") and hasAnyAncestor(isDialog())).performClick()

        rule.runOnIdle {
            assertEquals(1, disconnected)
            assertEquals(0, changedServer)
        }
    }

    private fun address() = SetupUiState.Address(serverUrl = "http://seerr.lan:5055", insecure = false, isInspecting = false, error = null)

    private fun signIn() =
        SetupUiState.SignIn(
            server =
                SetupServer(
                    baseUrl = "http://seerr.lan:5055",
                    title = "Living room",
                    variant = SeerrVariant.Jellyseerr,
                    versionLabel = "2.7.2",
                    mediaServerName = null,
                    modes = listOf(SeerrSignInMode.Local),
                    canResetPassword = false,
                    backdropUrl = null,
                ),
            form = SignInForm(mode = SeerrSignInMode.Local),
            isConnecting = false,
            link = null,
            error = null,
            notice = null,
        )
}
