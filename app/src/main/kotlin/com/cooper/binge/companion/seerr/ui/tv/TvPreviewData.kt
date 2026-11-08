package com.cooper.binge.companion.seerr.ui.tv

import androidx.compose.runtime.Composable
import com.binge.designsystem.tv.preview.TvPreviewsOnBlack
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant
import com.cooper.binge.companion.seerr.ui.LinkFlow
import com.cooper.binge.companion.seerr.ui.SetupActions
import com.cooper.binge.companion.seerr.ui.SetupError
import com.cooper.binge.companion.seerr.ui.SetupNotice
import com.cooper.binge.companion.seerr.ui.SetupServer
import com.cooper.binge.companion.seerr.ui.SetupUiState
import com.cooper.binge.companion.seerr.ui.SignInForm

/*
 * The television frames, one per state a remote can reach and one per focused control, on the design
 * system's TV panel. Rendered by Android Studio's preview pane: these particular frames aren't wired
 * into a `@PreviewTest`, unlike the dedicated TV screenshot tests under this package's `screenshotTest`
 * source set, so they're the review surface here rather than part of `validateDebugScreenshotTest`.
 */

/** The setup screens' actions, none of which does anything: a frame is a still. Shared with the screenshot frames. */
internal val NoSetupActions = SetupActions({}, {}, {}, {}, {}, {}, {}, {})

internal val SampleSetupServer =
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

internal fun setupAddress(
    serverUrl: String = "",
    insecure: Boolean = false,
    isInspecting: Boolean = false,
    error: SetupError? = null,
) = SetupUiState.Address(serverUrl = serverUrl, insecure = insecure, isInspecting = isInspecting, error = error)

internal fun setupSignIn(
    form: SignInForm = SignInForm(mode = SeerrSignInMode.Jellyfin),
    server: SetupServer = SampleSetupServer,
    isConnecting: Boolean = false,
    error: SetupError? = null,
    notice: SetupNotice? = null,
    link: LinkFlow? = null,
) = SetupUiState.SignIn(server = server, form = form, isConnecting = isConnecting, link = link, error = error, notice = notice)

@TvPreviewsOnBlack
@Composable
internal fun TvSetupAddressPreview() {
    TvSetupScreen(state = setupAddress(), actions = NoSetupActions, initialFocus = TvSetupFocus.Address)
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupAddressTypedPreview() {
    TvSetupScreen(
        state = setupAddress(serverUrl = "http://seerr.lan:5055", insecure = false),
        actions = NoSetupActions,
        initialFocus = TvSetupFocus.Continue,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupAddressInsecurePreview() {
    TvSetupScreen(state = setupAddress(serverUrl = "http://seerr.example.com", insecure = true), actions = NoSetupActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupAddressErrorPreview() {
    TvSetupScreen(state = setupAddress(serverUrl = "http://nas.lan:9000", error = SetupError.NotSeerr), actions = NoSetupActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupAddressInspectingPreview() {
    TvSetupScreen(state = setupAddress(serverUrl = "http://seerr.lan:5055", isInspecting = true), actions = NoSetupActions)
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupSignInJellyfinPreview() {
    TvSetupScreen(state = setupSignIn(), actions = NoSetupActions, initialFocus = TvSetupFocus.Credential)
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupSignInApiKeyPreview() {
    TvSetupScreen(
        state = setupSignIn(form = SignInForm(mode = SeerrSignInMode.ApiKey, apiKey = "MTc0NDE1NzQ0MjQyMzFhYzY0")),
        actions = NoSetupActions,
        initialFocus = TvSetupFocus.Connect,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupSignInRejectedPreview() {
    TvSetupScreen(
        state =
            setupSignIn(
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
        state = setupSignIn(form = SignInForm(mode = SeerrSignInMode.ApiKey, apiKey = "MTc0NDE1NzQ0MjQyMzFhYzY0"), isConnecting = true),
        actions = NoSetupActions,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupSignInNothingTypedPreview() {
    TvSetupScreen(
        state = setupSignIn(server = SampleSetupServer.copy(modes = listOf(SeerrSignInMode.Plex, SeerrSignInMode.QuickConnect))),
        actions = NoSetupActions,
    )
}

@TvPreviewsOnBlack
@Composable
internal fun TvSetupLoadingPreview() {
    TvSetupScreen(state = SetupUiState.Loading, actions = NoSetupActions)
}
