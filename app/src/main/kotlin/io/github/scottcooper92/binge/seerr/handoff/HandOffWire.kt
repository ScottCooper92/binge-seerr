package io.github.scottcooper92.binge.seerr.handoff

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
) {
    /** Never prints a secret: a credentials object in a log line or a failed assertion shows its mode and nothing else. */
    override fun toString(): String = "HandOffCredentials(mode=$mode)"
}

/**
 * Where the TV is, for the phone app to read as data rather than as a page: the same facts [HandOffProgress] gives
 * the browser's page. [modes] are the TV's own sign-in modes by name, and [failed] is whether the last attempt was
 * refused, so the app can show its form again with a message.
 */
@Serializable
data class HandOffStatus(
    val state: String,
    val server: String? = null,
    val modes: List<String> = emptyList(),
    val failed: Boolean = false,
) {
    companion object {
        const val WAITING = "waiting"
        const val CHECKING = "checking"
        const val FAILED = "failed"
        const val SIGN_IN = "signin"
        const val CONNECTED = "connected"
    }
}

/** [this] as the status a phone app reads. */
internal fun HandOffProgress.toStatus(): HandOffStatus =
    when (this) {
        HandOffProgress.Waiting -> HandOffStatus(HandOffStatus.WAITING)
        HandOffProgress.Checking -> HandOffStatus(HandOffStatus.CHECKING)
        HandOffProgress.Failed -> HandOffStatus(HandOffStatus.FAILED)
        is HandOffProgress.SignIn -> HandOffStatus(HandOffStatus.SIGN_IN, server = server, modes = modes, failed = failed)
        HandOffProgress.Connected -> HandOffStatus(HandOffStatus.CONNECTED)
    }

/** What the listener took from a connection: an address while the TV waits for one, or credentials while it waits to sign in. */
internal sealed interface HandOffAccepted {
    data class Address(
        val address: String,
    ) : HandOffAccepted

    data class Credentials(
        val credentials: HandOffCredentials,
    ) : HandOffAccepted
}
