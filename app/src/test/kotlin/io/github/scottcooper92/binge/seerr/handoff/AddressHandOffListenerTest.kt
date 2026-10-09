package io.github.scottcooper92.binge.seerr.handoff

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

private const val TOKEN = "k7m2pqx4"

/** The address the TV is signing in to, once a test has moved it to its sign-in step. */
private const val SIGNING_IN_TO = "http://192.168.1.10:5055"

/**
 * The television's listener over real loopback sockets: what it answers, what it refuses, and that
 * it stops — after one address, when cancelled, and against a client that sends nothing.
 */
class AddressHandOffListenerTest {
    private val key = HandOffKey.generate()
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val port = server.localPort
    private val listener =
        AddressHandOffListener(
            server = server,
            token = TOKEN,
            url = "http://127.0.0.1:$port/a/$TOKEN",
            page = FakePage,
            key = key,
            limits = HandOffLimits(readTimeoutMillis = 300, requestDeadlineMillis = 1_000, drainDeadlineMillis = 500),
        )

    /** Apart from the test's own scope, so an assertion that fails ends the test instead of leaving it waiting on a listener that never finishes. */
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() {
        // Joined before the socket closes: a listener closed under a coroutine still accepting throws out of it.
        runBlocking { serverScope.coroutineContext.job.cancelAndJoin() }
        listener.close()
    }

    /** What the TV would report to the page; a test moves it the way the setup flow would. */
    @Volatile
    private var progress: HandOffProgress = HandOffProgress.Waiting

    /** Serves until cancelled, handing each address the listener accepts to the channel. */
    private val credentials = Channel<HandOffCredentials>(Channel.UNLIMITED)

    /** The session that came with each accepted address, or an empty string for none. */
    private val sessions = Channel<String>(Channel.UNLIMITED)

    private fun serving(): Pair<Channel<String>, Job> {
        val addresses = Channel<String>(Channel.UNLIMITED)
        return addresses to
            serverScope.launch {
                listener.serve(
                    { progress },
                    { address, session ->
                        sessions.trySend(session.orEmpty())
                        addresses.trySend(address)
                    },
                    { credentials.trySend(it) },
                )
            }
    }

