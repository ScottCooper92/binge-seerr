package io.github.scottcooper92.binge.seerr.ui

import io.github.scottcooper92.binge.seerr.seerr.SeerrCredentials
import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import io.github.scottcooper92.binge.seerr.seerr.SeerrVariant
import kotlinx.serialization.Serializable

/** Why an attempt failed, as the setup form shows it. */
enum class SetupError {
    InvalidUrl,
    NotSeerr,
    Rejected,
    Unreachable,

    /** [Unreachable], for an address a phone sent that is an IP literal off the home network. */
    UnreachableNotLocal,
    LocalNetworkDenied,
    Unknown,
    LinkExpired,

    /** A phone sent its sign-in with the address, and the server didn't accept it here (#772); the TV signs in itself. */
    HandOffSessionRejected,
}

/** Something the form wants to say that is not an error in what was typed: the reset email went, or why the form is up. */
enum class SetupNotice {
    ResetEmailSent,

    /** The server rejected the saved sign-in, so the app has come back to the form on its own (#810). */
    SessionRejected,
}

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

/**
 * The television's "send the address from your phone" plate (#323): the code a phone scans while the
 * TV listens for an address, or why it cannot listen at all.
 */
sealed interface AddressHandOff {
    /**
     * Listening. [url] is what the plate spells out under the code, for typing; [scanUrl] is what the code
     * carries, which is [url] and the key that seals credentials, in a fragment a browser never sends. [pin] is what
     * the plate shows beside the code for the phone to ask for (#803); null for a code with no key.
     */
    data class Listening(
        val url: String,
        val scanUrl: String = url,
        val pin: String? = null,
    ) : AddressHandOff

    data class Unavailable(
        val reason: Reason,
    ) : AddressHandOff

    enum class Reason {
        /** No private IPv4 address on Wi-Fi or Ethernet. */
        NoLocalNetwork,

        /** The listener could not be opened, or failed while open. */
        CouldNotListen,
    }
}

/** The screen's state: which step of setup the user is on, or the saved connection. */
sealed interface SetupUiState {
    data object Loading : SetupUiState

    /**
     * Step one: the address, and nothing else until the server answers. An [insecure] address, plain
     * HTTP to a public host, is not read until the user has ticked [cleartextAllowed] for it. A local
     * address on a platform that gates the local network shows [needsLocalNetwork] until it is allowed;
     * meanwhile asking for the permission takes Continue's place, and a grant reads the server at once. A [handOff] in
     * progress takes the television's page over until it ends.
     */
    data class Address(
        val serverUrl: String,
        val insecure: Boolean,
        val isInspecting: Boolean,
        val error: SetupError?,
        val cleartextAllowed: Boolean = false,
        val handOff: AddressHandOff? = null,
        val needsLocalNetwork: Boolean = false,
        /** The hand-off's live code, kept after an address arrives so a phone can send another if that one failed. */
        val code: AddressHandOff.Listening? = null,
        /** Whether [serverUrl] came from a phone rather than the remote. */
        val received: Boolean = false,
    ) : SetupUiState {
        val canContinue: Boolean get() = serverUrl.isNotBlank() && !isInspecting && (!insecure || cleartextAllowed)

        /**
         * A phone sent an address that uses plain HTTP to a public host, and the TV is waiting for the user to agree to it on
         * screen (#907). Until they do, nothing is read, so the TV shows the opt-in rather than its code.
         */
        val awaitingCleartextConsent: Boolean get() = received && insecure && !cleartextAllowed && !isInspecting
    }

    /** Step two: this server's own sign-in modes. */
    data class SignIn(
        val server: SetupServer,
        val form: SignInForm,
        val isConnecting: Boolean,
        val link: LinkFlow?,
        val error: SetupError?,
        val notice: SetupNotice?,
        /** The hand-off's code, while one is live: on a TV the step opens on it, so a phone can finish the sign-in. */
        val code: AddressHandOff.Listening? = null,
    ) : SetupUiState

    /** The credentials landed; the home swaps to the hub on the next frame. */
    data class Connected(
        val credentials: SeerrCredentials,
    ) : SetupUiState
}
