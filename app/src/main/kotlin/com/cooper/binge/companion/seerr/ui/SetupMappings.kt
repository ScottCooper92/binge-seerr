package com.cooper.binge.companion.seerr.ui

import com.binge.companion.sdk.attempt
import com.cooper.binge.companion.seerr.R
import com.cooper.binge.companion.seerr.auth.InvalidServerUrlException
import com.cooper.binge.companion.seerr.auth.NotSeerrServerException
import com.cooper.binge.companion.seerr.auth.PlexPinExpiredException
import com.cooper.binge.companion.seerr.auth.QuickConnectExpiredException
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.auth.SeerrServerPreview
import com.cooper.binge.companion.seerr.handoff.HAND_OFF_SESSION_MODE
import com.cooper.binge.companion.seerr.handoff.HandOffCredentials
import com.cooper.binge.companion.seerr.handoff.HandOffProgress
import com.cooper.binge.companion.seerr.handoff.HandOffSignInModes
import com.cooper.binge.companion.seerr.handoff.addressLocality
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.seerr.SeerrSignInMode
import com.cooper.binge.companion.seerr.seerr.isBlockedByLocalNetwork
import com.cooper.binge.companion.seerr.seerr.toSeerrError
import com.cooper.binge.companion.seerr.seerr.wantsClientCertificate
import com.cooper.binge.companion.seerr.telemetry.reportSignIn
import com.cooper.homelab.core.server.network.AddressLocality
import com.cooper.homelab.core.server.network.LocalNetworkPermission
import com.cooper.homelab.core.telemetry.Analytics
import com.cooper.homelab.core.telemetry.CrashBreadcrumbs
import com.cooper.binge.companion.seerr.feature.admin.R as AdminR

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
        attempt { SeerrSignInMode.valueOf(mode) }.getOrNull()?.takeIf { it in offered && it in HandOffSignInModes } ?: return null
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
                awaitingConfirm || awaitingCleartextConsent -> HandOffProgress.ConfirmOnTv
                received && failed -> HandOffProgress.Failed
                else -> HandOffProgress.Waiting
            }
        is SetupUiState.SignIn ->
            HandOffProgress.SignIn(
                server = server.title,
                address = server.baseUrl,
                modes = server.modes.filter { it in HandOffSignInModes }.map { it.name },
                failed = failed,
                attempt = attempts,
                serverId = server.instanceId,
            )
        // The server was reached and has nothing to sign in to yet: the page says so, as the TV does, and follows it (#1991).
        is SetupUiState.NotSetUp -> HandOffProgress.NotSetUp
        is SetupUiState.Connected -> HandOffProgress.Connected
    }

internal fun SeerrServerPreview.toSetupServer(): SetupServer {
    val settings = profile.settings
    val modes = MODE_ORDER.filter { it in profile.signInModes }
    return SetupServer(
        baseUrl = baseUrl,
        title = settings.applicationTitle?.takeIf { it.isNotBlank() } ?: profile.variant.displayName,
        variant = profile.variant,
        versionLabel = profile.versionLabel,
        mediaServerName = settings.jellyfinServerName?.takeIf { it.isNotBlank() },
        modes = modes,
        canResetPassword = settings.emailEnabled && SeerrSignInMode.Local in modes,
        backdropUrl = backdropUrls.firstOrNull(),
        instanceId = profile.instanceId,
        setUp = profile.isSetUp,
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

/**
 * The address's own cases first, then an expired code; a server that asks for a client certificate is its own case, since it did answer;
 * a 401 or 403 is the credentials; the rest is the server or the network.
 */
internal fun Throwable.toSetupError(): SetupError =
    when (this) {
        is InvalidServerUrlException -> SetupError.InvalidUrl
        is NotSeerrServerException -> SetupError.NotSeerr
        is PlexPinExpiredException, is QuickConnectExpiredException -> SetupError.LinkExpired
        else ->
            when {
                wantsClientCertificate() -> SetupError.ClientCertificateNeeded
                else ->
                    when (toSeerrError()) {
                        SeerrError.Unauthorized, SeerrError.Forbidden -> SetupError.Rejected
                        SeerrError.Unreachable -> SetupError.Unreachable
                        else -> SetupError.Unknown
                    }
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
    analytics.reportSignIn(HAND_OFF_SESSION_MODE, result.isSuccess)
    return result.isSuccess
}

internal fun SetupError.messageRes(): Int =
    when (this) {
        SetupError.InvalidUrl -> R.string.setup_error_invalid_url
        SetupError.NotSeerr -> R.string.setup_error_not_seerr
        SetupError.Rejected -> R.string.setup_error_rejected
        SetupError.Unreachable -> R.string.setup_error_unreachable
        SetupError.UnreachableNotLocal -> R.string.setup_error_unreachable_not_local
        SetupError.LocalNetworkDenied -> R.string.setup_error_local_network_denied
        SetupError.ClientCertificateNeeded -> R.string.setup_error_client_certificate
        SetupError.Unknown -> R.string.setup_error_unknown
        SetupError.LinkExpired -> AdminR.string.setup_error_link_expired
        SetupError.HandOffSessionRejected -> R.string.setup_error_handoff_session_rejected
    }
