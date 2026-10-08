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

    /** The PIN the TV shows beside the code, which the phone asks for before it trusts the link (#803); null without a key. */
    val pin: String?

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
        onAddress: (address: String, session: String?) -> Unit,
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
 * address that answers exactly one path and accepts an address only while the TV is waiting for one.
 *
 * - **One-time token.** It answers only `/a/<token>` (the page and the address), `/s/<token>` (the TV's status as JSON) and
 *   `/c/<token>` (sealed credentials), where the token is about 40 random bits, short
 *   enough to type, minted for this listener and shown only in the code on screen. That is enough
 *   because the listener is LAN-only, serves one connection at a time, takes an address only while
 *   the TV is waiting for one, and is bounded in time: before an address arrives its port and token are
 *   replaced every five minutes, and after one it stays up for at most the sign-in timeout. Any other path, a wrong token included, is a bare 404 that says
 *   nothing about what is listening. The comparison is constant-time.
 * - **PIN step.** When the TV also shows a PIN, the token alone does not let a client send an address or
 *   credentials. A client proves it by posting the PIN, and the match is bound to that client: the listener
 *   answers it with a random cookie (see `HandOffClients`) and trusts later requests that carry it. A match
 *   does not open the code to anyone else. The app keeps no cookies and sends the PIN with every post. Five
 *   wrong PINs lock the code.
 * - **LAN only.** The socket is bound to the TV's private IPv4 address on the active Wi-Fi or
 *   Ethernet network, never to every interface.
 * - **Short-lived.** It accepts an address while the TV is waiting for one: the first, and another
 *   after one that named no server. Once an address is in, it only answers status pages, and its
 *   owner keeps it up through the sign-in. It closes on cancel, when the TV reaches Connected (after a
 *   short linger), or after the sign-in timeout. A code nobody uses lapses after a timeout, and the
 *   owner replaces it with a new listener, a new port and a new token, rather than ending.
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

    override val pin: String? = key?.pin()

    /** The browsers that have shown they can see this TV, by the PIN beside its code (#909). A sender that keeps no cookies, as the app does not, sends the PIN with every post. */
    private val clients = HandOffClients(token)

    @Volatile private var wrongPins = 0

    /** After [MAX_WRONG_PINS] wrong tries the code takes no more: guessing 4 digits needs more chances than that. */
    private val locked: Boolean get() = wrongPins >= MAX_WRONG_PINS

    /** Whether [request] comes from a client the PIN already matched for, or the code has no PIN to match. */
    private fun trusted(request: HandOffRequest): Boolean = pin == null || clients.recognises(request)

    /**
     * Whether [request] lets a phone act: it comes from a client that has matched, or [given] matches the PIN now, in which
     * case the answer carries the cookie that marks the client. A miss counts.
     */
    private fun pinCheck(
        request: HandOffRequest,
        given: String?,
    ): PinCheck {
        val expected = pin
        return when {
            expected == null || clients.recognises(request) -> PinCheck.Trusted
            locked || given == null -> PinCheck.Refused
            MessageDigest.isEqual(given.trim().toByteArray(), expected.toByteArray()) -> PinCheck.Matched(clients.mint())
            else -> {
                wrongPins++
                PinCheck.Refused
            }
        }
    }

    private sealed interface PinCheck {
        data object Trusted : PinCheck

        data object Refused : PinCheck

        data class Matched(
            val cookie: String,
        ) : PinCheck
    }

    /** Where a phone acts on the code, and so where the page asks for the PIN until the client has matched. */
    private fun HandOffProgress.asksForPin(trusted: Boolean): Boolean =
        !trusted && (this is HandOffProgress.Waiting || this is HandOffProgress.Failed || this is HandOffProgress.SignIn)

    override suspend fun serve(
        progress: () -> HandOffProgress,
        onAddress: (address: String, session: String?) -> Unit,
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
                    is HandOffAccepted.Address -> onAddress(accepted.address, accepted.session)
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
            "GET" ->
                if (progress.asksForPin(trusted(request))) {
                    HandOffResponse(HttpStatus.Ok, page.pin(language, wrong = false, locked = locked)) to null
                } else {
                    HandOffResponse(HttpStatus.Ok, page.status(language, progress)) to null
                }
            "POST" -> post(request, progress, language)
            else -> HandOffResponse(HttpStatus.NotFound, page.refused()) to null
        }

    /** An address is read only while the TV is waiting for one; otherwise a post is answered with where things stand. */
    private fun post(
        request: HandOffRequest,
        progress: HandOffProgress,
        language: String?,
    ): Pair<HandOffResponse, HandOffAccepted?> {
        val fields = request.body.formFields()
        val check = pinCheck(request, fields["pin"])
        val cookie = (check as? PinCheck.Matched)?.cookie
        pinGate(check, fields, progress, language)?.let { return it.copy(clientCookie = cookie) to null }
        val address = fields["address"]?.trim()
        // The phone's session can ride on the same post (#772), sealed like any credentials. One that doesn't open,
        // or isn't a session, refuses the whole post rather than sending the TV on without it.
        val sealed = fields["sealed"]
        val session = sealed?.let(::openSession)
        val addressOk = !address.isNullOrEmpty() && isAcceptable(address)
        val sessionOk = sealed == null || session != null
        return if (addressOk && sessionOk) {
            HandOffResponse(HttpStatus.Ok, page.status(language, HandOffProgress.Checking), clientCookie = cookie) to
                HandOffAccepted.Address(checkNotNull(address), session)
        } else {
            HandOffResponse(HttpStatus.BadRequest, page.form(language, invalid = true), clientCookie = cookie) to null
        }
    }

    /**
     * What a post gets instead of being read for an address, if anything. The page's PIN form posts the PIN alone and the
     * app sends it with the address; either way nothing goes on without it (#909). A post of the PIN alone, once it
     * matches, opens whatever the page would show now, and so does any post while the TV isn't waiting for an address.
     */
    private fun pinGate(
        check: PinCheck,
        fields: Map<String, String>,
        progress: HandOffProgress,
        language: String?,
    ): HandOffResponse? =
        when {
            check is PinCheck.Refused ->
                HandOffResponse(HttpStatus.Forbidden, page.pin(language, wrong = fields["pin"] != null, locked = locked))
            fields.keys == setOf("pin") || !progress.acceptsAddress -> HandOffResponse(HttpStatus.Ok, page.status(language, progress))
            else -> null
        }

    /** The session [sealed] carries, if it opens under this listener's key for this token and is a session. */
    private fun openSession(sealed: String): String? =
        key
            ?.open(sealed, token)
            ?.let { runCatching { STATUS_JSON.decodeFromString<HandOffCredentials>(it.decodeToString()) }.getOrNull() }
            ?.takeIf { it.mode == HAND_OFF_SESSION_MODE }
            ?.session
            ?.takeIf { it.isNotEmpty() }

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
        val fields = request.body.formFields()
        val check = pinCheck(request, fields["pin"])
        if (check is PinCheck.Refused) return HandOffResponse(HttpStatus.Forbidden, REFUSED_JSON, JSON_TYPE) to null
        val cookie = (check as? PinCheck.Matched)?.cookie
        val credentials =
            fields["sealed"]
                ?.let { key?.open(it, token) }
                ?.let { runCatching { STATUS_JSON.decodeFromString<HandOffCredentials>(it.decodeToString()) }.getOrNull() }
                ?.takeIf { it.mode in progress.modes || (it.mode == HAND_OFF_SESSION_MODE && it.session.isNotEmpty()) }
                ?: return HandOffResponse(HttpStatus.BadRequest, REFUSED_JSON, JSON_TYPE, clientCookie = cookie) to null
        // The number the TV will count these as, so the phone knows which attempt a later `failed` is about.
        val taken = STATUS_JSON.encodeToString(HandOffTaken(attempt = progress.attempt + 1))
        return HandOffResponse(HttpStatus.Ok, taken, JSON_TYPE, clientCookie = cookie) to HandOffAccepted.Credentials(credentials)
    }

    private companion object {
        const val MAX_WRONG_PINS = 5
        const val JSON_TYPE = "application/json; charset=utf-8"
        const val REFUSED_JSON = "{\"ok\":false}"
        val STATUS_JSON = Json { ignoreUnknownKeys = true }
    }
}
