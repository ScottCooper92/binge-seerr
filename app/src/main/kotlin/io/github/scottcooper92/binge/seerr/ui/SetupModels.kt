package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import kotlinx.serialization.Serializable

/** Why an attempt failed, as the setup form shows it. */
enum class SetupError { InvalidUrl, NotSeerr, Rejected, Unreachable, Unknown, LinkExpired }

/** Something that went right and wants saying: the only one so far is the reset email. */
enum class SetupNotice { ResetEmailSent, }

/**
 * The server the address step found. The modes are the profile's, in the order the form offers
 * them: the media server's own sign-in first, the local account, and the API key last as the
 * admin path.
 */
data class SetupServer(
    val baseUrl: String,
    val title: String,
    val variant: SeerrVariant,
    /** Null for a development build. */
    val versionLabel: String?,
    /** The Jellyfin/Emby server's own name where the admin set one; the mode's label when present. */
    val mediaServerName: String?,
    val modes: List<SeerrSignInMode>,
    val canResetPassword: Boolean,
    val backdropUrl: String?,
)

/** The sign-in step's fields. Secrets live here only until the attempt finishes. */
data class SignInForm(
    val mode: SeerrSignInMode = SeerrSignInMode.ApiKey,
    val apiKey: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
) {
    val canSubmit: Boolean
        get() =
            when (mode) {
                SeerrSignInMode.ApiKey -> apiKey.isNotBlank()
                SeerrSignInMode.Local -> email.isNotBlank() && password.isNotBlank()
                SeerrSignInMode.Jellyfin, SeerrSignInMode.Emby -> username.isNotBlank() && password.isNotBlank()
                SeerrSignInMode.Plex, SeerrSignInMode.QuickConnect -> true
            }

    val canRequestReset: Boolean get() = email.isNotBlank()
}

/** A sign-in that finishes somewhere else: the user approves a code, and the form waits. */
sealed interface LinkFlow {
    val code: String

    /** [launchPending] is true until the screen has opened [authUrl] once; it is not reopened on recomposition. */
    data class Plex(
        override val code: String,
        val authUrl: String,
        val launchPending: Boolean,
    ) : LinkFlow

    data class QuickConnect(
        override val code: String,
    ) : LinkFlow
}

/**
 * A [LinkFlow] as it is kept across the process dying while the user is away approving it. Only
 * what cannot be derived again is stored: the server is read again on the way back, and the Plex
 * authorisation URL is rebuilt from this install's own identity rather than saved with the code.
 */
@Serializable
internal sealed interface PendingLink {
    val serverUrl: String
    val code: String

    /** Whether this was Settings' Edit connection, whose saved credentials the form must not read as "connected". */
    val editing: Boolean

    val mode: SeerrSignInMode

    @Serializable
    data class Plex(
        override val serverUrl: String,
        override val code: String,
        override val editing: Boolean,
        val pinId: Long,
        val expiresAtEpochMillis: Long?,
    ) : PendingLink {
        override val mode: SeerrSignInMode get() = SeerrSignInMode.Plex
    }

    @Serializable
    data class QuickConnect(
        override val serverUrl: String,
        override val code: String,
        override val editing: Boolean,
        val secret: String,
    ) : PendingLink {
        override val mode: SeerrSignInMode get() = SeerrSignInMode.QuickConnect
    }
}

/** The screen's state: which step of setup the user is on, or the saved connection. */
sealed interface SetupUiState {
    data object Loading : SetupUiState

    /** Step one: the address, and nothing else until the server answers. */
    data class Address(
        val serverUrl: String,
        val insecure: Boolean,
        val isInspecting: Boolean,
        val error: SetupError?,
    ) : SetupUiState

    /** Step two: this server's own sign-in modes. */
    data class SignIn(
        val server: SetupServer,
        val form: SignInForm,
        val isConnecting: Boolean,
        val link: LinkFlow?,
        val error: SetupError?,
        val notice: SetupNotice?,
    ) : SetupUiState

    /** The credentials landed; the home swaps to the hub on the next frame. */
    data class Connected(
        val credentials: SeerrCredentials,
    ) : SetupUiState
}
