package io.github.scottcooper92.binge.seerr.ui

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrScreenStatePreview
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode

/**
 * The phone setup: the two steps' layouts across the device matrix, then each state of them on the
 * phone cell alone. The sign-in step's fields follow the mode, so a frame per mode that draws
 * differently. The link sheet is a modal with no stateless split, so it has no frame.
 */
class SetupScreenshotTest {
    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun addressLayout() = SetupScreen(state = previewAddress(), actions = NoSetupActions)

    @PreviewTest
    @SeerrScreenPreviews
    @Composable
    fun signInLayout() = SetupScreen(state = previewSignIn(), actions = NoSetupActions)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun loading() = SetupScreen(state = SetupUiState.Loading, actions = NoSetupActions)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressTyped() = SetupScreen(state = previewAddress(serverUrl = "http://seerr.lan:5055"), actions = NoSetupActions)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressInsecure() =
        SetupScreen(state = previewAddress(serverUrl = "http://seerr.example.com", insecure = true), actions = NoSetupActions)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressNotSeerr() =
        SetupScreen(state = previewAddress(serverUrl = "http://nas.lan:9000", error = SetupError.NotSeerr), actions = NoSetupActions)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun addressInspecting() =
        SetupScreen(state = previewAddress(serverUrl = "http://seerr.lan:5055", isInspecting = true), actions = NoSetupActions)

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInApiKey() =
        SetupScreen(
            state = previewSignIn(form = SignInForm(mode = SeerrSignInMode.ApiKey, apiKey = PREVIEW_API_KEY)),
            actions = NoSetupActions,
        )

    /** A local account with an email typed, which is what turns on the forgotten-password link. */
    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInLocal() =
        SetupScreen(
            state = previewSignIn(form = SignInForm(mode = SeerrSignInMode.Local, email = "scott@example.com", password = "hunter2")),
            actions = NoSetupActions,
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInRejected() =
        SetupScreen(
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
        SetupScreen(
            state =
                previewSignIn(
                    form = SignInForm(mode = SeerrSignInMode.Local, email = "scott@example.com"),
                    notice = SetupNotice.ResetEmailSent,
                ),
            actions = NoSetupActions,
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInConnecting() =
        SetupScreen(
            state = previewSignIn(form = SignInForm(mode = SeerrSignInMode.ApiKey, apiKey = PREVIEW_API_KEY), isConnecting = true),
            actions = NoSetupActions,
        )

    @PreviewTest
    @SeerrScreenStatePreview
    @Composable
    fun signInPlex() =
        SetupScreen(
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
        SetupScreen(
            state =
                previewSignIn(
                    form = SignInForm(mode = SeerrSignInMode.QuickConnect),
                    server = previewSetupServer(modes = listOf(SeerrSignInMode.QuickConnect, SeerrSignInMode.ApiKey)),
                ),
            actions = NoSetupActions,
        )
}
