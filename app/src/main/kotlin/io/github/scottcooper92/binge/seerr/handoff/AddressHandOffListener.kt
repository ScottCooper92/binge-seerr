package io.github.scottcooper92.binge.seerr.handoff

import io.github.scottcooper92.binge.seerr.seerr.isValidBaseUrl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** One open hand-off on the television: the URL its code carries, and the wait for an address. */
interface AddressHandOffSession {
    /** `http://<lan-ip>:<port>/a/<token>`, the URL a phone's browser opens and the plate spells out for typing. */
    val url: String

    /** What the QR code holds: [url] and, in its fragment, the key a phone seals credentials with. */
    val scanUrl: String

    /**
     * Serves the page until cancelled or closed. [progress] is read on every request, so the page follows the
     * TV; [onAddress] gets each acceptable address a phone posts while [progress] still accepts one, which
     * is the first, and any sent again after a server that could not be reached. [onCredentials] gets sign-in
     * credentials a phone app sealed with the key in the code, while the TV is on its sign-in step. Cancellable: the listener
     * stops within [ACCEPT_POLL_MILLIS] of being cancelled, or once the connection it is answering at the
     * time has finished.
     */
    suspend fun serve(
        progress: () -> HandOffProgress,
        onAddress: (String) -> Unit,
        onCredentials: (HandOffCredentials) -> Unit,
    )

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
 * - **One-time token.** It answers only `/a/<token>` (the page and the address), `/s/<token>` (the TV's status as JSON) and
 *   `/c/<token>` (sealed credentials), where the token is about 40 random bits, short
 *   enough to type, minted for this listener and shown only in the code on screen. That is enough
 *   because the listener is LAN-only, serves one connection at a time, and its port and token are
 *   replaced every five minutes. Any other path, a wrong token included, is a bare 404 that says
 *   nothing about what is listening. The comparison is constant-time.
 * - **LAN only.** The socket is bound to the TV's private IPv4 address on the active Wi-Fi or
 *   Ethernet network, never to every interface.
 * - **Short-lived.** It listens while the code is on screen, until a timeout, or until one address
 *   is accepted, whichever comes first; the owner closes it on each of those. When it lapses on a
 *   timeout, the owner replaces it with a new listener, a new port and a new token, rather than ending.
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
    /** Seals credentials for this TV; null where a test, or a listener meant for addresses alone, has none. */
    private val key: HandOffKey? = null,
    private val isAcceptable: (String) -> Boolean = String::isValidBaseUrl,
    private val limits: HandOffLimits = HandOffLimits(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AddressHandOffSession {
    private val path = "/a/$token".toByteArray(StandardCharsets.UTF_8)
    private val statusPath = "/s/$token".toByteArray(StandardCharsets.UTF_8)
    private val credentialsPath = "/c/$token".toByteArray(StandardCharsets.UTF_8)

    override val scanUrl: String get() = key?.let { "$url#k=${it.encoded()}" } ?: url

    override suspend fun serve(
        progress: () -> HandOffProgress,
        onAddress: (String) -> Unit,
        onCredentials: (HandOffCredentials) -> Unit,
    ) {
        withContext(dispatcher) {
            server.soTimeout = ACCEPT_POLL_MILLIS
            while (true) {
                ensureActive()
                val client =
                    try {
                        server.accept()
                    } catch (_: SocketTimeoutException) {
                        null
                    }
                when (val accepted = client?.use { serve(it, progress) }) {
                    is HandOffAccepted.Address -> onAddress(accepted.address)
                    is HandOffAccepted.Credentials -> onCredentials(accepted.credentials)
                    null -> Unit
                }
            }
        }
    }

    override fun close() = server.close()

    /** Answers one connection, and returns the address it carried if it was accepted. */
    private fun serve(
        client: Socket,
        progress: () -> HandOffProgress,
    ): HandOffAccepted? {
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
                is ReadOutcome.Parsed -> respond(outcome.request, progress())
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

    /** The answer to one parsed request, and what it handed the TV, if anything. */
    internal fun respond(
        request: HandOffRequest,
        progress: HandOffProgress = HandOffProgress.Waiting,
    ): Pair<HandOffResponse, HandOffAccepted?> {
        val target = request.target.substringBefore('?').toByteArray(StandardCharsets.UTF_8)
        val language = request.header("accept-language")
        return when {
            MessageDigest.isEqual(target, path) -> respondToPage(request, progress, language)
            MessageDigest.isEqual(target, statusPath) && request.method == "GET" ->
                HandOffResponse(HttpStatus.Ok, STATUS_JSON.encodeToString(progress.toStatus()), JSON_TYPE) to null
            MessageDigest.isEqual(target, credentialsPath) && request.method == "POST" -> receiveCredentials(request, progress)
            else -> HandOffResponse(HttpStatus.NotFound, page.refused()) to null
        }
    }

    private fun respondToPage(
        request: HandOffRequest,
        progress: HandOffProgress,
        language: String?,
    ): Pair<HandOffResponse, HandOffAccepted?> =
        when (request.method) {
            "GET" -> HandOffResponse(HttpStatus.Ok, page.status(language, progress)) to null
            "POST" -> post(request, progress, language)
            else -> HandOffResponse(HttpStatus.NotFound, page.refused()) to null
        }

    /** An address is read only while the TV is waiting for one; otherwise a post is answered with where things stand. */
    private fun post(
        request: HandOffRequest,
        progress: HandOffProgress,
        language: String?,
    ): Pair<HandOffResponse, HandOffAccepted?> {
        if (!progress.acceptsAddress) return HandOffResponse(HttpStatus.Ok, page.status(language, progress)) to null
        val address = request.body.formFields()["address"]?.trim()
        return if (address != null && address.isNotEmpty() && isAcceptable(address)) {
            HandOffResponse(HttpStatus.Ok, page.status(language, HandOffProgress.Checking)) to HandOffAccepted.Address(address)
        } else {
            HandOffResponse(HttpStatus.BadRequest, page.form(language, invalid = true)) to null
        }
    }

    /**
     * Credentials a phone app sealed with the key in the code. Read only while the TV is on its sign-in step, and
     * only if they open under this listener's key for this token; anything else is refused without saying which
     * part was wrong. Nothing here is ever logged or echoed.
     */
    private fun receiveCredentials(
        request: HandOffRequest,
        progress: HandOffProgress,
    ): Pair<HandOffResponse, HandOffAccepted?> {
        if (progress !is HandOffProgress.SignIn) return HandOffResponse(HttpStatus.Conflict, REFUSED_JSON, JSON_TYPE) to null
        val credentials =
            request.body
                .formFields()["sealed"]
                ?.let { key?.open(it, token) }
                ?.let { runCatching { STATUS_JSON.decodeFromString<HandOffCredentials>(it.decodeToString()) }.getOrNull() }
                ?.takeIf { it.mode in progress.modes }
                ?: return HandOffResponse(HttpStatus.BadRequest, REFUSED_JSON, JSON_TYPE) to null
        return HandOffResponse(HttpStatus.Ok, OK_JSON, JSON_TYPE) to HandOffAccepted.Credentials(credentials)
    }

    private companion object {
        const val JSON_TYPE = "application/json; charset=utf-8"
        const val OK_JSON = "{\"ok\":true}"
        const val REFUSED_JSON = "{\"ok\":false}"
        val STATUS_JSON = Json { ignoreUnknownKeys = true }
    }
}
