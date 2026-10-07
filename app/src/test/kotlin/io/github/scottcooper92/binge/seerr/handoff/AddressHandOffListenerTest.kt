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

    private fun serving(): Pair<Channel<String>, Job> {
        val addresses = Channel<String>(Channel.UNLIMITED)
        return addresses to
            serverScope.launch {
                listener.serve({ progress }, { address, _ -> addresses.trySend(address) }, { credentials.trySend(it) })
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

    private fun get(path: String) = exchange("GET $path HTTP/1.1\r\nHost: tv\r\nAccept-Language: es-MX,en;q=0.5\r\n\r\n")

    private fun post(
        path: String,
        body: String,
    ) = exchange(
        "POST $path HTTP/1.1\r\nHost: tv\r\nContent-Type: application/x-www-form-urlencoded\r\n" +
            "Content-Length: ${body.toByteArray().size}\r\n\r\n$body",
    )

    @Test
    fun `the token's path serves the form, in the browser's language, with headers that keep it to itself`() =
        runBlocking {
            val (addresses, serving) = serving()

            val answer = get("/a/$TOKEN")

            assertTrue(answer.startsWith("HTTP/1.1 200 OK\r\n"))
            assertTrue(answer.contains("form:es:false"))
            assertTrue(answer.contains("Cache-Control: no-store"))
            assertTrue(answer.contains("Referrer-Policy: no-referrer"))
            assertTrue(answer.contains("Connection: close"))
            assertTrue(addresses.isEmpty)
            serving.cancelAndJoin()
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
            post("/a/$TOKEN", "address=seerr.lan")
            assertEquals("seerr.lan", withTimeout(5_000) { addresses.receive() })

            progress = HandOffProgress.SignIn("Living room")
            get("/a/$TOKEN").let { assertTrue(it, it.contains("status:es:SignIn(Living room)")) }
            post("/a/$TOKEN", "address=other.lan").let { assertTrue(it, it.contains("status:en:SignIn(Living room)")) }
            assertTrue(addresses.isEmpty)

            progress = HandOffProgress.Connected
            get("/a/$TOKEN").let { assertTrue(it, it.contains("status:es:Connected")) }
            serving.cancelAndJoin()
        }

    @Test
    fun `an address that named no server is taken again, from the form the page goes back to`() =
        runBlocking {
            val (addresses, serving) = serving()
            post("/a/$TOKEN", "address=nope.lan")
            assertEquals("nope.lan", withTimeout(5_000) { addresses.receive() })

            progress = HandOffProgress.Failed
            assertTrue(get("/a/$TOKEN").contains("status:es:Failed"))
            post("/a/$TOKEN", "address=seerr.lan")

            assertEquals("seerr.lan", withTimeout(5_000) { addresses.receive() })
            serving.cancelAndJoin()
        }

    @Test
    fun `an address the TV cannot use gets the form back with an error, and the listener keeps going`() =
        runBlocking {
            val (addresses, serving) = serving()

            assertTrue(post("/a/$TOKEN", "address=http%3A%2F%2F").startsWith("HTTP/1.1 400 "))
            assertTrue(post("/a/$TOKEN", "address=").contains("form:en:true"))
            assertTrue(post("/a/$TOKEN", "nothing=here").contains("form:en:true"))
            assertTrue(post("/a/$TOKEN", "address=%zz").contains("form:en:true"))
            assertTrue(addresses.isEmpty)

            post("/a/$TOKEN", "address=seerr.lan")
            assertEquals("seerr.lan", withTimeout(5_000) { addresses.receive() })
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
        context: String = TOKEN,
    ) = "sealed=" + using.seal(Json.encodeToString(credentials).toByteArray(), context)

    private val local = HandOffCredentials(mode = "Local", email = "ana@example.com", password = "correct horse")

    @Test
    fun `the status route reports the TV's progress as JSON, for the token's path only`() =
        runBlocking {
            val (_, serving) = serving()

            progress = HandOffProgress.SignIn("Living room", modes = listOf("Local", "Jellyfin"), failed = true)
            val answer = get("/s/$TOKEN")

            assertTrue(answer.startsWith("HTTP/1.1 200 OK\r\n"))
            assertTrue(answer.contains("Content-Type: application/json"))
            assertEquals(
                HandOffStatus(HandOffStatus.SIGN_IN, "Living room", listOf("Local", "Jellyfin"), failed = true),
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
            progress = HandOffProgress.SignIn("Home", modes = listOf("Jellyfin"))
            assertTrue(post("/c/$TOKEN", sealedFor(local)).startsWith("HTTP/1.1 400 "))
            assertTrue(credentials.isEmpty)

            progress = HandOffProgress.SignIn("Home", modes = listOf("Local"), attempt = 4)
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
            progress = HandOffProgress.SignIn("Home", modes = listOf("Local"))

            assertTrue(post("/c/$TOKEN", sealedFor(local, using = HandOffKey.generate())).startsWith("HTTP/1.1 400 "))
            assertTrue(post("/c/$TOKEN", sealedFor(local, context = "other000")).startsWith("HTTP/1.1 400 "))
            assertTrue(post("/c/$TOKEN", "sealed=garbage").startsWith("HTTP/1.1 400 "))
            assertTrue(post("/c/$TOKEN", "password=correct+horse&email=ana%40example.com&mode=Local").startsWith("HTTP/1.1 400 "))
            assertTrue(post("/c/abcdefghjkmnpqrstuvw", sealedFor(local)).startsWith("HTTP/1.1 404 "))
            assertTrue(get("/c/$TOKEN").startsWith("HTTP/1.1 404 "))
            assertTrue(credentials.isEmpty)
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
