package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.auth.InvalidServerUrlException
import io.github.scottcooper92.binge.seerr.auth.NotSeerrServerException
import io.github.scottcooper92.binge.seerr.auth.PlexPinExpiredException
import io.github.scottcooper92.binge.seerr.auth.QuickConnectExpiredException
import io.github.scottcooper92.binge.seerr.auth.SeerrConnection
import io.github.scottcooper92.binge.seerr.auth.SeerrServerPreview
import io.github.scottcooper92.binge.seerr.handoff.AddressLocality
import io.github.scottcooper92.binge.seerr.handoff.HAND_OFF_SESSION_MODE
import io.github.scottcooper92.binge.seerr.handoff.HandOffCredentials
import io.github.scottcooper92.binge.seerr.handoff.HandOffProgress
import io.github.scottcooper92.binge.seerr.handoff.HandOffSignInModes
import io.github.scottcooper92.binge.seerr.handoff.addressLocality
import io.github.scottcooper92.binge.seerr.seerr.LocalNetworkPermission
import io.github.scottcooper92.binge.seerr.seerr.SeerrAuth
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.seerr.SeerrLoginRequest
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.isBlockedByLocalNetwork
import io.github.scottcooper92.binge.seerr.seerr.toSeerrError
import io.github.scottcooper92.binge.seerr.telemetry.Analytics
import io.github.scottcooper92.binge.seerr.telemetry.AnalyticsEvents
import io.github.scottcooper92.binge.seerr.telemetry.CrashBreadcrumbs

/** The form offers the media server's own sign-in first and the admin key last. */
private val MODE_ORDER =
    listOf(
        SeerrSignInMode.Plex,
        SeerrSignInMode.Jellyfin,
        SeerrSignInMode.Emby,
        SeerrSignInMode.QuickConnect,
        SeerrSignInMode.Local,
        SeerrSignInMode.ApiKey,
    )

/** [this] as a form for one of the server's [offered] modes that has fields to fill; null for any other, or one that is not a mode. */
internal fun HandOffCredentials.toSignInForm(offered: List<SeerrSignInMode>): SignInForm? {
    val mode =
        runCatching { SeerrSignInMode.valueOf(mode) }.getOrNull()?.takeIf { it in offered && it in HandOffSignInModes } ?: return null
    return SignInForm(mode = mode, apiKey = apiKey, username = username, email = email, password = password)
}

/** Where the phone's page should say the TV has got to. [received] is whether the address came from a phone. */
internal fun SetupUiState.toHandOffProgress(
    received: Boolean,
    failed: Boolean,
    attempts: Int,
): HandOffProgress =
    when (this) {
        SetupUiState.Loading -> HandOffProgress.Checking
        is SetupUiState.Address ->
            when {
                isInspecting -> HandOffProgress.Checking
                awaitingCleartextConsent -> HandOffProgress.ConfirmOnTv
                received && failed -> HandOffProgress.Failed
                else -> HandOffProgress.Waiting
            }
        is SetupUiState.SignIn ->
            HandOffProgress.SignIn(
                server = server.title,
                modes = server.modes.filter { it in HandOffSignInModes }.map { it.name },
                failed = failed,
                attempt = attempts,
            )
        is SetupUiState.Connected -> HandOffProgress.Connected
    }

internal fun SeerrServerPreview.toSetupServer(): SetupServer {
    val settings = profile.settings
    val modes = MODE_ORDER.filter { it in profile.signInModes }
    return SetupServer(
        baseUrl = baseUrl,
        title = settings.applicationTitle?.takeIf { it.isNotBlank() } ?: profile.variant.displayName,
        variant = profile.variant,
        versionLabel = profile.version?.label,
        mediaServerName = settings.jellyfinServerName?.takeIf { it.isNotBlank() },
        modes = modes,
        canResetPassword = settings.emailEnabled && SeerrSignInMode.Local in modes,
        backdropUrl = backdropUrls.firstOrNull(),
    )
}

/**
 * An address a phone sent that could not be reached says so more usefully when it is not a local
 * address: a phone can reach a public or VPN address that a TV on the home network cannot.
 */
internal fun SetupError.forAddress(
    url: String,
    received: Boolean,
): SetupError =
    if (this == SetupError.Unreachable && received && addressLocality(url) == AddressLocality.NotLocal) {
        SetupError.UnreachableNotLocal
    } else {
        this
    }

/** The address's own cases first, then an expired code; a 401 or 403 is the credentials; the rest is the server or the network. */
internal fun Throwable.toSetupError(): SetupError =
    when (this) {
        is InvalidServerUrlException -> SetupError.InvalidUrl
        is NotSeerrServerException -> SetupError.NotSeerr
        is PlexPinExpiredException, is QuickConnectExpiredException -> SetupError.LinkExpired
        else ->
            when (toSeerrError()) {
                SeerrError.Unauthorized, SeerrError.Forbidden -> SetupError.Rejected
                SeerrError.Unreachable -> SetupError.Unreachable
                else -> SetupError.Unknown
            }
    }

/** An unreachable local server with the permission refused is the permission, not the server. */
internal fun SetupError.orLocalNetworkDenied(
    url: String,
    permission: LocalNetworkPermission,
): SetupError = if (this == SetupError.Unreachable && url.isBlockedByLocalNetwork(permission)) SetupError.LocalNetworkDenied else this

/** [SeerrConnection.adoptSession], with the breadcrumb and the sign-in event it is owed. True when the TV is signed in. */
internal suspend fun SeerrConnection.adoptHandedSession(
    baseUrl: String,
    session: String,
    analytics: Analytics,
    crashBreadcrumbs: CrashBreadcrumbs,
): Boolean {
    crashBreadcrumbs.log("signing in with a phone's session")
    val result = adoptSession(baseUrl, session)
    analytics.event(
        AnalyticsEvents.SIGN_IN,
        mapOf(AnalyticsEvents.PARAM_METHOD to HAND_OFF_SESSION_MODE, AnalyticsEvents.PARAM_SUCCESS to result.isSuccess),
    )
    return result.isSuccess
}

/**
 * Signs in to [baseUrl] with what [form] holds, for the modes typed into the form; null for the two that finish in
 * another app, which [SetupLinks] runs instead.
 */
internal suspend fun SeerrConnection.signIn(
    baseUrl: String,
    form: SignInForm,
): Result<*>? =
    when (form.mode) {
        SeerrSignInMode.ApiKey -> connect(baseUrl, SeerrAuth.ApiKey(form.apiKey.trim()))
        SeerrSignInMode.Local -> logIn(baseUrl, SeerrLoginRequest.Local(form.email.trim(), form.password))
        SeerrSignInMode.Jellyfin, SeerrSignInMode.Emby -> logIn(baseUrl, SeerrLoginRequest.Jellyfin(form.username.trim(), form.password))
        SeerrSignInMode.Plex, SeerrSignInMode.QuickConnect -> null
    }
