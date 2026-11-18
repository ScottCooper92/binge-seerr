package com.cooper.binge.companion.seerr.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.launch
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val ITERATIONS = 200
private const val SERVER_WAIT_MILLIS = 5_000L
private const val HELD_MILLIS = 30_000L
private const val BUSY_MILLIS = 150L

private interface Ping {
    @GET("ping")
    suspend fun ping()
}

/**
 * [OkHttpDrain.awaitIdle] must not return while a call a test never awaited is still on its way back (#1678): a call that
 * ends in an exception resumes through a second hop on `Dispatchers.Default`, after OkHttp's dispatcher already reads idle.
 */
class OkHttpDrainTest {
    private val server = MockWebServer()
    private val drain = OkHttpDrain()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private lateinit var ping: Ping

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().dispatcher(drain.newDispatcher()).build()
        ping =
            Retrofit
                .Builder()
                .baseUrl(server.url("/"))
                .client(client)
                .build()
                .create(Ping::class.java)
    }

    @After
    fun tearDown() {
        drain.awaitIdle()
        server.close()
    }

    @Test
    fun `a call that fails has resumed by the time the drain returns, every time`() {
        repeat(ITERATIONS) { round ->
            server.enqueue(MockResponse(code = 503))
            val resumed = AtomicBoolean(false)
            scope.launch {
                try {
                    ping.ping()
                } catch (_: HttpException) {
                    // The failure is the point: Retrofit throws it from the Default hop.
                }
                resumed.set(true)
            }

            drain.awaitIdle()

            assertTrue("round $round: the failed call had not resumed when the drain returned", resumed.get())
        }
    }

    /** The flush's own contract, which the Retrofit hop is one case of: work already on the Default pool is waited for. */
    @Test
    fun `work already running on the Default pool is waited for`() {
        val finished = AtomicBoolean(false)
        val started = CountDownLatch(1)
        Dispatchers.Default.asExecutor().execute {
            started.countDown()
            Thread.sleep(BUSY_MILLIS)
            finished.set(true)
        }
        assertTrue("the Default task never started", started.await(SERVER_WAIT_MILLIS, TimeUnit.MILLISECONDS))

        drain.awaitIdle()

        assertTrue("the drain returned while a Default worker was still busy", finished.get())
    }

    @Test
    fun `a call cancelled in flight is finished with when the drain returns`() {
        server.enqueue(MockResponse.Builder().headersDelay(HELD_MILLIS, TimeUnit.MILLISECONDS).build())
        val job: Job = scope.launch { ping.ping() }
        assertTrue("the held request never reached the server", server.takeRequest(SERVER_WAIT_MILLIS, TimeUnit.MILLISECONDS) != null)

        job.cancel()
        drain.awaitIdle()

        assertTrue("the cancelled call was still running when the drain returned", job.isCompleted && job.isCancelled)
    }
}
