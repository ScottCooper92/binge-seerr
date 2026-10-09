package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.preview.SeerrTvSpanishScreenPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.ui.AddressHandOff
import io.github.scottcooper92.binge.seerr.ui.SetupActions
import io.github.scottcooper92.binge.seerr.ui.SetupError
import io.github.scottcooper92.binge.seerr.ui.SetupNotice
import io.github.scottcooper92.binge.seerr.ui.SetupUiState
import io.github.scottcooper92.binge.seerr.ui.SignInForm

private val NoSetupActions = SetupActions({}, {}, {}, {}, {}, {}, {}, {})

private const val API_KEY = "MTc0NDE1NzQ0MjQyMzFhYzY0"

/**
 * The television setup's arms past the empty address step, which `TvSetupAddressStepScreenshotTest`
 * frames: an address typed, refused and inspected, and each sign-in the remote can finish, with the
 * focused control where the frame seeds one. The states come from `TvPreviewData.kt`'s builders, so the
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

    /** Plain http to a name that is not a private address: the warning and the opt-in under the field, Continue held. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun addressInsecure() =
        TvSetupScreen(state = setupAddress(serverUrl = "http://seerr.example.com", insecure = true), actions = NoSetupActions)

    /**
     * #907: an address a phone sent that waits on the opt-in. The page is the typed form, landed on the opt-in, and the
     * field sits where it does with no note under it (compare [addressTyped]).
     */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun addressFromPhoneAwaitingOptIn() =
        TvSetupScreen(
            state = setupAddress(serverUrl = "http://seerr.example.com", insecure = true).copy(received = true),
            actions = NoSetupActions,
            offerHandOff = true,
        )

    /** The same in Spanish, whose warning runs longest: it has to fit above the button bar without moving the field. */
    @PreviewTest
    @SeerrTvSpanishScreenPreviews
    @Composable
    fun addressFromPhoneAwaitingOptInSpanish() =
        TvSetupScreen(
            state = setupAddress(serverUrl = "http://seerr.example.com", insecure = true).copy(received = true),
            actions = NoSetupActions,
            offerHandOff = true,
        )

    /** The opt-in ticked: Continue beside the field is what goes on now. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun addressInsecureAllowed() =
        TvSetupScreen(
            state = setupAddress(serverUrl = "http://seerr.example.com", insecure = true).copy(cleartextAllowed = true),
            actions = NoSetupActions,
        )

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

    /**
     * The server rejected the saved session (#810): the code page on the saved server, saying so, with Disconnect beside
     * Change server for a server that is gone.
     */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun reconnectCode() =
        TvSetupScreen(
            state = setupSignIn(notice = SetupNotice.SessionRejected).copy(code = RECONNECT_CODE),
            actions = ReconnectActions,
            offerHandOff = true,
        )

    /** The same, typed with the remote: the reason under the fields. Disconnect stays on the code page. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun reconnectTyped() = TvSetupScreen(state = setupSignIn(notice = SetupNotice.SessionRejected), actions = ReconnectActions)
}

private val RECONNECT_CODE = AddressHandOff.Listening(url = "http://192.168.86.53:41234/a/k7m2pqx4", pin = "4821")

private val ReconnectActions = SetupActions({}, {}, {}, {}, {}, {}, {}, {}, onDisconnect = {})
