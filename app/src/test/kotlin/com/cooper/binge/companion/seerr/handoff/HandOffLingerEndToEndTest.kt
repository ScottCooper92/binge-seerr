package com.cooper.binge.companion.seerr.handoff

import androidx.test.core.app.ApplicationProvider
import com.cooper.binge.companion.seerr.telemetry.DiscardLogger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

private const val TOKEN = "k7m2pqx4"

/**
 * The phone's page against a TV that signed in and left its setup at once (#2475): the real listener, the real client, and the
 * linger that keeps the listener answering "Connected" after the screen that owned it is gone, then lets it go.
 */
@RunWith(RobolectricTestRunner::class)
class HandOffLingerEndToEndTest {
    private val key = HandOffKey.generate()
    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val listener =
        AddressHandOffListener(
            server = server,
            token = TOKEN,
            url = "http://127.0.0.1:${server.localPort}/a/$TOKEN",
            page = HandOffPageTemplate(copyFor = { error("not asked for") }, appLink = "x"),
            key = key,
            dispatcher = Dispatchers.IO,
        )
    private val screenScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineExceptionHandler { _, e -> uncaught += e })
    private val client = OkHttpTvSignInClient(OkHttpClient(), Dispatchers.IO, DiscardLogger)
    private val target = TvHandOffTarget(host = "127.0.0.1", port = server.localPort, token = TOKEN, key = key)

    @After
    fun tearDown() {
        runBlocking {
            screenScope.coroutineContext.job.cancelAndJoin()
            appScope.coroutineContext.job.cancelAndJoin()
        }
        listener.close()
    }

    private fun lanHandOffs() = LanAddressHandOffs(ApplicationProvider.getApplicationContext(), appScope, Dispatchers.IO)

    @Test
    fun `a phone still reads Connected from a TV whose setup has gone, until the linger ends`() =
        runBlocking {
            val serving = screenScope.launch { listener.serve({ HandOffProgress.Connected }, { _, _ -> }, {}) }
            delay(100)
            assertEquals(HandOffStatus.CONNECTED, client.status(target)?.state)

            // The home replaces the setup: its scope goes, and the listener is handed on instead of closed.
            serving.cancelAndJoin()
            lanHandOffs().linger(listener, 1_500.milliseconds)
            delay(300)

            assertEquals("the phone's next poll still reads it", HandOffStatus.CONNECTED, client.status(target)?.state)

            delay(2_500)
            assertNull("then the listener is gone", client.status(target))
        }

    @Test
    fun `a listener already closed when the linger starts is let go quietly, not thrown into the app scope`() =
        runBlocking {
            val closed = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            val session = ClosedUnderUs(closed)

            lanHandOffs().linger(session, 1_000.milliseconds)
            delay(500)

            assertEquals("nothing reached the scope's uncaught handler", emptyList<Throwable>(), uncaught.toList())
            assertEquals("and the listener was let go", true, session.closedByLinger)
        }

    @Test
    fun `a listener closed with the screen leaves the phone no answer at all`() =
        runBlocking {
            val serving = screenScope.launch { listener.serve({ HandOffProgress.Connected }, { _, _ -> }, {}) }
            delay(100)
            serving.cancelAndJoin()
            listener.close()

            assertNull(client.status(target))
        }

    /** A listener whose socket is gone: serving it throws, as a closed one does. */
    private class ClosedUnderUs(
        private val server: ServerSocket,
    ) : AddressHandOffSession {
        override val url = "http://127.0.0.1/a/x"
        override val scanUrl = "http://127.0.0.1/a/x"
        override val pin: String? = null

        @Volatile
        var closedByLinger = false

        override suspend fun serve(
            progress: () -> HandOffProgress,
            onAddress: (address: String, session: String?) -> Unit,
            onCredentials: (HandOffCredentials) -> Unit,
        ) {
            server.close()
            throw SocketException("Socket closed")
        }

        override fun close() {
            closedByLinger = true
        }
    }
}
