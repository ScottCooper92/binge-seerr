package com.cooper.binge.companion.seerr.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import okhttp3.Dispatcher
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val DEFAULT_AWAIT_IDLE_TIMEOUT_MILLIS = 2_000L
private const val AWAIT_IDLE_POLL_MILLIS = 1L

/** `Dispatchers.Default` runs on at least two workers, however few cores the machine has. */
private const val MIN_DEFAULT_WORKERS = 2

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
     * Waits until every [newDispatcher] this drain has handed out is idle - no running or queued call - and the
     * `Dispatchers.Default` pool has been flushed behind it, or [timeoutMillis] elapses. Test-only (#177): OkHttp answers
     * each call on its own thread, against a real socket or an in-memory transport alike, so a call a test never awaits
     * directly (a fire-and-forget `launch`) can still be resuming when `ViewModelStore.clear()` returns. Calling this from
     * teardown, after clearing the view models and before [MainDispatcherRule] resets Main, drains that work while Main is
     * still a dispatcher for it to land on.
     *
     * The flush covers a second hop the dispatcher counts cannot see: Retrofit resumes a call that ends in an exception -
     * a non-2xx response, a cancellation - through `Dispatchers.Default` rather than straight back from OkHttp's callback,
     * so a client can already read idle here while that redispatch is still queued (#1678). That task is inside Retrofit
     * and nothing here can observe it, so this is not a happens-before: it is deterministic in practice. [flushDefaultPool]
     * parks one task on every Default worker, which cannot all be in place until each worker has come back to its run loop
     * and taken the work queued for it first. It then looks again, since a resumed continuation can start another call.
     *
     * A timeout returns rather than throwing, so a genuinely stuck call fails the test's own assertions instead of a
     * teardown no test author asked to fail on, but it says so on stderr: which wait ran out, and how many Default
     * workers had checked in, so a flake that comes back can be read from its log.
     */
    fun awaitIdle(timeoutMillis: Long = DEFAULT_AWAIT_IDLE_TIMEOUT_MILLIS) {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        do {
            while (busy()) {
                if (System.nanoTime() >= deadlineNanos) return timedOut("a call still running or queued", timeoutMillis)
                Thread.sleep(AWAIT_IDLE_POLL_MILLIS)
            }
            val remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime()).coerceAtLeast(1)
            if (!flushDefaultPool(remainingMillis)) return
        } while (busy())
    }

    private fun busy() = dispatchers.any { it.runningCallsCount() > 0 || it.queuedCallsCount() > 0 }

    /** True once a task has been parked on every Default worker at the same moment, false (and logged) when it ran out of time. */
    private fun flushDefaultPool(timeoutMillis: Long): Boolean {
        val workers = Runtime.getRuntime().availableProcessors().coerceAtLeast(MIN_DEFAULT_WORKERS)
        val checkedIn = CountDownLatch(workers)
        val release = CountDownLatch(1)
        repeat(workers) {
            Dispatchers.Default.asExecutor().execute {
                checkedIn.countDown()
                release.await(timeoutMillis, TimeUnit.MILLISECONDS)
            }
        }
        val allArrived = checkedIn.await(timeoutMillis, TimeUnit.MILLISECONDS)
        release.countDown()
        if (!allArrived) timedOut("the Default pool flush, ${workers - checkedIn.count} of $workers workers checked in", timeoutMillis)
        return allArrived
    }

    private fun timedOut(
        what: String,
        timeoutMillis: Long,
    ) = System.err.println("OkHttpDrain.awaitIdle timed out after ${timeoutMillis}ms waiting on $what")
}
