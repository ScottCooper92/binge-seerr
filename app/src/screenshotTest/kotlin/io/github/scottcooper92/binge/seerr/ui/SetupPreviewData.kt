package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant

internal val NoSetupActions = SetupActions({}, {}, {}, {}, {}, {}, {}, {})

internal const val PREVIEW_API_KEY = "MTc0NDE1NzQ0MjQyMzFhYzY0"

internal fun previewSetupServer(
    modes: List<SeerrSignInMode> = listOf(SeerrSignInMode.Jellyfin, SeerrSignInMode.Local, SeerrSignInMode.ApiKey),
    canResetPassword: Boolean = true,
) = SetupServer(
    baseUrl = "http://seerr.lan:5055",
    title = "Living room Jellyseerr",
    variant = SeerrVariant.Jellyseerr,
    versionLabel = "2.7.2",
    mediaServerName = "Attic Jellyfin",
    modes = modes,
    canResetPassword = canResetPassword,
    backdropUrl = null,
)

internal fun previewAddress(
    serverUrl: String = "",
    insecure: Boolean = false,
    isInspecting: Boolean = false,
    error: SetupError? = null,
    cleartextAllowed: Boolean = false,
) = SetupUiState.Address(
    serverUrl = serverUrl,
    insecure = insecure,
    isInspecting = isInspecting,
    error = error,
    cleartextAllowed = cleartextAllowed,
)

internal fun previewSignIn(
    form: SignInForm = SignInForm(mode = SeerrSignInMode.Jellyfin),
    server: SetupServer = previewSetupServer(),
    isConnecting: Boolean = false,
    error: SetupError? = null,
    notice: SetupNotice? = null,
) = SetupUiState.SignIn(server = server, form = form, isConnecting = isConnecting, link = null, error = error, notice = notice)

internal fun previewAdvancedReady(
    isLoadingChoices: Boolean = false,
    isSubmitting: Boolean = false,
    error: AdvancedRequestError? = null,
) = AdvancedRequestUiState.Ready(
    destination =
        DestinationChoices(
            servers = listOf(Choice(1, "Radarr"), Choice(2, "Radarr 4K")),
            serverId = 1,
            profiles = listOf(Choice(10, "HD-1080p"), Choice(20, "Ultra-HD")),
            profileId = 10,
            rootFolders = listOf("/data/media/movies", "/data/media/kids"),
            rootFolder = "/data/media/movies",
            loadingChoices = isLoadingChoices,
        ),
    isSubmitting = isSubmitting,
    error = error,
)
