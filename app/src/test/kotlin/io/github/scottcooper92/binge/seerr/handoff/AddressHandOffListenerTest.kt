package io.github.scottcooper92.binge.seerr.handoff

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
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
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val port = server.localPort
    private val listener =
        AddressHandOffListener(
            server = server,
            token = TOKEN,
            url = "http://127.0.0.1:$port/a/$TOKEN",
            page = FakePage,
            limits = HandOffLimits(readTimeoutMillis = 300, requestDeadlineMillis = 1_000, drainDeadlineMillis = 500),
        )

    @After
    fun tearDown() = listener.close()

    private fun CoroutineScope.listening(): Deferred<String> = async(Dispatchers.IO) { listener.awaitAddress() }

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
            val waiting = listening()

            val answer = get("/a/$TOKEN")

            assertTrue(answer.startsWith("HTTP/1.1 200 OK\r\n"))
            assertTrue(answer.contains("form:es:false"))
            assertTrue(answer.contains("Cache-Control: no-store"))
            assertTrue(answer.contains("Referrer-Policy: no-referrer"))
            assertTrue(answer.contains("Connection: close"))
            assertFalse(waiting.isCompleted)
            waiting.cancelAndJoin()
        }

    @Test
    fun `a wrong token, another path or another method gets a bare 404`() =
        runBlocking {
            val waiting = listening()

            for (answer in listOf(get("/a/abcdefghijklmnopqrstuw"), get("/"), get("/a/$TOKEN/x"), get("/favicon.ico"))) {
                assertTrue(answer, answer.startsWith("HTTP/1.1 404 Not Found\r\n"))
                assertFalse(answer.contains("form:"))
            }
            assertTrue(exchange("DELETE /a/$TOKEN HTTP/1.1\r\n\r\n").startsWith("HTTP/1.1 404 "))
            assertFalse(waiting.isCompleted)
            waiting.cancelAndJoin()
        }

    @Test
    fun `a posted address is accepted once, and the listener is done`() =
        runBlocking {
            val waiting = listening()

            val answer = post("/a/$TOKEN", "address=http%3A%2F%2F192.168.1.10%3A5055&extra=ignored")

            assertTrue(answer.startsWith("HTTP/1.1 200 OK\r\n"))
            assertTrue(answer.contains("sent:en"))
            assertEquals("http://192.168.1.10:5055", withTimeout(5_000) { waiting.await() })
            listener.close()
            assertConnectionRefused()
        }

    @Test
    fun `an address the TV cannot use gets the form back with an error, and the listener keeps going`() =
        runBlocking {
            val waiting = listening()

            assertTrue(post("/a/$TOKEN", "address=http%3A%2F%2F").startsWith("HTTP/1.1 400 "))
            assertTrue(post("/a/$TOKEN", "address=").contains("form:en:true"))
            assertTrue(post("/a/$TOKEN", "nothing=here").contains("form:en:true"))
            assertTrue(post("/a/$TOKEN", "address=%zz").contains("form:en:true"))
            assertFalse(waiting.isCompleted)

            post("/a/$TOKEN", "address=seerr.lan")
            assertEquals("seerr.lan", withTimeout(5_000) { waiting.await() })
        }

    @Test
    fun `oversized and malformed requests are refused before they are read`() =
        runBlocking {
            val waiting = listening()

            assertTrue(exchange("GET /a/$TOKEN HTTP/1.1\r\nX: ${"a".repeat(9_000)}\r\n\r\n").startsWith("HTTP/1.1 431 "))
            val manyHeaders = (1..60).joinToString("") { "X-$it: y\r\n" }
            assertTrue(exchange("GET /a/$TOKEN HTTP/1.1\r\n$manyHeaders\r\n").startsWith("HTTP/1.1 431 "))
            assertTrue(exchange("POST /a/$TOKEN HTTP/1.1\r\nContent-Length: 100000\r\n\r\n").startsWith("HTTP/1.1 413 "))
            assertTrue(exchange("POST /a/$TOKEN HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n").startsWith("HTTP/1.1 400 "))
            assertTrue(exchange("POST /a/$TOKEN HTTP/1.1\r\nContent-Length: -1\r\n\r\n").startsWith("HTTP/1.1 400 "))
            assertTrue(exchange("garbage\r\n\r\n").startsWith("HTTP/1.1 400 "))
            assertTrue(exchange("GET /a/$TOKEN SPDY/3\r\n\r\n").startsWith("HTTP/1.1 400 "))
            assertTrue(exchange("GET /a/$TOKEN HTTP/1.1\r\nno colon\r\n\r\n").startsWith("HTTP/1.1 400 "))
            assertFalse(waiting.isCompleted)
            waiting.cancelAndJoin()
        }

    @Test
    fun `a client that sends nothing is dropped after the read timeout, and the next one is served`() =
        runBlocking {
            val waiting = listening()

            val silent = Socket(InetAddress.getLoopbackAddress(), port)
            silent.soTimeout = 5_000
            // The listener gives up on the read and closes without answering.
            assertEquals(-1, silent.getInputStream().read())
            silent.close()

            assertTrue(get("/a/$TOKEN").startsWith("HTTP/1.1 200 "))
            waiting.cancelAndJoin()
        }

    @Test
    fun `a refused client that trickles bytes cannot hold the listener past the drain deadline`() =
        runBlocking {
            val waiting = listening()
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
            waiting.cancelAndJoin()
        }

    @Test
    fun `cancelling stops the listener without an address`() =
        runBlocking {
            val waiting = listening()

            withTimeout(2_000) { waiting.cancelAndJoin() }

            assertTrue(waiting.isCancelled)
            listener.close()
            assertConnectionRefused()
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

        override fun sent(acceptLanguage: String?) = "sent:${pickLanguage(acceptLanguage)}"
    }
}