    /** Sends [request] as raw bytes and reads the whole answer; the listener always closes after one. */
    private fun exchange(request: String): String =
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            socket.soTimeout = 5_000
            socket.getOutputStream().write(request.toByteArray(StandardCharsets.UTF_8))
            socket.getOutputStream().flush()
            socket.getInputStream().readBytes().toString(StandardCharsets.UTF_8)
        }

    private fun get(
        path: String,
        cookie: String? = null,
    ) = exchange(
        "GET $path HTTP/1.1\r\nHost: tv\r\nAccept-Language: es-MX,en;q=0.5\r\n" + (cookie?.let { "Cookie: $it\r\n" } ?: "") + "\r\n",
    )

    /** The `name=value` a browser would send back for the cookie [answer] set. */
    private fun cookieOf(answer: String): String =
        Regex("Set-Cookie: ([^;\r\n]+)").find(answer)?.groupValues?.get(1) ?: error("no cookie in $answer")

    /** Posts [body], with the TV's PIN added unless [pin] says otherwise: most tests are about what comes after it (#909). */
    private fun post(
        path: String,
        body: String,
        pin: String? = key.pin(),
        cookie: String? = null,
    ): String {
        val sent = if (pin == null) body else "$body&pin=$pin"
        return exchange(
            "POST $path HTTP/1.1\r\nHost: tv\r\nContent-Type: application/x-www-form-urlencoded\r\n" +
                (cookie?.let { "Cookie: $it\r\n" } ?: "") +
                "Content-Length: ${sent.toByteArray().size}\r\n\r\n$sent",
        )
    }

    /** Matches the PIN as a browser would, and returns the cookie it keeps. */
    private fun matchPin(): String = cookieOf(post("/a/$TOKEN", "pin=${key.pin()}", pin = null))

    @Test
    fun `the token's path asks for the PIN first, in the browser's language, with headers that keep it to itself`() =
        runBlocking {
            val (addresses, serving) = serving()

            val answer = get("/a/$TOKEN")

            assertTrue(answer.startsWith("HTTP/1.1 200 OK\r\n"))
            assertTrue(answer.contains("pin:es:wrong=false:locked=false"))
            assertTrue(answer.contains("Cache-Control: no-store"))
            assertTrue(answer.contains("Referrer-Policy: no-referrer"))
            assertTrue(answer.contains("Connection: close"))
            assertTrue(addresses.isEmpty)
            serving.cancelAndJoin()
        }

    @Test
    fun `nothing is taken without the PIN, a wrong one says so, and the right one opens the form`() =
        runBlocking {
            val (addresses, serving) = serving()

            assertTrue(post("/a/$TOKEN", "address=seerr.lan", pin = null).startsWith("HTTP/1.1 403 "))
            post("/a/$TOKEN", "pin=0000".takeIf { key.pin() != "0000" } ?: "pin=0001", pin = null).let {
                assertTrue(it, it.startsWith("HTTP/1.1 403 ") && it.contains("pin:en:wrong=true:locked=false"))
            }
            assertTrue(addresses.isEmpty)

            val matched = post("/a/$TOKEN", "pin=${key.pin()}", pin = null)
            assertTrue(matched, matched.contains("form:en:false"))
            val cookie = cookieOf(matched)
            // Matched once, that client's page and posts need it no more.
            assertTrue(get("/a/$TOKEN", cookie).contains("form:es:false"))
            post("/a/$TOKEN", "address=seerr.lan", pin = null, cookie = cookie)
            assertEquals("seerr.lan", withTimeout(5_000) { addresses.receive() })
            serving.cancelAndJoin()
        }

    @Test
    fun `one client's match does not open the code to another client`() =
        runBlocking {
            val (addresses, serving) = serving()
            val cookie = cookieOf(post("/a/$TOKEN", "pin=${key.pin()}", pin = null))

            // Someone else with the token, and no cookie, still meets the PIN.
            assertTrue(get("/a/$TOKEN").contains("pin:es"))
            assertTrue(post("/a/$TOKEN", "address=theirs.lan", pin = null).startsWith("HTTP/1.1 403 "))
            assertTrue(post("/a/$TOKEN", "address=theirs.lan", pin = null, cookie = "handoff-$TOKEN=guess").startsWith("HTTP/1.1 403 "))
            assertTrue(addresses.isEmpty)

            post("/a/$TOKEN", "address=mine.lan", pin = null, cookie = cookie)
            assertEquals("mine.lan", withTimeout(5_000) { addresses.receive() })
            serving.cancelAndJoin()
        }

    /** #916: the lock ends the code, so its owner can put a new one on the TV rather than leave a dead one up. */
    @Test
    fun `the fifth wrong PIN is told the code is locked, and the listener stops serving`() =
        runBlocking {
            val (addresses, serving) = serving()
            val wrong = if (key.pin() == "0000") "0001" else "0000"

            repeat(4) { post("/a/$TOKEN", "pin=$wrong", pin = null).let { assertTrue(it, it.contains("locked=false")) } }
            post("/a/$TOKEN", "pin=$wrong", pin = null).let { assertTrue(it, it.contains("locked=true")) }

            withTimeout(5_000) { serving.join() }
            assertTrue(addresses.isEmpty)
        }

    @Test
    fun `a wrong token, another path or another method gets a bare 404`() =
        runBlocking {
            val (addresses, serving) = serving()

            for (answer in listOf(get("/a/abcdefghjkmnpqrstuvw"), get("/"), get("/a/$TOKEN/x"), get("/favicon.ico"))) {
                assertTrue(answer, answer.startsWith("HTTP/1.1 404 Not Found\r\n"))
                assertFalse(answer.contains("form:"))
            }
            assertTrue(exchange("DELETE /a/$TOKEN HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 404 "))
            assertTrue(addresses.isEmpty)
            serving.cancelAndJoin()
        }

    @Test
    fun `a posted address is passed on and answered with the checking page, and the listener keeps serving`() =
        runBlocking {
            val (addresses, serving) = serving()

            val answer = post("/a/$TOKEN", "address=http%3A%2F%2F192.168.1.10%3A5055&extra=ignored")

            assertTrue(answer.startsWith("HTTP/1.1 200 OK\r\n"))
            assertTrue(answer.contains("status:en:Checking"))
            assertEquals("http://192.168.1.10:5055", withTimeout(5_000) { addresses.receive() })
            assertTrue(serving.isActive)
            serving.cancelAndJoin()
            listener.close()
            assertConnectionRefused()
        }

    @Test
    fun `a page follows the TV, and an address posted while the TV is past that step is not taken`() =
        runBlocking {
            val (addresses, serving) = serving()
            val cookie = matchPin()
            post("/a/$TOKEN", "address=seerr.lan")
            assertEquals("seerr.lan", withTimeout(5_000) { addresses.receive() })

            progress = HandOffProgress.SignIn("Living room", SIGNING_IN_TO)
            get("/a/$TOKEN", cookie).let { assertTrue(it, it.contains("status:es:SignIn(Living room)")) }
            post("/a/$TOKEN", "address=other.lan").let { assertTrue(it, it.contains("status:en:SignIn(Living room)")) }
            assertTrue(addresses.isEmpty)

            progress = HandOffProgress.Connected
            get("/a/$TOKEN", cookie).let { assertTrue(it, it.contains("status:es:Connected")) }
            serving.cancelAndJoin()
        }

    @Test
    fun `an address that named no server is taken again, from the form the page goes back to`() =
        runBlocking {
            val (addresses, serving) = serving()
            val cookie = matchPin()
            post("/a/$TOKEN", "address=nope.lan")
            assertEquals("nope.lan", withTimeout(5_000) { addresses.receive() })

            progress = HandOffProgress.Failed
            assertTrue(get("/a/$TOKEN", cookie).contains("status:es:Failed"))
            post("/a/$TOKEN", "address=seerr.lan")

            assertEquals("seerr.lan", withTimeout(5_000) { addresses.receive() })
            serving.cancelAndJoin()
        }

    @Test
    fun `an address the TV cannot use gets the form back with an error, and the listener keeps going`() =
        runBlocking {
            val (addresses, serving) = serving()
            val cookie = matchPin()

            assertTrue(post("/a/$TOKEN", "address=http%3A%2F%2F").startsWith("HTTP/1.1 400 "))
            assertTrue(post("/a/$TOKEN", "address=").contains("form:en:true"))
            assertTrue(post("/a/$TOKEN", "nothing=here").contains("form:en:true"))
            assertTrue(post("/a/$TOKEN", "address=%zz", pin = null, cookie = cookie).contains("form:en:true"))
            assertTrue(addresses.isEmpty)

            post("/a/$TOKEN", "address=seerr.lan")
            assertEquals("seerr.lan", withTimeout(5_000) { addresses.receive() })
            serving.cancelAndJoin()
        }

    @Test
    fun `oversized and malformed requests are refused before they are read`() =
        runBlocking {
            val (addresses, serving) = serving()

            assertTrue(exchange("GET /a/$TOKEN HTTP/1.1\r\nX: ${"a".repeat(9_000)}\r\n\r\n").startsWith("HTTP/1.1 431 "))
            val manyHeaders = (1..60).joinToString("") { "X-$it: y\r\n" }
            assertTrue(exchange("GET /a/$TOKEN HTTP/1.1\r\n$manyHeaders\r\n").startsWith("HTTP/1.1 431 "))
            assertTrue(exchange("POST /a/$TOKEN HTTP/1.1\r\nContent-Length: 100000\r\n\r\n").startsWith("HTTP/1.1 413 "))
            assertTrue(exchange("POST /a/$TOKEN HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n").startsWith("HTTP/1.1 400 "))
            assertTrue(exchange("POST /a/$TOKEN HTTP/1.1\r\nContent-Length: -1\r\n\r\n").startsWith("HTTP/1.1 400 "))
            assertTrue(exchange("garbage\r\n\r\n").startsWith("HTTP/1.1 400 "))
            assertTrue(exchange("GET /a/$TOKEN SPDY/3\r\n\r\n").startsWith("HTTP/1.1 400 "))
            assertTrue(exchange("GET /a/$TOKEN HTTP/1.1\r\nno colon\r\n\r\n").startsWith("HTTP/1.1 400 "))
            assertTrue(addresses.isEmpty)
            serving.cancelAndJoin()
        }

    @Test
    fun `a client that sends nothing is dropped after the read timeout, and the next one is served`() =
        runBlocking {
            val (addresses, serving) = serving()

            val silent = Socket(InetAddress.getLoopbackAddress(), port)
            silent.soTimeout = 5_000
            // The listener gives up on the read and closes without answering.
            assertEquals(-1, silent.getInputStream().read())
            silent.close()

            assertTrue(get("/a/$TOKEN").startsWith("HTTP/1.1 200 "))
            serving.cancelAndJoin()
        }

    @Test
    fun `a refused client that trickles bytes cannot hold the listener past the drain deadline`() =
        runBlocking {
            val (addresses, serving) = serving()
            val trickler = Socket(InetAddress.getLoopbackAddress(), port)
            val sending =
                thread {
                    try {
                        trickler.getOutputStream().apply {
                            write("GET /a/$TOKEN HTTP/1.1\r\nX: ${"a".repeat(9_000)}\r\n\r\n".toByteArray())
                            repeat(100) {
                                Thread.sleep(100)
                                write('x'.code)
                                flush()
                            }
                        }
                    } catch (_: IOException) {
                        // The listener closed on us, which is the point.
                    }
                }

            val answer = withTimeout(3_000) { runInterruptible(Dispatchers.IO) { get("/a/$TOKEN") } }

            assertTrue(answer.startsWith("HTTP/1.1 200 "))
            trickler.close()
            sending.join()
            serving.cancelAndJoin()
        }

    @Test
    fun `cancelling stops the listener without an address`() =
        runBlocking {
            val (addresses, serving) = serving()

            withTimeout(2_000) { serving.cancelAndJoin() }

            assertTrue(serving.isCancelled)
            listener.close()
            assertConnectionRefused()
        }

    private fun sealedFor(
        credentials: HandOffCredentials,
        using: HandOffKey = key,
        context: String = HandOffKey.context(TOKEN, SIGNING_IN_TO),
    ) = "sealed=" + using.seal(Json.encodeToString(credentials).toByteArray(), context)

    private fun sessionFor(address: String) =
        sealedFor(HandOffCredentials(mode = HAND_OFF_SESSION_MODE, session = "s:phone"), context = HandOffKey.context(TOKEN, address))

    private val local = HandOffCredentials(mode = "Local", email = "ana@example.com", password = "correct horse")

    @Test
    fun `the status route reports the TV's progress as JSON, for the token's path only`() =
        runBlocking {
            val (_, serving) = serving()

            progress = HandOffProgress.SignIn("Living room", SIGNING_IN_TO, modes = listOf("Local", "Jellyfin"), failed = true)
            val answer = get("/s/$TOKEN")

            assertTrue(answer.startsWith("HTTP/1.1 200 OK\r\n"))
            assertTrue(answer.contains("Content-Type: application/json"))
            assertEquals(
                HandOffStatus(HandOffStatus.SIGN_IN, "Living room", listOf("Local", "Jellyfin"), failed = true, address = SIGNING_IN_TO),
                Json.decodeFromString<HandOffStatus>(answer.substringAfter("\r\n\r\n")),
            )
            assertTrue(get("/s/abcdefghjkmnpqrstuvw").startsWith("HTTP/1.1 404 "))
            assertTrue(exchange("POST /s/$TOKEN HTTP/1.1\r\nContent-Length: 0\r\n\r\n").startsWith("HTTP/1.1 404 "))
            serving.cancelAndJoin()
        }

    @Test
    fun `sealed credentials are opened and passed on, only on the sign-in step and only for an offered mode`() =
        runBlocking {
            val (_, serving) = serving()

            progress = HandOffProgress.Checking
            assertTrue(post("/c/$TOKEN", sealedFor(local)).startsWith("HTTP/1.1 409 "))
            progress = HandOffProgress.SignIn("Home", SIGNING_IN_TO, modes = listOf("Jellyfin"))
            assertTrue(post("/c/$TOKEN", sealedFor(local)).startsWith("HTTP/1.1 400 "))
            assertTrue(credentials.isEmpty)

            progress = HandOffProgress.SignIn("Home", SIGNING_IN_TO, modes = listOf("Local"), attempt = 4)
            val answer = post("/c/$TOKEN", sealedFor(local))

            assertTrue(answer.startsWith("HTTP/1.1 200 OK\r\n"))
            assertTrue(answer, answer.contains("\"attempt\":5"))
            assertEquals(local, withTimeout(5_000) { credentials.receive() })
            serving.cancelAndJoin()
        }

    @Test
    fun `credentials sealed with another key or for another token, or not sealed at all, are refused`() =
        runBlocking {
            val (_, serving) = serving()
            progress = HandOffProgress.SignIn("Home", SIGNING_IN_TO, modes = listOf("Local"))

            assertTrue(post("/c/$TOKEN", sealedFor(local, using = HandOffKey.generate())).startsWith("HTTP/1.1 400 "))
            assertTrue(post("/c/$TOKEN", sealedFor(local, context = "other000")).startsWith("HTTP/1.1 400 "))
            // Sealed for the token alone, as before #1029, or for an address other than the one the TV signs in to.
            assertTrue(post("/c/$TOKEN", sealedFor(local, context = TOKEN)).startsWith("HTTP/1.1 400 "))
            assertTrue(
                post(
                    "/c/$TOKEN",
                    sealedFor(local, context = HandOffKey.context(TOKEN, "http://192.168.1.66:5055")),
                ).startsWith("HTTP/1.1 400 "),
            )
            assertTrue(post("/c/$TOKEN", "sealed=garbage").startsWith("HTTP/1.1 400 "))
            assertTrue(post("/c/$TOKEN", "password=correct+horse&email=ana%40example.com&mode=Local").startsWith("HTTP/1.1 400 "))
            assertTrue(post("/c/abcdefghjkmnpqrstuvw", sealedFor(local)).startsWith("HTTP/1.1 404 "))
            assertTrue(get("/c/$TOKEN").startsWith("HTTP/1.1 404 "))
            assertTrue(credentials.isEmpty)
            serving.cancelAndJoin()
        }

    @Test
    fun `a session posted with an address is taken only for the address it was sealed for`() =
        runBlocking {
            val (addresses, serving) = serving()

            // Someone on the LAN rewrote the address on its way: the session sealed for the real one does not open (#1029).
            val rewritten = post("/a/$TOKEN", "address=http%3A%2F%2F192.168.1.66%3A5055&" + sessionFor(SIGNING_IN_TO))
            assertTrue(rewritten, rewritten.startsWith("HTTP/1.1 400 "))
            assertTrue(rewritten.contains("form:en:true"))
            assertTrue(addresses.isEmpty)

            val answer = post("/a/$TOKEN", "address=http%3A%2F%2F192.168.1.10%3A5055&" + sessionFor(SIGNING_IN_TO))
            assertTrue(answer, answer.startsWith("HTTP/1.1 200 OK\r\n"))
            assertEquals(SIGNING_IN_TO, withTimeout(5_000) { addresses.receive() })
            assertEquals("s:phone", withTimeout(5_000) { sessions.receive() })
            serving.cancelAndJoin()
        }

    @Test
    fun `the code carries the key in a fragment, and the typed address does not`() {
        assertEquals("http://127.0.0.1:$port/a/$TOKEN#k=${key.encoded()}", listener.scanUrl)
        assertEquals("http://127.0.0.1:$port/a/$TOKEN", listener.url)
    }

    private fun assertConnectionRefused() {
        val refused =
            try {
                Socket(InetAddress.getLoopbackAddress(), port).close()
                false
            } catch (_: IOException) {
                true
            }
        assertTrue("the port still answers", refused)
    }

    /** Names what it was asked for, so a test can see which page was served and in which language. */
    private object FakePage : HandOffPage {
        override fun pin(
            acceptLanguage: String?,
            wrong: Boolean,
            locked: Boolean,
        ) = "pin:${pickLanguage(acceptLanguage)}:wrong=$wrong:locked=$locked"

        override fun form(
            acceptLanguage: String?,
            invalid: Boolean,
        ) = "form:${pickLanguage(acceptLanguage)}:$invalid"

        override fun status(
            acceptLanguage: String?,
            progress: HandOffProgress,
        ) = if (progress is HandOffProgress.Waiting) {
            form(
                acceptLanguage,
                invalid = false,
            )
        } else {
            "status:${pickLanguage(acceptLanguage)}:${if (progress is HandOffProgress.SignIn) "SignIn(${progress.server})" else progress}"
        }
    }
}
