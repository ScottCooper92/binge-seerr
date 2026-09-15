package io.github.scottcooper92.binge.seerr.seerr

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * The saved server's client reuses one pool, and a Seerr server hangs up an idle connection after
 * its own keep-alive. This covers the read that goes out on a connection the server has already
 * closed — #254.
 */
class SeerrIdleConnectionTest {
    private val server = MockWebServer()
    private val factory = SeerrApiFactory(logRequests = false)

    @Before
    fun start() {
        server.start()
    }

    @After
    fun tearDown() {
        factory.evict()
        server.close()
    }

    @Test
    fun `a read on a connection the server closed while idle still succeeds`() {
        // The server answers, then hangs up without saying so — what a keep-alive timeout looks like
        // to a client that has already pooled the connection.
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(COUNT_BODY)
                .onResponseEnd(SocketEffect.CloseSocket())
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(COUNT_BODY)
                .build(),
        )

        val api = factory.cached(server.url("/").toString(), SeerrAuth.ApiKey("key"))

        runBlocking {
            api.requestCount()
            api.requestCount()
        }
    }

    @Test
    fun `a read still succeeds when every pooled connection was closed while idle`() {
        // The pane opens several calls at once, so the pool fills; the server then hangs up on all of
        // them. OkHttp's default pool holds five, which is the number of failures #254 reports.
        repeat(POOL_SIZE) {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(COUNT_BODY)
                    .onResponseEnd(SocketEffect.CloseSocket())
                    .build(),
            )
        }
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(COUNT_BODY)
                .build(),
        )

        val api = factory.cached(server.url("/").toString(), SeerrAuth.ApiKey("key"))

        runBlocking {
            (1..POOL_SIZE).map { async { api.requestCount() } }.awaitAll()
            api.requestCount()
        }
    }

    /**
     * The fix for #254, and the only test here that fails without it. A Seerr server hangs up an
     * idle connection after 5 s, so the client has to retire its own first; OkHttp's default pool
     * holds one for five minutes and would hand the second call the stale connection.
     *
     * It sleeps because the pool evicts on elapsed time and there is no clock to advance. The wait
     * sits between our idle timeout and the server's keep-alive, which is the window the bug lives
     * in.
     */
    @Test
    fun `an idle connection is retired before a Seerr server would close it`() {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(COUNT_BODY)
                .build(),
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body(COUNT_BODY)
                .build(),
        )

        val api = factory.cached(server.url("/").toString(), SeerrAuth.ApiKey("key"))

        runBlocking { api.requestCount() }
        Thread.sleep(IDLE_WAIT_MILLIS)
        runBlocking { api.requestCount() }

        assertEquals(0, server.takeRequest().connectionIndex)
        assertEquals(1, server.takeRequest().connectionIndex)
    }

    private companion object {
        const val POOL_SIZE = 5

        /** Past the pool's 3 s idle timeout, short of the 5 s a Seerr server allows. */
        const val IDLE_WAIT_MILLIS = 3_500L

        const val COUNT_BODY =
            """{"total":0,"movie":0,"tv":0,"pending":0,"approved":0,"declined":0,"processing":0,"available":0}"""
    }
}
