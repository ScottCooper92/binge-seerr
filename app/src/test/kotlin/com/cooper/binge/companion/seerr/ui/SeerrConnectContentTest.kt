package com.cooper.binge.companion.seerr.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.presentation.connect.SignInForm
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.uiandroid.connect.ConnectAddressActions
import com.cooper.homelab.core.uiandroid.connect.ConnectSignInActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The setup flow on the shared connect screen: what its controls ask of the view model, and Back from the sign-in. */
@RunWith(RobolectricTestRunner::class)
class SeerrConnectContentTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private var continued = 0
    private var changedServer = 0
    private var checkedAgain = 0
    private var dispatcher: OnBackPressedDispatcher? = null

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<android.content.Context>().getString(id)

    private fun probed(setUp: Boolean): ProbedServer<SetupServer> {
        val server =
            SetupServer(
                baseUrl = "http://seerr.lan:5055",
                title = "Living room",
                variant = SeerrVariant.Jellyseerr,
                versionLabel = "2.7.2",
                mediaServerName = null,
                modes = listOf(SeerrSignInMode.Local),
                canResetPassword = false,
                backdropUrl = null,
                setUp = setUp,
            )
        return ProbedServer(
            address = requireNotNull(ServerAddress.parse(server.baseUrl)),
            name = server.title,
            setUp = setUp,
            modes = server.modes.mapNotNull { it.formMode() },
            preview = server,
        )
    }

    private fun show(
        state: ConnectState<SetupServer>,
        onDisconnect: (() -> Unit)? = null,
    ) = rule.setContent {
        dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
        BingeExpressiveTheme(dynamicColor = false) {
            SeerrConnectContent(
                ui = SeerrConnectUi(state),
                addressActions = ConnectAddressActions({}, {}, { continued++ }, {}),
                signInActions = ConnectSignInActions({}, {}, {}, {}, { changedServer++ }, { checkedAgain++ }),
                onRequestPasswordReset = {},
                onDisconnect = onDisconnect,
            )
        }
    }

    @Test
    fun `continue on the address step reads the server`() {
        show(ConnectState.Address("http://seerr.lan:5055", false, false, false, null, false, false))

        rule.onNodeWithText(string(R.string.setup_continue)).performClick()

        assertEquals(1, continued)
    }

    @Test
    fun `back from the sign-in changes server`() {
        show(ConnectState.SignIn(probed(setUp = true), SignInForm(mode = SeerrSignInMode.Local.formMode()), false, null))

        rule.waitForIdle()
        rule.runOnIdle { dispatcher?.onBackPressed() }

        rule.runOnIdle { assertEquals(1, changedServer) }
    }

    @Test
    fun `the way off a rejected server shows only when the app offers it`() {
        val state = ConnectState.SignIn(probed(setUp = true), SignInForm(mode = SeerrSignInMode.Local.formMode()), false, null)
        show(state, onDisconnect = {})
        rule.onNodeWithText(string(R.string.hub_disconnect)).assertExists()
    }

    @Test
    fun `no way off the server is offered unless the app passes one`() {
        show(ConnectState.SignIn(probed(setUp = true), SignInForm(mode = SeerrSignInMode.Local.formMode()), false, null))
        rule.onNodeWithText(string(R.string.hub_disconnect)).assertDoesNotExist()
    }

    @Test
    fun `a server that is not set up offers to check again and to change server`() {
        show(ConnectState.NotSetUp(probed(setUp = false), isChecking = false, error = null))

        rule.onNodeWithText(string(R.string.setup_check_again)).performClick()
        rule.onNodeWithText(string(R.string.setup_change_server)).performClick()

        assertEquals(1, checkedAgain)
        assertEquals(1, changedServer)
    }
}
