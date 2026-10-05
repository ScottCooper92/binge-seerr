package io.github.scottcooper92.binge.seerr.handoff

import io.github.scottcooper92.binge.seerr.seerr.isValidBaseUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** One open hand-off on the television: the URL its code carries, and the wait for an address. */
interface AddressHandOffSession {
    /** `http://<lan-ip>:<port>/a/<token>`, the URL the QR code holds. */
    val url: String

    /**
     * Serves the page until a phone posts an address the TV can use, and returns it. Cancellable:
     * the listener stops within [ACCEPT_POLL_MILLIS] of being cancelled, or once the connection
     * it is answering at the time has finished.
     */
    suspend fun awaitAddress(): String

    /** Stops listening. Safe to call more than once and from any thread. */
    fun close()
}

/** How often a waiting listener looks up from `accept()` to see whether it has been cancelled. */
internal const val ACCEPT_POLL_MILLIS = 250

private const val DRAIN_CHUNK = 4 * 1024
private const val MAX_DRAIN_BYTES = 64 * 1024

/**
 * The television's side of the hand-off (#323): a tiny HTTP/1.1 listener on the TV's own LAN
 * address that answers exactly one path and accepts exactly one address.
 *
 * - **One-time token.** It answers only `/a/<token>`, where the token is 128 random bits minted for
 *   this listener and shown only in the code on screen. Any other path, a wrong token included, is
 *   a bare 404 that says nothing about what is listening. The comparison is constant-time.
 * - **LAN only.** The socket is bound to the TV's private IPv4 address on the active Wi-Fi or
 *   Ethernet network, never to every interface.
 * - **Short-lived.** It listens while the code is on screen, until a timeout, or until one address
 *   is accepted, whichever comes first; the owner closes it on each of those.
 * - **Address only.** What it accepts is a server address, checked with [isValidBaseUrl], and
 *   nothing else. The TV then reads that server exactly as if the address had been typed, so the
 *   plain-HTTP opt-in and sign-in that follow are unchanged.
 *
 * One connection at a time, each bounded by [HandOffLimits]: request size, header count, a read
 * timeout and an overall deadline. Plain HTTP because there is no certificate a TV could present
 * that a phone would trust; what crosses the LAN is the address the user would otherwise type.
 */
internal class AddressHandOffListener(
    private val server: ServerSocket,
    private val token: String,
    override val url: String,
    private val page: HandOffPage,
    private val isAcceptable: (String) -> Boolean = String::isValidBaseUrl,
    private val limits: HandOffLimits = HandOffLimits(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AddressHandOffSession {
    private val path = "/a/$token".toByteArray(StandardCharsets.UTF_8)

    override suspend fun awaitAddress(): String =
        withContext(dispatcher) {
            server.soTimeout = ACCEPT_POLL_MILLIS
            var accepted: String? = null
            while (accepted == null) {
                ensureActive()
                val client =
                    try {
                        server.accept()
                    } catch (_: SocketTimeoutException) {
                        null
                    }
                accepted = client?.use(::serve)
            }
            accepted
        }

    override fun close() = server.close()

    /** Answers one connection, and returns the address it carried if it was accepted. */
    private fun serve(client: Socket): String? {
        client.soTimeout = limits.readTimeoutMillis
        val outcome =
            try {
                HandOffRequestReader(limits).read(client.getInputStream())
            } catch (_: IOException) {
                // A connection that stalls or drops mid-request gets no answer at all.
                return null
            }
        val (response, address) =
            when (outcome) {
                is ReadOutcome.Refused -> HandOffResponse(outcome.status, page.refused()) to null
                is ReadOutcome.Parsed -> respond(outcome.request)
            }
        try {
            client.getOutputStream().apply {
                write(response.bytes())
                flush()
            }
        } catch (_: IOException) {
            // The address still counts: the phone asked for it to be used, and the TV has it.
        }
        if (outcome is ReadOutcome.Refused) drain(client)
        return address
    }

    /**
     * After refusing a request it stopped reading, the listener reads and drops what is left, up to
     * a bound, before it closes. Closing on unread bytes resets the connection, and the client would
     * see a reset instead of the refusal. The drain is bounded by time as well as bytes, so a client
     * that trickles one byte per read timeout cannot hold the listener.
     */
    private fun drain(client: Socket) {
        try {
            client.shutdownOutput()
            val input = client.getInputStream()
            val buffer = ByteArray(DRAIN_CHUNK)
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(limits.drainDeadlineMillis)
            var total = 0
            var remaining = limits.drainDeadlineMillis
            while (total < MAX_DRAIN_BYTES && remaining > 0) {
                client.soTimeout = minOf(remaining, limits.readTimeoutMillis.toLong()).toInt()
                val count = input.read(buffer)
                total = if (count < 0) MAX_DRAIN_BYTES else total + count
                remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            }
        } catch (_: IOException) {
            // A client that keeps sending or goes quiet is closed on regardless.
        }
    }

    /** The answer to one parsed request, and the address it accepted, if any. */
    internal fun respond(request: HandOffRequest): Pair<HandOffResponse, String?> {
        val target = request.target.substringBefore('?').toByteArray(StandardCharsets.UTF_8)
        if (!MessageDigest.isEqual(target, path)) return HandOffResponse(HttpStatus.NotFound, page.refused()) to null
        val language = request.header("accept-language")
        return when (request.method) {
            "GET" -> HandOffResponse(HttpStatus.Ok, page.form(language, invalid = false)) to null
            "POST" -> {
                val address = request.body.formFields()["address"]?.trim()
                if (address != null && address.isNotEmpty() && isAcceptable(address)) {
                    HandOffResponse(HttpStatus.Ok, page.sent(language)) to address
                } else {
                    HandOffResponse(HttpStatus.BadRequest, page.form(language, invalid = true)) to null
                }
            }
            else -> HandOffResponse(HttpStatus.NotFound, page.refused()) to null
        }
    }
}
