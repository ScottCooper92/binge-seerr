package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.ui.SetupActions
import io.github.scottcooper92.binge.seerr.ui.SetupError
import io.github.scottcooper92.binge.seerr.ui.SetupUiState
import io.github.scottcooper92.binge.seerr.ui.SignInForm

private val NoSetupActions = SetupActions({}, {}, {}, {}, {}, {}, {}, {})

private const val API_KEY = "MTc0NDE1NzQ0MjQyMzFhYzY0"

/**
 * The television setup's arms past the empty address step, which `TvSetupAddressStepScreenshotTest`
 * frames: an address typed, refused and inspected, and each sign-in the remote can finish, with the
 * focused control where the frame seeds one. The states come from `TvPreviews.kt`'s builders, so the
 * IDE preview and the frame cannot drift.
 */
class TvSetupScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun addressTyped() =
        TvSetupScreen(
            state = setupAddress(serverUrl = "http://seerr.lan:5055"),
            actions = NoSetupActions,
            initialFocus = TvSetupFocus.Continue,
        )

    /** Plain http to a name that is not a private address: the warning under the field. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun addressInsecure() =
        TvSetupScreen(state = setupAddress(serverUrl = "http://seerr.example.com", insecure = true), actions = NoSetupActions)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun addressNotSeerr() =
        TvSetupScreen(state = setupAddress(serverUrl = "http://nas.lan:9000", error = SetupError.NotSeerr), actions = NoSetupActions)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun addressInspecting() =
        TvSetupScreen(state = setupAddress(serverUrl = "http://seerr.lan:5055", isInspecting = true), actions = NoSetupActions)

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun loading() = TvSetupScreen(state = SetupUiState.Loading, actions = NoSetupActions)

    /** Jellyfin sign-in with the credential field focused. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun signInJellyfin() = TvSetupScreen(state = setupSignIn(), actions = NoSetupActions, initialFocus = TvSetupFocus.Credential)

    /** An API key typed, with Connect focused. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun signInApiKey() =
        TvSetupScreen(
            state = setupSignIn(form = SignInForm(mode = SeerrSignInMode.ApiKey, apiKey = API_KEY)),
            actions = NoSetupActions,
            initialFocus = TvSetupFocus.Connect,
        )

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun signInRejected() =
        TvSetupScreen(
            state =
                setupSignIn(
                    form = SignInForm(mode = SeerrSignInMode.Local, email = "scott@example.com", password = "hunter2"),
                    error = SetupError.Rejected,
                ),
            actions = NoSetupActions,
        )

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun signInConnecting() =
        TvSetupScreen(
            state = setupSignIn(form = SignInForm(mode = SeerrSignInMode.ApiKey, apiKey = API_KEY), isConnecting = true),
            actions = NoSetupActions,
        )

    /** A server whose only sign-ins finish elsewhere, so there is nothing to type. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun signInNothingTyped() =
        TvSetupScreen(
            state = setupSignIn(server = SampleSetupServer.copy(modes = listOf(SeerrSignInMode.Plex, SeerrSignInMode.QuickConnect))),
            actions = NoSetupActions,
        )
}
