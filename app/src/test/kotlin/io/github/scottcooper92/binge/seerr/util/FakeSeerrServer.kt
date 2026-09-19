package io.github.scottcooper92.binge.seerr.util

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.Headers
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private const val DEFAULT_TAKE_TIMEOUT_MILLIS = 5_000L
private const val DEFAULT_AWAIT_IDLE_TIMEOUT_MILLIS = 2_000L
private const val AWAIT_IDLE_POLL_MILLIS = 1L
private const val AWAIT_IDLE_SETTLE_MILLIS = 50L

/**
 * An explicit port matters, not just a syntactically valid URL: [String.hasExplicitPort] is what a
 * portless address retries against [String.withDefaultSeerrPort] on, same as a real MockWebServer's
 * URL (bound to a real, explicit, random port) never triggered.
 */
private const val BASE_URL = "http://fake-seerr.test:8080/"

/** A canned answer for [FakeSeerrServer] — the in-memory stand-in for MockWebServer's `MockResponse`. */
data class FakeResponse(
    val code: Int = 200,
    val headers: Headers = headersOf(),
    val body: String = "",
)

/** One answered request — the in-memory stand-in for MockWebServer's `RecordedRequest`. */
data class FakeRequest(
    val method: String,
    val url: HttpUrl,
    val headers: Headers,
    val body: String,
)

/**
 * An in-memory transport for [SeerrApiFactory]/`plexTvApi`: every call is answered inside
 * [interceptor], with no socket and no server thread of its own, so a test built on this can take
 * [MainDispatcherRule] without a real connection outliving it (#337). OkHttp still answers each call
 * on its own dispatcher thread exactly as it always has — a response a test holds open (behind a
 * latch, in [dispatcher]) still leaves the test's own thread free to carry on, the same way it did
 * against a real MockWebServer.
 *
 * A request is answered from [dispatcher] when one is set — the path-scripted style most of these
 * tests use — otherwise from the next [enqueue]d response, the queued style a few of them do.
 * Neither set answers 404, as an un-scripted path did on the real server.
 */
class FakeSeerrServer {
    private val queued = ConcurrentLinkedQueue<FakeResponse>()
    private val toTake = LinkedBlockingQueue<FakeRequest>()
    private val all = CopyOnWriteArrayList<FakeRequest>()
    private val dispatchers = CopyOnWriteArrayList<Dispatcher>()

    /** Path-scripted routing, tried before the queue. [FakeRequest.url] carries the path and any query. */
    var dispatcher: ((FakeRequest) -> FakeResponse)? = null

    val requestCount: Int get() = all.size
    val requests: List<FakeRequest> get() = all

    fun url(path: String = "/"): String = BASE_URL

    fun enqueue(response: FakeResponse) {
        queued += response
    }

    /** Blocks for up to [timeoutMillis] for the next request; throws if none arrives, as MockWebServer's did. */
    fun takeRequest(timeoutMillis: Long = DEFAULT_TAKE_TIMEOUT_MILLIS): FakeRequest =
        toTake.poll(timeoutMillis, TimeUnit.MILLISECONDS) ?: error("no request received within ${timeoutMillis}ms")

    /**
     * A fresh [Dispatcher] for one client, tracked so [awaitIdle] can tell when it - and every
     * other client built against this server - has drained. Never shared across clients: a
     * [SeerrApiFactory] throwaway client's `release()` shuts its own dispatcher's executor down,
     * which would break every other client still using it if this one were pooled.
     */
    fun newDispatcher(): Dispatcher = Dispatcher().also { dispatchers += it }

    /**
     * Polls until every [newDispatcher] this server has handed out is idle - no running or
     * queued call - or [timeoutMillis] elapses, then holds for [AWAIT_IDLE_SETTLE_MILLIS] more.
     * Test-only (#177): OkHttp answers each call on its own thread even against this in-memory
     * transport, so a call a test never awaits directly (a fire-and-forget `launch`) can still be
     * resuming when `ViewModelStore.clear()` returns. Calling this from teardown, after clearing
     * the view models and before [MainDispatcherRule] resets Main, drains that work while Main is
     * still a dispatcher for it to land on.
     *
     * The settle wait covers a second hop this server's own idle count cannot see: Retrofit
     * resumes a call that ends in an exception - a non-2xx response, a cancellation - through
     * `Dispatchers.Default` rather than straight back from OkHttp's callback, so a client can
     * already read idle here while that redispatch is still in flight. It is a single, near-instant
     * hop with no I/O behind it, so a short fixed wait closes the gap in practice, even though
     * nothing this class can poll makes that a hard guarantee the way the dispatcher counts are.
     *
     * Best-effort: a timeout returns rather than throwing, so a genuinely stuck call fails the
     * test's own assertions instead of a teardown no test author asked to fail on.
     */
    fun awaitIdle(timeoutMillis: Long = DEFAULT_AWAIT_IDLE_TIMEOUT_MILLIS) {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (dispatchers.any { it.runningCallsCount() > 0 || it.queuedCallsCount() > 0 }) {
            if (System.nanoTime() >= deadlineNanos) return
            Thread.sleep(AWAIT_IDLE_POLL_MILLIS)
        }
        Thread.sleep(AWAIT_IDLE_SETTLE_MILLIS)
    }

    /** No server is running, so nothing to stop; kept so a teardown written for MockWebServer needs no other change. */
    fun close() = Unit

    /**
     * [cookieJar] replays what OkHttp's own `BridgeInterceptor` would have done had this call
     * reached the network — this interceptor sits in front of it instead, so that never runs.
     */
    fun interceptor(cookieJar: CookieJar = CookieJar.NO_COOKIES): Interceptor =
        Interceptor { chain ->
            val outgoing = chain.request()
            val toSend = cookieJar.loadForRequest(outgoing.url)
            val request =
                if (toSend.isEmpty()) {
                    outgoing
                } else {
                    outgoing.newBuilder().header("Cookie", toSend.joinToString("; ") { "${it.name}=${it.value}" }).build()
                }
            val recorded = FakeRequest(request.method, request.url, request.headers, request.bodyAsString())
            all += recorded
            toTake += recorded
            val answer = dispatcher?.invoke(recorded) ?: queued.poll() ?: FakeResponse(code = 404)
            Cookie.parseAll(request.url, answer.headers).takeIf { it.isNotEmpty() }?.let { cookieJar.saveFromResponse(request.url, it) }
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(answer.code)
                .message("")
                .headers(answer.headers)
                .body(answer.body.toResponseBody("application/json; charset=utf-8".toMediaType()))
                .build()
        }

    private fun Request.bodyAsString(): String = body?.let { requestBody -> Buffer().also { requestBody.writeTo(it) }.readUtf8() }.orEmpty()
}
