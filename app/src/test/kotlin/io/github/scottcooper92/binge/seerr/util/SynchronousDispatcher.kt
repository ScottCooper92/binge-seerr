package io.github.scottcooper92.binge.seerr.util

import okhttp3.Dispatcher
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/**
 * A [Dispatcher] whose calls run on the calling thread instead of a pool.
 *
 * The ten files that drive a real `MockWebServer` cannot take [MainDispatcherRule] as-is: a call
 * that resumes on OkHttp's own thread after the coroutine that started it has been cancelled throws
 * once `Dispatchers.Main` is reset out from under it (#177). Running the call on the calling thread
 * removes the thread crossing rather than racing it — `Call.enqueue`'s callback fires before
 * `enqueue` itself returns, so `suspendCancellableCoroutine` resumes inline and nothing is left to
 * outlive the test. The local loopback round trip this still makes is real, not virtual, but it
 * finishes before the suspending call returns control either way.
 */
fun synchronousDispatcher(): Dispatcher = Dispatcher(SameThreadExecutorService)

/** Runs everything immediately on the calling thread; `shutdown` is tracked but changes nothing. */
private object SameThreadExecutorService : AbstractExecutorService() {
    @Volatile
    private var shutdown = false

    override fun execute(command: Runnable) = command.run()

    override fun shutdown() {
        shutdown = true
    }

    override fun shutdownNow(): MutableList<Runnable> {
        shutdown = true
        return mutableListOf()
    }

    override fun isShutdown() = shutdown

    override fun isTerminated() = shutdown

    override fun awaitTermination(
        timeout: Long,
        unit: TimeUnit,
    ) = true
}
