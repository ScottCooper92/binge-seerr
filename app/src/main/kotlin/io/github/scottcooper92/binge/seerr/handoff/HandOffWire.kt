package io.github.scottcooper92.binge.seerr.handoff

import io.github.scottcooper92.binge.seerr.seerr.SeerrSignInMode
import kotlinx.serialization.Serializable

/**
 * What a phone sends the TV to sign in with: one of the TV's own sign-in modes, by name, and the fields that mode
 * reads. Sealed with the key from the code before it leaves the phone, so this is only ever plain text in memory.
 */
@Serializable
data class HandOffCredentials(
    val mode: String,
    val apiKey: String = "",
    val email: String = "",
    val username: String = "",
    val password: String = "",
    /** With [HAND_OFF_SESSION_MODE]: the phone's own Seerr session, which the TV adopts instead of signing in afresh (#772). */
    val session: String = "",
) {
    /** Never prints a secret: a credentials object in a log line or a failed assertion shows its mode and nothing else. */
    override fun toString(): String = "HandOffCredentials(mode=$mode)"
}

/**
 * The mode for the phone's own session rather than a sign-in to repeat: the TV checks it against the server and keeps
 * it if the server still answers to it. Not a [SeerrSignInMode], since the TV never offers it as a form; it works
 * whichever modes the server has, Plex and Quick Connect included, because the phone has already signed in.
 */
const val HAND_OFF_SESSION_MODE = "Session"

/** The sign-in modes a phone can send credentials for: the ones with fields to fill. Plex and Quick Connect finish with a code. */
internal val HandOffSignInModes =
    setOf(SeerrSignInMode.ApiKey, SeerrSignInMode.Local, SeerrSignInMode.Jellyfin, SeerrSignInMode.Emby)

/**
 * Where the TV is, for the phone app to read as data rather than as a page: the same facts [HandOffProgress] gives
 * the browser's page. [modes] are the TV's own sign-in modes by name, and [failed] is whether the last attempt was
 * refused, so the app can show its form again with a message. [attempt] is how many sets of credentials the TV has
 * taken: [failed] is about the attempt with that number, so a phone that sent attempt N reads it only once this has
 * reached N.
 */
@Serializable
data class HandOffStatus(
    val state: String,
    val server: String? = null,
    val modes: List<String> = emptyList(),
    val failed: Boolean = false,
    val attempt: Int = 0,
) {
    companion object {
        const val WAITING = "waiting"
        const val CHECKING = "checking"
        const val CONFIRM = "confirm"
        const val FAILED = "failed"
        const val SIGN_IN = "signin"
        const val CONNECTED = "connected"
    }
}

/** The answer to credentials the TV took: the number of the attempt they became. */
@Serializable
internal data class HandOffTaken(
    val ok: Boolean = true,
    val attempt: Int,
)

/** [this] as the status a phone app reads. */
internal fun HandOffProgress.toStatus(): HandOffStatus =
    when (this) {
        HandOffProgress.Waiting -> HandOffStatus(HandOffStatus.WAITING)
        HandOffProgress.Checking -> HandOffStatus(HandOffStatus.CHECKING)
        HandOffProgress.ConfirmOnTv -> HandOffStatus(HandOffStatus.CONFIRM)
        HandOffProgress.Failed -> HandOffStatus(HandOffStatus.FAILED)
        is HandOffProgress.SignIn ->
            HandOffStatus(
                HandOffStatus.SIGN_IN,
                server = server,
                modes = modes,
                failed = failed,
                attempt = attempt,
            )
        HandOffProgress.Connected -> HandOffStatus(HandOffStatus.CONNECTED)
    }

/** What the listener took from a connection: an address while the TV waits for one, or credentials while it waits to sign in. */
internal sealed interface HandOffAccepted {
    /** [session] is the phone's own session, opened from the same post, when the user chose to sign the TV in as them. */
    data class Address(
        val address: String,
        val session: String? = null,
    ) : HandOffAccepted

    data class Credentials(
        val credentials: HandOffCredentials,
    ) : HandOffAccepted
}
