package io.github.scottcooper92.binge.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.binge.designsystem.tv.preview.TvPreviewsOnBlack
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestError
import io.github.scottcooper92.binge.seerr.ui.AdvancedRequestUiState
import io.github.scottcooper92.binge.seerr.ui.Choice
import io.github.scottcooper92.binge.seerr.ui.SetupActions
import io.github.scottcooper92.binge.seerr.ui.SetupError
import io.github.scottcooper92.binge.seerr.ui.SetupServer
import io.github.scottcooper92.binge.seerr.ui.SetupUiState
import io.github.scottcooper92.binge.seerr.ui.SignInForm

/*
 * The television frames, one per state a remote can reach and one per focused control, on the design
 * system's TV panel. Rendered by Android Studio's preview pane: this repository has no screenshot suite,
 * so the frames are the review surface rather than a gate.
 */

private val NoSetupActions = SetupActions({}, {}, {}, {}, {}, {}, {}, {})

private val NoAdvancedActions = TvAdvancedRequestActions({}, {}, {}, {}, {}, {})

private val SampleServer =
    SetupServer(
        baseUrl = "http://seerr.lan:5055",
        title = "Living room Jellyseerr",
        variant = SeerrVariant.Jellyseerr,
        versionLabel = "2.7.2",
        mediaServerName = "Attic Jellyfin",
        modes = listOf(SeerrSignInMode.Jellyfin, SeerrSignInMode.Local, SeerrSignInMode.ApiKey),
        canResetPassword = true,
        backdropUrl = null,
    )

private fun address(
    serverUrl: String = "",
    insecure: Boolean = false,
    isInspecting: Boolean = false,
    error: SetupError? = null,
) = SetupUiState.Address(serverUrl = serverUrl, insecure = insecure, isInspecting = isInspecting, error = error)

private fun signIn(
    form: SignInForm = SignInForm(mode = SeerrSignInMode.Jellyfin),
    server: SetupServer = SampleServer,
    isConnecting: Boolean = false,
    error: SetupError? = null,
) = SetupUiState.SignIn(server = server, form = form, isConnecting = isConnecting, link = null, error = error, notice = null)

private fun ready(
    isLoadingChoices: Boolean = false,
    isSubmitting: Boolean = false,
    error: AdvancedRequestError? = null,
) = AdvancedRequestUiState.Ready(
    servers = listOf(Choice(1, "Radarr"), Choice(2, "Radarr 4K")),
    serverId = 1,
    profiles = listOf(Choice(10, "HD-1080p"), Choice(20, "Ultra-HD")),
    profileId = 10,
    rootFolders = listOf("/data/media/movies", "/data/media/kids"),
    rootFolder = "/data/media/movies",
    isLoadingChoices = isLoadingChoices,
    isSubmitting = isSubmitting,
    error = error,
)

@TvPreviewsOnBlack
@Composable
internal fun TvSetupAddressPreview() {
    TvSetupScreen(state = address(), actions = NoSetupActions, initialFocus = TvSetupFocus.Address)
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupAddressTypedPreview() {
    TvSetupScreen(
        state = address(serverUrl = "http://seerr.lan:5055", insecure = false),
        actions = NoSetupActions,
        initialFocus = TvSetupFocus.Continue,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupAddressInsecurePreview() {
    TvSetupScreen(state = address(serverUrl = "http://seerr.example.com", insecure = true), actions = NoSetupActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupAddressErrorPreview() {
    TvSetupScreen(state = address(serverUrl = "http://nas.lan:9000", error = SetupError.NotSeerr), actions = NoSetupActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupAddressInspectingPreview() {
    TvSetupScreen(state = address(serverUrl = "http://seerr.lan:5055", isInspecting = true), actions = NoSetupActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupSignInJellyfinPreview() {
    TvSetupScreen(state = signIn(), actions = NoSetupActions, initialFocus = TvSetupFocus.Credential)
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupSignInApiKeyPreview() {
    TvSetupScreen(
        state = signIn(form = SignInForm(mode = SeerrSignInMode.ApiKey, apiKey = "MTc0NDE1NzQ0MjQyMzFhYzY0")),
        actions = NoSetupActions,
        initialFocus = TvSetupFocus.Connect,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupSignInRejectedPreview() {
    TvSetupScreen(
        state =
            signIn(
                form = SignInForm(mode = SeerrSignInMode.Local, email = "scott@example.com", password = "hunter2"),
                error = SetupError.Rejected,
            ),
        actions = NoSetupActions,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupSignInConnectingPreview() {
    TvSetupScreen(
        state = signIn(form = SignInForm(mode = SeerrSignInMode.ApiKey, apiKey = "MTc0NDE1NzQ0MjQyMzFhYzY0"), isConnecting = true),
        actions = NoSetupActions,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupSignInNothingTypedPreview() {
    TvSetupScreen(
        state = signIn(server = SampleServer.copy(modes = listOf(SeerrSignInMode.Plex, SeerrSignInMode.QuickConnect))),
        actions = NoSetupActions,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupLoadingPreview() {
    TvSetupScreen(state = SetupUiState.Loading, actions = NoSetupActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvAdvancedRequestReadyPreview() {
    TvAdvancedRequestScreen(state = ready(), actions = NoAdvancedActions, initialFocusedLabel = "Radarr")
}

@TvPreviewsOnBlack
@Composable
internal fun TvAdvancedRequestSubmitFocusedPreview() {
    TvAdvancedRequestScreen(state = ready(), actions = NoAdvancedActions, initialSubmitFocused = true)
}

@TvPreviewsOnBlack
@Composable
internal fun TvAdvancedRequestLoadingChoicesPreview() {
    TvAdvancedRequestScreen(state = ready(isLoadingChoices = true), actions = NoAdvancedActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvAdvancedRequestRejectedPreview() {
    TvAdvancedRequestScreen(state = ready(error = AdvancedRequestError.Rejected), actions = NoAdvancedActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvAdvancedRequestSubmittingPreview() {
    TvAdvancedRequestScreen(state = ready(isSubmitting = true), actions = NoAdvancedActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvAdvancedRequestNotConnectedPreview() {
    TvAdvancedRequestScreen(state = AdvancedRequestUiState.Failed(AdvancedRequestError.NotConnected), actions = NoAdvancedActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvAdvancedRequestUnreachablePreview() {
    TvAdvancedRequestScreen(state = AdvancedRequestUiState.Failed(AdvancedRequestError.Unreachable), actions = NoAdvancedActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvAdvancedRequestLoadingPreview() {
    TvAdvancedRequestScreen(state = AdvancedRequestUiState.Loading, actions = NoAdvancedActions)
}
