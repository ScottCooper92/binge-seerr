package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.presentation.connect.SignInForm
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.uiandroid.connect.ConnectAddressActions
import com.cooper.homelab.core.uiandroid.connect.ConnectLinkActions
import com.cooper.homelab.core.uiandroid.connect.ConnectSignInActions
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The shared path's sign-in with the keyboard up on a small phone: the focused field is whole, below the tabs and above the pinned
 * button, because the backdrop and heading step aside for it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp")
class SeerrConnectKeyboardTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    private fun show(fontScale: Float) =
        rule.setContent {
            WithKeyboard(260.dp) {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    BingeExpressiveTheme(dynamicColor = false) {
                        SeerrConnectContent(
                            ui = SeerrConnectUi(signIn()),
                            addressActions = ConnectAddressActions({}, {}, {}, {}),
                            signInActions = ConnectSignInActions({}, {}, {}, {}, {}, {}),
                            linkActions = ConnectLinkActions({}, {}),
                            onRequestPasswordReset = {},
                        )
                    }
                }
            }
        }

    private fun assertFieldClear() {
        rule.onNodeWithText("Living room").assertDoesNotExist()
        rule.onNodeWithText("Username").performTextInput("scott")
        rule.waitForIdle()

        val tab = rule.onNodeWithText("Jellyfin").getUnclippedBoundsInRoot()
        val field = rule.onNodeWithText("Username").getUnclippedBoundsInRoot()
        val button = rule.onNodeWithText("Sign in").getUnclippedBoundsInRoot()
        assertTrue("the field ${field.top} is under the tabs ${tab.bottom}", field.top >= tab.bottom)
        assertTrue("the field ${field.bottom} runs behind the button ${button.top}", field.bottom <= button.top)
    }

    @Test
    fun `the server's heading shows while the keyboard is down`() {
        rule.setContent {
            BingeExpressiveTheme(dynamicColor = false) {
                SeerrConnectContent(
                    ui = SeerrConnectUi(signIn()),
                    addressActions = ConnectAddressActions({}, {}, {}, {}),
                    signInActions = ConnectSignInActions({}, {}, {}, {}, {}, {}),
                    linkActions = ConnectLinkActions({}, {}),
                    onRequestPasswordReset = {},
                )
            }
        }

        rule.onNodeWithText("Living room").assertExists()
    }

    @Test
    fun `the focused field is clear of the tabs and the button at 1x`() {
        show(fontScale = 1f)

        assertFieldClear()
    }

    @Test
    fun `the focused field is clear of the tabs and the button at 2x`() {
        show(fontScale = 2f)

        assertFieldClear()
    }

    private fun signIn(): ConnectState<SetupServer> {
        val server =
            SetupServer(
                baseUrl = "http://seerr.lan:5055",
                title = "Living room",
                variant = SeerrVariant.Jellyseerr,
                versionLabel = "2.7.2",
                mediaServerName = null,
                modes = listOf(SeerrSignInMode.Local, SeerrSignInMode.Jellyfin),
                canResetPassword = false,
                backdropUrl = null,
                setUp = true,
            )
        val probed =
            ProbedServer(
                address = requireNotNull(ServerAddress.parse(server.baseUrl)),
                name = server.title,
                setUp = true,
                modes = server.modes.mapNotNull { it.formMode() },
                preview = server,
            )
        return ConnectState.SignIn(probed, SignInForm(mode = SeerrSignInMode.Jellyfin.formMode()), false, null)
    }
}
