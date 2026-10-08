package io.github.scottcooper92.binge.seerr.handoff

import java.security.SecureRandom
import java.util.Base64

/**
 * The browsers a code's PIN has matched for (#909). Each is a random value set as a cookie on the answer that matched,
 * so a match opens the code to that browser and to nobody else who has the token. One connection is answered at a
 * time, so there is no lock.
 */
internal class HandOffClients(
    token: String,
) {
    private val name = "handoff-$token"
    private val known = ArrayDeque<String>()

    /** Whether [request] carries a cookie this set handed out. */
    fun recognises(request: HandOffRequest): Boolean =
        request
            .header("cookie")
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')
            ?.let { it in known } == true

    /** A new client's `Set-Cookie` value, remembered. Only the latest [MAX_CLIENTS] are kept, so a flood of matches cannot grow it. */
    fun mint(): String {
        val bytes = ByteArray(CLIENT_BYTES).also(SecureRandom()::nextBytes)
        val value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        if (known.size >= MAX_CLIENTS) known.removeFirst()
        known.addLast(value)
        return "$name=$value; Path=/; HttpOnly; SameSite=Strict"
    }

    private companion object {
        const val MAX_CLIENTS = 16
        const val CLIENT_BYTES = 16
    }
}
