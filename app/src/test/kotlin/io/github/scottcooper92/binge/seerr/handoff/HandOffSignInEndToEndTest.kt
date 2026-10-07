package io.github.scottcooper92.binge.seerr.handoff

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

private const val TOKEN = "k7m2pqx4"

/**
 * The phone's client against the TV's real listener over loopback: the status it reads, the credentials it seals and
 * what the listener opens, with no stand-in on either side of the wire.
 */
class HandOffSignInEndToEndTest {
    private val key = HandOffKey.generate()
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val listener =
        AddressHandOffListener(
            server = server,
            token = TOKEN,
            url = "http://127.0.0.1:${server.localPort}/a/$TOKEN",
            page = HandOffPageTemplate(copyFor = { error("not asked for") }, appLink = "x"),
            key = key,
        )
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpTvSignInClient(OkHttpClient())

    @Volatile
    private var progress: HandOffProgress = HandOffProgress.Waiting
    private val received = Channel<HandOffCredentials>(Channel.UNLIMITED)

    private val target = TvHandOffTarget(host = "127.0.0.1", port = server.localPort, token = TOKEN, key = key)

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        listener.close()
    }

    private fun serve() = scope.launch { listener.serve({ progress }, { _, _ -> }, { received.trySend(it) }) }

    @Test
    fun `the phone reads the TV's progress, and sealed credentials reach the TV as the form held them`() =
        runBlocking {
            serve()
            assertEquals(HandOffStatus.WAITING, client.status(target)?.state)

            progress = HandOffProgress.SignIn("Living room", modes = listOf("Jellyfin"))
            assertEquals(HandOffStatus(HandOffStatus.SIGN_IN, "Living room", listOf("Jellyfin")), client.status(target))

            val credentials = HandOffCredentials(mode = "Jellyfin", username = "ana", password = "correct horse")
            assertEquals(1, client.send(target, credentials))

            assertEquals(credentials, withTimeout(5_000) { received.receive() })
        }

    @Test
    fun `a TV the phone cannot reach has no status, and a refusal is a failure to send`() =
        runBlocking {
            serve()
            progress = HandOffProgress.SignIn("Home", modes = listOf("Local"))

            assertNull(client.send(target, HandOffCredentials(mode = "Jellyfin", username = "ana", password = "x")))
            assertTrue(received.isEmpty)
            assertNull(client.status(target.copy(port = 1)))
        }

    @Test
    fun `a target without a key sends nothing`() =
        runBlocking {
            serve()
            progress = HandOffProgress.SignIn("Home", modes = listOf("Local"))

            assertNull(client.send(target.copy(key = null), HandOffCredentials(mode = "Local", email = "a@b.c", password = "x")))
            assertTrue(received.isEmpty)
        }
}
