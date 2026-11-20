package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.flags.LocalFlags
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.homelab.core.flags.Flags
import com.cooper.homelab.core.flags.SharedFlags
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.presentation.connect.ConnectError
import com.cooper.homelab.core.presentation.connect.ConnectState
import com.cooper.homelab.core.presentation.connect.ProbedServer
import com.cooper.homelab.core.presentation.connect.SignInForm
import com.cooper.homelab.core.server.address.ServerAddress
import com.cooper.homelab.core.uiandroid.connect.ConnectAddressActions
import com.cooper.homelab.core.uiandroid.connect.ConnectLinkActions
import com.cooper.homelab.core.uiandroid.connect.ConnectSignInActions

/**
 * The setup flow drawn by the shared connect screen, in the states the old screen's frames show, so a state's frame can be set beside
 * its old one (`SetupScreenshotTest`, the method of the same name). The old frames stay until the flag is gone.
 */
class SeerrConnectScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun addressLayout() = ConnectFrame(addressState())

    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun signInLayout() = ConnectFrame(signInState(SeerrSignInMode.Jellyfin))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressTyped() = ConnectFrame(addressState(address = "http://seerr.lan:5055"))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressInsecure() = ConnectFrame(addressState(address = "http://seerr.example.com", insecure = true))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressNotSeerr() = ConnectFrame(addressState(address = "http://nas.lan:9000", error = ConnectError.NotThisServer))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressInspecting() = ConnectFrame(addressState(address = "http://seerr.lan:5055", isInspecting = true))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInApiKey() = ConnectFrame(signInState(SeerrSignInMode.ApiKey, FIELD_API_KEY to PREVIEW_API_KEY))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInLocal() = ConnectFrame(signInState(SeerrSignInMode.Local, FIELD_EMAIL to "scott@example.com", FIELD_PASSWORD to "hunter2"))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInRejected() =
        ConnectFrame(
            signInState(
                SeerrSignInMode.Local,
                FIELD_EMAIL to "scott@example.com",
                FIELD_PASSWORD to "hunter2",
                error = ConnectError.Failed(ServerFailure.SessionRejected),
            ),
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInConnecting() =
        ConnectFrame(
            signInState(SeerrSignInMode.ApiKey, FIELD_API_KEY to PREVIEW_API_KEY, connecting = true),
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInResetEmailSent() =
        ConnectFrame(
            signInState(SeerrSignInMode.Local, FIELD_EMAIL to "scott@example.com"),
            notice = SetupNotice.ResetEmailSent,
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInPlex() = ConnectFrame(signInState(SeerrSignInMode.Plex, modes = listOf(SeerrSignInMode.Plex, SeerrSignInMode.ApiKey)))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInQuickConnect() =
        ConnectFrame(signInState(SeerrSignInMode.QuickConnect, modes = listOf(SeerrSignInMode.QuickConnect, SeerrSignInMode.ApiKey)))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun notSetUp() = ConnectFrame(ConnectState.NotSetUp(previewProbed(setUp = false), isChecking = false, error = null))

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = ConnectFrame(ConnectState.Loading)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun editConnection() = ConnectFrame(signInState(SeerrSignInMode.Jellyfin), title = "Edit connection", onExit = {})

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun reconnect() =
        ConnectFrame(
            signInState(SeerrSignInMode.Jellyfin),
            notice = SetupNotice.SessionRejected,
            onDisconnect = {},
        )
}

private fun previewProbed(
    setUp: Boolean = true,
    modes: List<SeerrSignInMode> = previewSetupServer().modes,
): ProbedServer<SetupServer> {
    val server = previewSetupServer(modes = modes).copy(setUp = setUp)
    return ProbedServer(
        address = requireNotNull(ServerAddress.parse(server.baseUrl)),
        name = server.title,
        setUp = setUp,
        modes = server.modes.mapNotNull { it.formMode() },
        preview = server,
    )
}

private fun addressState(
    address: String = "",
    insecure: Boolean = false,
    isInspecting: Boolean = false,
    error: ConnectError? = null,
): ConnectState<SetupServer> =
    ConnectState.Address(
        address = address,
        insecure = insecure,
        cleartextAllowed = false,
        isInspecting = isInspecting,
        error = error,
        needsLocalNetwork = false,
        canTryDemo = false,
    )

private fun signInState(
    mode: SeerrSignInMode,
    vararg values: Pair<String, String>,
    error: ConnectError? = null,
    connecting: Boolean = false,
    modes: List<SeerrSignInMode> = previewSetupServer().modes,
): ConnectState<SetupServer> =
    ConnectState.SignIn(
        server = previewProbed(modes = modes),
        form = SignInForm(mode = mode.formMode(), values = values.toMap()),
        isConnecting = connecting,
        error = error,
    )

/** The setup screen with Binge's connect wording on, as the old frames have it. */
@Composable
private fun ConnectFrame(
    state: ConnectState<SetupServer>,
    notice: SetupNotice? = null,
    resetCoolingDown: Boolean = false,
    title: String? = null,
    onExit: (() -> Unit)? = null,
    onDisconnect: (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalFlags provides Flags.of(SharedFlags.BingePromotion to true, SharedFlags.ReviewPrompt to true)) {
        SeerrConnectContent(
            ui = SeerrConnectUi(state, notice, resetCoolingDown),
            addressActions = ConnectAddressActions({}, {}, {}, {}),
            signInActions = ConnectSignInActions({}, {}, {}, {}, {}, {}),
            linkActions = ConnectLinkActions({}, {}),
            onRequestPasswordReset = {},
            title = title,
            onExit = onExit,
            onDisconnect = onDisconnect,
        )
    }
}
