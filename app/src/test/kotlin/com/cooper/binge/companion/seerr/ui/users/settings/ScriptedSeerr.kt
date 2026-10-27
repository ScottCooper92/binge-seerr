package com.cooper.binge.companion.seerr.ui.users.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.cooper.binge.companion.seerr.auth.CredentialStore
import com.cooper.binge.companion.seerr.auth.SeerrConnection
import com.cooper.binge.companion.seerr.seerr.SeerrApiFactory
import com.cooper.binge.companion.seerr.seerr.SeerrAuth
import com.cooper.binge.companion.seerr.util.OkHttpDrain
import com.cooper.binge.companion.seerr.util.PlainCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.Headers.Companion.headersOf
import org.junit.rules.TemporaryFolder
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal const val ADMIN = 2
internal const val MANAGE_USERS = 1 shl 3
internal const val MANAGE_REQUESTS = 1 shl 4
internal const val REQUEST = 1 shl 5

private const val REQUEST_WAIT_MILLIS = 2_000L
private const val EXPECTED_CALL_WAIT_MILLIS = 15_000L
private const val POLL_MILLIS = 10L

/** How long a held answer waits for its release before giving up on its own, so a stuck test still ends. */
private const val HOLD_TIMEOUT_MILLIS = 30_000L

private const val EMPTY_PAGE = """{"pageInfo":{"pages":0,"results":0},"results":[]}"""

/**
 * A path-scripted Seerr for the settings pages: each `METHOD path` answers with the body last
 * served for it, so a test can switch a record after a write. The clients [connection] builds run
 * on a [drain], and [close] waits for their calls before stopping the server. Tests call it from
 * `@After`, which runs before `MainDispatcherRule` resets Main, so a call still in flight lands on
 * a live Main and does not leak into the next test as `UncaughtExceptionsBeforeTest` (#807).
 */
