package com.cooper.binge.companion.seerr.ui.clipping

import androidx.compose.runtime.Composable
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.SetupError
import com.cooper.binge.companion.seerr.ui.SetupNotice
import com.cooper.binge.companion.seerr.ui.SetupScreen
import com.cooper.binge.companion.seerr.ui.SetupServer
import com.cooper.binge.companion.seerr.ui.SetupUiState
import com.cooper.binge.companion.seerr.ui.SignInForm
import com.cooper.binge.companion.seerr.ui.consent.ConsentScreen
import com.cooper.binge.companion.seerr.ui.tv.NoSetupActions
import com.cooper.binge.companion.seerr.util.createSeerrComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LONG_ADDRESS = "https://a-rather-long-hostname.living-room.home.example.org:5055/requests"
private const val LONG_SERVER = "Alexandria Fitzgerald-Montgomery's Front Room Jellyseerr"

/**
 * The setup flow, the consent question with a long address and server, each error and notice
 * the flow can say, and every sign-in mode, at font scales 1.3 and 2.0, through the design system's
 * `assertNoTextClipped`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h8000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SetupClippingTest {
    @get:Rule
    val rule = createSeerrComposeRule()

    @Test fun `an insecure address at 1_3`() =
        rule.assertNoClippingAt(
            FONT_SCALE_13,
        ) { Address(error = SetupError.UnreachableNotLocal, insecure = true) }

    @Test fun `an insecure address at 2_0`() =
        rule.assertNoClippingAt(
            FONT_SCALE_20,
        ) { Address(error = SetupError.UnreachableNotLocal, insecure = true) }

    @Test fun `an address needing the local network at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Address(needsLocalNetwork = true) }

    @Test fun `an address denied the local network at 2_0`() =
        rule.assertNoClippingAt(
            FONT_SCALE_20,
        ) { Address(error = SetupError.LocalNetworkDenied) }

    @Test fun `an address that is not a server at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Address(error = SetupError.NotSeerr) }

    @Test fun `an expired link at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { Address(error = SetupError.LinkExpired) }

    @Test fun `a local sign-in at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { SignIn(SeerrSignInMode.Local, SetupError.Rejected) }

    @Test fun `a local sign-in at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { SignIn(SeerrSignInMode.Local, SetupError.Rejected) }

    @Test fun `a sign-in with a reset notice at 2_0`() =
        rule.assertNoClippingAt(FONT_SCALE_20) {
            SignIn(SeerrSignInMode.Local, notice = SetupNotice.ResetEmailSent)
        }

    @Test fun `a sign-in that came back rejected at 2_0`() =
        rule.assertNoClippingAt(FONT_SCALE_20) {
            SignIn(SeerrSignInMode.ApiKey, notice = SetupNotice.SessionRejected)
        }

    @Test fun `a media server sign-in at 2_0`() =
        rule.assertNoClippingAt(FONT_SCALE_20) {
            SignIn(SeerrSignInMode.Jellyfin, SetupError.HandOffSessionRejected)
        }

    @Test fun `a plex sign-in at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { SignIn(SeerrSignInMode.Plex) }

    @Test fun `a quick connect sign-in at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { SignIn(SeerrSignInMode.QuickConnect) }

    @Test fun `the consent question at 1_3`() = rule.assertNoClippingAt(FONT_SCALE_13) { ConsentScreen(onChoice = {}) }

    @Test fun `the consent question at 2_0`() = rule.assertNoClippingAt(FONT_SCALE_20) { ConsentScreen(onChoice = {}) }

    @Composable
    private fun Address(
        error: SetupError? = null,
        insecure: Boolean = false,
        needsLocalNetwork: Boolean = false,
    ) = SetupScreen(
        state =
            SetupUiState.Address(
                serverUrl = LONG_ADDRESS,
                insecure = insecure,
                isInspecting = false,
                error = error,
                cleartextAllowed = false,
                needsLocalNetwork = needsLocalNetwork,
            ),
        actions = NoSetupActions,
    )

    @Composable
    private fun SignIn(
        mode: SeerrSignInMode,
        error: SetupError? = null,
        notice: SetupNotice? = null,
    ) = SetupScreen(
        state =
            SetupUiState.SignIn(
                server =
                    SetupServer(
                        baseUrl = LONG_ADDRESS,
                        title = LONG_SERVER,
                        variant = SeerrVariant.Jellyseerr,
                        versionLabel = "3.1.0-develop.2026.10.12",
                        mediaServerName = "Alexandria's Attic Jellyfin Server",
                        modes =
                            listOf(
                                SeerrSignInMode.Jellyfin,
                                SeerrSignInMode.Local,
                                SeerrSignInMode.Plex,
                                SeerrSignInMode.QuickConnect,
                                SeerrSignInMode.ApiKey,
                            ),
                        canResetPassword = true,
                        backdropUrl = null,
                    ),
                form =
                    SignInForm(
                        mode = mode,
                        email = "alexandria.fitzgerald-montgomery@a-rather-long-domain.example.com",
                        password = "hunter2",
                        apiKey = "MTc0NDE1NzQ0MjQyMzFhYzY0",
                    ),
                isConnecting = false,
                link = null,
                error = error,
                notice = notice,
            ),
        actions = NoSetupActions,
    )
}
