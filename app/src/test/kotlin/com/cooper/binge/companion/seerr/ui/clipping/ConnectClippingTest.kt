package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.runtime.Composable
import com.binge.designsystem.theme.BingeExpressiveTheme
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.SeerrConnectContent
import com.cooper.binge.companion.seerr.ui.SeerrConnectUi
import com.cooper.binge.companion.seerr.ui.SetupError
import com.cooper.binge.companion.seerr.ui.SetupNotice
import com.cooper.binge.companion.seerr.ui.SetupServer
import com.cooper.binge.companion.seerr.ui.formMode
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.connect.ConnectError
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.presentation.connect.SignInForm
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.uiandroid.connect.ConnectAddressActions
import com.cooper.homelab.core.uiandroid.connect.ConnectLinkActions
import com.cooper.homelab.core.uiandroid.connect.ConnectSignInActions
import com.cooper.homelab.core.uiandroid.consent.ConsentScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_ADDRESS = "https://a-rather-long-hostname.living-room.home.example.org:5055/requests"
private const val LONG_SERVER = "Alexandria Fitzgerald-Montgomery's Front Room Jellyseerr"

/**
 * The setup flow on the shared connect screen, the consent question with a long address and server, each error and notice
 * the flow can say, and every sign-in mode, at font scales 1.3 and 2.0, through the design system's
 * `assertNoTextClipped`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h8000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ConnectClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test fun `an insecure address at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { Address(failure = unreachable(), insecure = true) }

    @Test fun `an insecure address at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Address(failure = unreachable(), insecure = true) }

    @Test fun `an address needing the local network at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Address(needsLocalNetwork = true) }

    @Test fun `an address denied the local network at 2_0`() =
        rule.assertNoClippingAt(FONT_SCALE_20) { Address(failure = ConnectError.Failed(ServerFailure.LocalNetworkDenied)) }

    @Test fun `an address that is not a server at 2_0`() =
        rule.assertNoClippingAt(FONT_SCALE_20) {
            Address(failure = ConnectError.NotThisServer)
        }

    @Test fun `an expired link at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Address(failure = ConnectError.LinkExpired) }

    @Test fun `a local sign-in at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { SignIn(SeerrSignInMode.Local, rejected()) }

    @Test fun `a local sign-in at 2_0`() =
        rule.assertNoClippingAt(FONT_SCALE_20, allowBrokenWords = true) { SignIn(SeerrSignInMode.Local, rejected()) }

    @Test fun `a sign-in with a reset notice at 2_0`() =
        rule.assertNoClippingAt(FONT_SCALE_20, allowBrokenWords = true) {
            SignIn(SeerrSignInMode.Local, notice = SetupNotice.ResetEmailSent)
        }

    @Test fun `a sign-in that came back rejected at 2_0`() =
        rule.assertNoClippingAt(FONT_SCALE_20, allowBrokenWords = true) {
            SignIn(SeerrSignInMode.ApiKey, notice = SetupNotice.SessionRejected)
        }

    @Test fun `a media server sign-in at 2_0`() =
        rule.assertNoClippingAt(FONT_SCALE_20, allowBrokenWords = true) {
            SignIn(SeerrSignInMode.Jellyfin, resetError = SetupError.HandOffSessionRejected)
        }

    @Test fun `a plex sign-in at 2_0`() =
        rule.assertNoClippingAt(FONT_SCALE_20, allowBrokenWords = true) { SignIn(SeerrSignInMode.Plex) }

    @Test fun `a quick connect sign-in at 2_0`() =
        rule.assertNoClippingAt(FONT_SCALE_20, allowBrokenWords = true) { SignIn(SeerrSignInMode.QuickConnect) }

    @Test fun `the consent question at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { ConsentScreen(onChoice = {}) }

    @Test fun `the consent question at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { ConsentScreen(onChoice = {}) }

    private fun unreachable() = ConnectError.Failed(ServerFailure.Unreachable())

    private fun rejected() = ConnectError.Failed(ServerFailure.SessionRejected)

    @Composable
    private fun Address(
        failure: ConnectError? = null,
        insecure: Boolean = false,
        needsLocalNetwork: Boolean = false,
    ) = Connect(
        SeerrConnectUi(
            ConnectState.Address(
                address = LONG_ADDRESS,
                insecure = insecure,
                cleartextAllowed = false,
                isInspecting = false,
                error = failure,
                needsLocalNetwork = needsLocalNetwork,
                canTryDemo = true,
            ),
        ),
    )

    @Composable
    private fun SignIn(
        mode: SeerrSignInMode,
        failure: ConnectError? = null,
        notice: SetupNotice? = null,
        resetError: SetupError? = null,
    ) {
        val modes =
            listOf(
                SeerrSignInMode.Jellyfin,
                SeerrSignInMode.Local,
                SeerrSignInMode.Plex,
                SeerrSignInMode.QuickConnect,
                SeerrSignInMode.ApiKey,
            )
        val server =
            SetupServer(
                baseUrl = LONG_ADDRESS,
                title = LONG_SERVER,
                variant = SeerrVariant.Jellyseerr,
                versionLabel = "3.1.0-develop.2026.10.12",
                mediaServerName = "Alexandria's Attic Jellyfin Server",
                modes = modes,
                canResetPassword = true,
                backdropUrl = null,
            )
        val probed =
            ProbedServer(
                address = requireNotNull(ServerAddress.parse(LONG_ADDRESS)),
                name = LONG_SERVER,
                setUp = true,
                modes = modes.mapNotNull { it.formMode() },
                preview = server,
            )
        val form =
            SignInForm(
                mode = mode.formMode(),
                values =
                    mapOf(
                        "email" to "alexandria.fitzgerald-montgomery@a-rather-long-domain.example.com",
                        "password" to "hunter2",
                        "apiKey" to "MTc0NDE1NzQ0MjQyMzFhYzY0",
                    ),
            )
        Connect(
            SeerrConnectUi(
                ConnectState.SignIn(probed, form, isConnecting = false, error = failure),
                notice = notice,
                resetError = resetError,
            ),
        )
    }

    @Composable
    private fun Connect(ui: SeerrConnectUi) =
        BingeExpressiveTheme(dynamicColor = false) {
            SeerrConnectContent(
                ui = ui,
                addressActions = ConnectAddressActions({}, {}, {}, {}),
                signInActions = ConnectSignInActions({}, {}, {}, {}, {}, {}),
                linkActions = ConnectLinkActions({}, {}),
                onRequestPasswordReset = {},
            )
        }
}
