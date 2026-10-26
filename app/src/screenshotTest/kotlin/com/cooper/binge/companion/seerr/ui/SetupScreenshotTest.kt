package com.cooper.binge.companion.seerr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.stringResource
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.flags.Flags
import com.cooper.binge.companion.seerr.flags.LocalFlags
import com.cooper.binge.companion.seerr.preview.SeerrScreenPreviews
import com.cooper.binge.companion.seerr.preview.SeerrScreenStatePreview
import com.cooper.binge.companion.seerr.preview.SeerrSpanishPreviews
import com.cooper.binge.companion.seerr.preview.SeerrTabletPanesPreview
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.ui.tv.NoSetupActions

/**
 * The phone setup: the two steps' layouts across the device matrix, then each state of them on the
 * phone cell alone. The sign-in step's fields follow the mode, so a frame per mode that draws
 * differently. The link sheet is a modal with no stateless split, so it has no frame.
 *
 * The `tabletLand` frames hold the step flow's split on a wide window: heading and commit on one side, the
 * fields on the other, and neither stretched across the screen (#1104).
 */
class SetupScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun addressLayout() = SetupFrame(state = previewAddress(), actions = NoSetupActions)

    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun signInLayout() = SetupFrame(state = previewSignIn(), actions = NoSetupActions)

    /** The address step offering the demo (#1705), across the device matrix and in Spanish, whose labels run longer. */
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun addressDemoOffered() = SetupFrame(state = previewAddress().copy(canTryDemo = true), actions = NoSetupActions)

    @PreviewTest
    @SeerrSpanishPreviews
    @Composable
    fun addressDemoOfferedSpanish() = SetupFrame(state = previewAddress().copy(canTryDemo = true), actions = NoSetupActions)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = SetupFrame(state = SetupUiState.Loading, actions = NoSetupActions)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressTyped() = SetupFrame(state = previewAddress(serverUrl = "http://seerr.lan:5055"), actions = NoSetupActions)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressInsecure() =
        SetupFrame(state = previewAddress(serverUrl = "http://seerr.example.com", insecure = true), actions = NoSetupActions)

    /** The same address once the user has opted in to plain HTTP for it: Continue is enabled. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressInsecureAllowed() =
        SetupFrame(
            state = previewAddress(serverUrl = "http://seerr.example.com", insecure = true, cleartextAllowed = true),
            actions = NoSetupActions,
        )

    /** A local address on a platform that gates the local network: the explanation and the ask, with Continue still on. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressNeedsLocalNetwork() =
        SetupFrame(state = previewAddress(serverUrl = "http://192.168.1.20:5055", needsLocalNetwork = true), actions = NoSetupActions)

    /** The read failed while the permission is refused: the permission is named, not the server. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressLocalNetworkDenied() =
        SetupFrame(
            state =
                previewAddress(
                    serverUrl = "http://192.168.1.20:5055",
                    error = SetupError.LocalNetworkDenied,
                    needsLocalNetwork = true,
                ),
            actions = NoSetupActions,
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressNotSeerr() =
        SetupFrame(state = previewAddress(serverUrl = "http://nas.lan:9000", error = SetupError.NotSeerr), actions = NoSetupActions)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressInspecting() =
        SetupFrame(state = previewAddress(serverUrl = "http://seerr.lan:5055", isInspecting = true), actions = NoSetupActions)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInApiKey() =
        SetupFrame(
            state = previewSignIn(form = SignInForm(mode = SeerrSignInMode.ApiKey, apiKey = PREVIEW_API_KEY)),
            actions = NoSetupActions,
        )

    /** A local account with an email typed, which is what turns on the forgotten-password link. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInLocal() =
        SetupFrame(
            state = previewSignIn(form = SignInForm(mode = SeerrSignInMode.Local, email = "scott@example.com", password = "hunter2")),
            actions = NoSetupActions,
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInRejected() =
        SetupFrame(
            state =
                previewSignIn(
                    form = SignInForm(mode = SeerrSignInMode.Local, email = "scott@example.com", password = "hunter2"),
                    error = SetupError.Rejected,
                ),
            actions = NoSetupActions,
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInResetEmailSent() =
        SetupFrame(
            state =
                previewSignIn(
                    form = SignInForm(mode = SeerrSignInMode.Local, email = "scott@example.com"),
                    notice = SetupNotice.ResetEmailSent,
                ),
            actions = NoSetupActions,
        )

    /** The server rejected the saved session (#810): the reason under the fields, and a way to leave the server. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun reconnect() =
        SetupFrame(
            state = previewSignIn(notice = SetupNotice.SessionRejected),
            actions = SetupActions({}, {}, {}, {}, {}, {}, {}, {}, onDisconnect = {}),
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInConnecting() =
        SetupFrame(
            state = previewSignIn(form = SignInForm(mode = SeerrSignInMode.ApiKey, apiKey = PREVIEW_API_KEY), isConnecting = true),
            actions = NoSetupActions,
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInPlex() =
        SetupFrame(
            state =
                previewSignIn(
                    form = SignInForm(mode = SeerrSignInMode.Plex),
                    server = previewSetupServer(modes = listOf(SeerrSignInMode.Plex, SeerrSignInMode.ApiKey)),
                ),
            actions = NoSetupActions,
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInQuickConnect() =
        SetupFrame(
            state =
                previewSignIn(
                    form = SignInForm(mode = SeerrSignInMode.QuickConnect),
                    server = previewSetupServer(modes = listOf(SeerrSignInMode.QuickConnect, SeerrSignInMode.ApiKey)),
                ),
            actions = NoSetupActions,
        )

    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun addressTabletLand() = SetupFrame(state = previewAddress(serverUrl = "http://seerr.lan:5055"), actions = NoSetupActions)

    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun addressNeedsLocalNetworkTabletLand() =
        SetupFrame(state = previewAddress(serverUrl = "http://192.168.1.20:5055", needsLocalNetwork = true), actions = NoSetupActions)

    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun signInTabletLand() = SetupFrame(state = previewSignIn(), actions = NoSetupActions)

    @PreviewTest
    @SeerrTabletPanesPreview
    @Composable
    fun signInRejectedTabletLand() =
        SetupFrame(
            state =
                previewSignIn(
                    form = SignInForm(mode = SeerrSignInMode.Local, email = "scott@example.com", password = "hunter2"),
                    error = SetupError.Rejected,
                ),
            actions = NoSetupActions,
        )

    /** Edit connection's title sits in the flow's own bar, with the one Back: on the sign-in it steps back to the address. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun editConnection() =
        SetupFrame(
            state = previewSignIn(),
            actions = NoSetupActions,
            title = stringResource(R.string.settings_edit_connection),
            onBack = {},
        )
}

/** The setup screen with Binge's connect wording on, so these frames keep showing the intro that mentions Binge. */
@Composable
private fun SetupFrame(
    state: SetupUiState,
    actions: SetupActions,
    title: String? = null,
    onBack: (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalFlags provides Flags.AllOn) {
        SetupScreen(state = state, actions = actions, title = title, onBack = onBack)
    }
}
