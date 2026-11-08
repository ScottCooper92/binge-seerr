package com.cooper.binge.companion.seerr.ui

import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.SeerrVariant

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
    needsLocalNetwork: Boolean = false,
) = SetupUiState.Address(
    serverUrl = serverUrl,
    insecure = insecure,
    isInspecting = isInspecting,
    error = error,
    cleartextAllowed = cleartextAllowed,
    needsLocalNetwork = needsLocalNetwork,
)

internal fun previewSignIn(
    form: SignInForm = SignInForm(mode = SeerrSignInMode.Jellyfin),
    server: SetupServer = previewSetupServer(),
    isConnecting: Boolean = false,
    error: SetupError? = null,
    notice: SetupNotice? = null,
) = SetupUiState.SignIn(server = server, form = form, isConnecting = isConnecting, link = null, error = error, notice = notice)
