package io.github.scottcooper92.binge.seerr.util

import okhttp3.Dispatcher
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

private const val DEFAULT_AWAIT_IDLE_TIMEOUT_MILLIS = 2_000L
private const val AWAIT_IDLE_POLL_MILLIS = 1L
private const val AWAIT_IDLE_SETTLE_MILLIS = 50L

/**
 * The OkHttp dispatchers a test's clients run on, so its teardown can wait for every call to finish before
 * [MainDispatcherRule] resets Main (#177, #807). A fake server owns one and hands [newDispatcher] to the
 * `SeerrApiFactory` it builds clients with.
 */
class OkHttpDrain {
    private val dispatchers = CopyOnWriteArrayList<Dispatcher>()

    /**
     * A fresh [Dispatcher] for one client, tracked so [awaitIdle] can tell when it - and every
     * other client it was handed to - has drained. Never shared across clients: a
     * [SeerrApiFactory] throwaway client's `release()` shuts its own dispatcher's executor down,
     * which would break every other client still using it if this one were pooled.
     */
    fun newDispatcher(): Dispatcher = Dispatcher().also { dispatchers += it }

    /**
     * Polls until every [newDispatcher] this drain has handed out is idle - no running or
     * queued call - or [timeoutMillis] elapses, then holds for [AWAIT_IDLE_SETTLE_MILLIS] more.
     * Test-only (#177): OkHttp answers each call on its own thread, against a real socket or an
     * in-memory transport alike, so a call a test never awaits directly (a fire-and-forget `launch`) can still be
     * resuming when `ViewModelStore.clear()` returns. Calling this from teardown, after clearing
     * the view models and before [MainDispatcherRule] resets Main, drains that work while Main is
     * still a dispatcher for it to land on.
     *
     * The settle wait covers a second hop the dispatcher counts cannot see: Retrofit
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
}