internal class ScriptedSeerr(
    private val folder: TemporaryFolder,
) {
    val server = MockWebServer()
    val received = CopyOnWriteArrayList<RecordedRequest>()
    private val drain = OkHttpDrain()

    /** A fresh client dispatcher on this server's drain, for a test that builds its own `SeerrApiFactory`. */
    fun newDispatcher(): okhttp3.Dispatcher = drain.newDispatcher()

    /** Written on the test's thread and read on MockWebServer's connection threads, so it is a concurrent map (#1539). */
    private val responses = ConcurrentHashMap<String, (RecordedRequest) -> MockResponse>()
    private val holds = CopyOnWriteArrayList<HeldResponse>()
    private var stores = 0

    /** While set, every request is held this long and then dropped; see [unreachable]. */
    @Volatile private var dropAfterMillis: Long? = null

    fun start() {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    dropAfterMillis?.let { millis ->
                        Thread.sleep(millis)
                        return MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()
                    }
                    return responses[request.method + " " + request.url.encodedPath]?.invoke(request) ?: MockResponse(code = 404)
                }
            }
        server.start()
    }

    fun close() {
        // A hold the test never released would keep its call, and so the drain, waiting for ever.
        holds.forEach { it.release(code = 503) }
        drain.awaitIdle()
        server.close()
    }

    /** Answers [key] with [body]; [setCookie] adds a `Set-Cookie`, as a sign-in's answer carries the session. */
    fun serve(
        key: String,
        body: String = "{}",
        code: Int = 200,
        setCookie: String? = null,
    ) {
        val json = arrayOf("Content-Type", "application/json")
        val headers = if (setCookie == null) headersOf(*json) else headersOf(*json, "Set-Cookie", setCookie)
        responses[key] = { MockResponse(code = code, headers = headers, body = body) }
    }

    /**
     * Answers from the request itself, for a route whose response depends on what was asked.
     *
     * [delayMillis] holds the answer back, so a test can have a second call arrive and queue while
     * this one is still in flight.
     */
    fun serveFrom(
        key: String,
        delayMillis: Long = 0,
        body: (RecordedRequest) -> String,
    ) {
        responses[key] = { request ->
            MockResponse
                .Builder()
                .code(200)
                .headers(headersOf("Content-Type", "application/json"))
                .body(body(request))
                .headersDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()
        }
    }

    /**
     * Holds [key]'s answers until the test calls [HeldResponse.release], so it can act while a call is in flight and pin
     * the moment that call ends; a fixed delay races `runTest`'s virtual time and cannot (#950). [count] and [awaitCount]
     * see a held request as soon as it arrives. Requests after the release are answered at once with what it gave.
     */
    fun serveHeld(key: String): HeldResponse =
        HeldResponse().also { held ->
            holds += held
            responses[key] = { held.answer() }
        }

    /** Answers an offset-paged endpoint: the body for the page the request's `skip` lands in. */
    fun servePages(
        key: String,
        pageSize: Int,
        bodies: List<String>,
    ) {
        responses[key] = { request ->
            val page = (request.url.queryParameter("skip")?.toIntOrNull() ?: 0) / pageSize
            val body = bodies.getOrElse(page) { EMPTY_PAGE }
            MockResponse(code = 200, headers = headersOf("Content-Type", "application/json"), body = body)
        }
    }

    /**
     * The server stops answering: each request is held for [afterMillis], then its connection is closed, which
     * the client reads as unreachable. The hold is what lets a test watch a re-check while it is in flight.
     */
    fun unreachable(afterMillis: Long = 0) {
        dropAfterMillis = afterMillis
    }

    fun remove(key: String) {
        responses.remove(key)
    }

    /** The viewer and the server every page reads first. */
    fun viewer(
        id: Int,
        permissions: Int,
        version: String = "3.4.0",
        settings: String = """{"mediaServerType":2,"localLogin":true}""",
    ) {
        serve("GET /api/v1/auth/me", """{"id":$id,"displayName":"Viewer","permissions":$permissions}""")
        serve("GET /api/v1/status", """{"version":"$version"}""")
        serve("GET /api/v1/settings/public", settings)
    }

    fun body(
        method: String,
        path: String,
    ): String =
        received
            .last { it.method == method && it.url.encodedPath == path }
            .body
            ?.utf8()
            .orEmpty()

    fun count(
        method: String,
        path: String,
    ): Int = received.count { it.method == method && it.url.encodedPath == path }

    /**
     * Waits, on real time, until every call this server's clients made has run its callback. Under the unconfined test
     * dispatcher a view model's work after a read (a `setJobs` and the schedule it computes) runs inside that callback on
     * OkHttp's thread, so a test that moves a fake clock waits here first, or the view model reads the clock already
     * moved (#1226).
     */
    fun awaitCallbacks() = drain.awaitIdle()

    /**
     * A request a refresh triggers lands on OkHttp's threads after the call that triggered it has
     * returned, so a count read on the next line races it; this waits for it in real time. A wait for a call that is
     * expected passes [expectedCallWaitMillis]: the default is short, and on a starved machine a slow call, not a missing
     * one, is the likelier reason to run out of it.
     */
    suspend fun awaitCount(
        method: String,
        path: String,
        moreThan: Int,
        timeoutMillis: Long = REQUEST_WAIT_MILLIS,
    ) = withContext(Dispatchers.Default) {
        withTimeout(timeoutMillis) { while (count(method, path) <= moreThan) delay(POLL_MILLIS) }
    }

    /**
     * [awaitCount] without letting time pass: it blocks the test's thread, so `runTest` cannot move virtual time on while
     * it waits and a later scheduled call cannot stand in for the one awaited. Returns whether the count got past
     * [moreThan], so a test can assert that a call is made, or that none is.
     *
     * [pump] runs on each poll, so a test can let its scheduler run what is already due (never moving time on) while it
     * waits: a call the view model makes first suspends on work the test's own scheduler owns, such as the credential
     * store, and a blocked thread alone would never let that run. [timeoutMillis] is how long to wait for a call that is
     * expected. A test asserting that none comes keeps the short default: waiting longer only slows it.
     */
    fun awaitCountHoldingTime(
        method: String,
        path: String,
        moreThan: Int,
        timeoutMillis: Long = REQUEST_WAIT_MILLIS,
        pump: () -> Unit = {},
    ): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (count(method, path) <= moreThan && System.nanoTime() < deadline) {
            pump()
            Thread.sleep(POLL_MILLIS)
        }
        return count(method, path) > moreThan
    }

    /** How long a test waits for a call it expects, where a slow machine, not the code, is the likely reason for a miss. */
    internal fun expectedCallWaitMillis(): Long = EXPECTED_CALL_WAIT_MILLIS

    suspend fun connection(
        scope: TestScope,
        quickConnectPollInterval: Duration = 10.milliseconds,
        onServerChanged: suspend () -> Unit = {},
        apis: SeerrApiFactory = SeerrApiFactory(logRequests = false, testDispatcher = drain::newDispatcher),
        host: String? = null,
    ): SeerrConnection {
        val connection =
            SeerrConnection(
                store =
                    CredentialStore(
                        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { folder.newFile("d${stores++}.preferences_pb") },
                        PlainCipher,
                    ),
                apis = apis,
                quickConnectPollInterval = quickConnectPollInterval,
                onServerChanged = onServerChanged,
            )
        val address = server.url("/").let { url -> if (host == null) url else url.newBuilder().host(host).build() }
        connection.connect(address.toString(), SeerrAuth.ApiKey("k3y")).getOrThrow()
        return connection
    }
}

/**
 * A connection to this server under a name on the local network, with the local-network permission refused (#1507): it
 * connects, because setup's probe is not gated, and then every call a screen makes is turned away the way Android would.
 * [testDns] sends the name to the scripted server, so nothing but the gate stands between a screen and its answer.
 */
internal suspend fun ScriptedSeerr.deniedConnection(scope: TestScope): SeerrConnection =
    connection(
        scope,
        apis =
            SeerrApiFactory(
                logRequests = false,
                localNetwork = { false },
                testDns = { listOf(InetAddress.getLoopbackAddress()) },
                testDispatcher = ::newDispatcher,
            ),
        host = "seerr.lan",
    )

/** An answer [ScriptedSeerr.serveHeld] keeps back until [release]. */
internal class HeldResponse {
    private val released = CountDownLatch(1)

    @Volatile private var code = 200

    @Volatile private var body = "{}"

    /** Lets every held request, and any later one, through with [body] and [code]; a 5xx [code] fails the call. */
    fun release(
        body: String = "{}",
        code: Int = 200,
    ) {
        if (released.count == 0L) return
        this.body = body
        this.code = code
        released.countDown()
    }

    /** Runs on MockWebServer's thread for the request's connection, so blocking here holds only that call. */
    internal fun answer(): MockResponse {
        released.await(HOLD_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        return MockResponse(code = code, headers = headersOf("Content-Type", "application/json"), body = body)
    }
}
