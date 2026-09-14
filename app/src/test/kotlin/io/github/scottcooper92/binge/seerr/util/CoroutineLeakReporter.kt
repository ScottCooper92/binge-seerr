package io.github.scottcooper92.binge.seerr.util

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import java.io.File
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Every line this reporter writes starts here, so one grep finds the lot. */
internal const val LEAK_MARKER = "[coroutine-leak]"

/** Where the report is written, if the test task named one. */
internal const val LEAK_LOG_PROPERTY = "binge.coroutineLeakLog"

/**
 * Names the coroutine behind an uncaught exception, which nothing else in the suite can.
 *
 * `kotlinx-coroutines-test` stashes an exception that arrives while no test is running and rethrows
 * it at the next `runTest` as `UncaughtExceptionsBeforeTest` — a class declared with a no-argument
 * constructor, so it carries no message and no cause by construction. The class and line it names
 * are wherever the next test happened to be, not where the work came from (#177).
 *
 * This is registered as a `kotlinx.coroutines.CoroutineExceptionHandler` service, which
 * `handleCoroutineExceptionImpl` calls before falling back to the thread's handler, so it sees the
 * real throwable and the real context. It logs and returns rather than throwing
 * `ExceptionSuccessfullyProcessed`, so the exception carries on to the test framework exactly as
 * before: this reports, it does not swallow.
 */
class CoroutineLeakReporter :
    AbstractCoroutineContextElement(CoroutineExceptionHandler),
    CoroutineExceptionHandler {
    override fun handleException(
        context: CoroutineContext,
        exception: Throwable,
    ) {
        val report = describeLeak(Thread.currentThread().name, context, exception)
        System.err.println(report)
        System.getProperty(LEAK_LOG_PROPERTY)?.let { path ->
            val file = File(path)
            file.parentFile?.mkdirs()
            file.appendText(report + "\n")
        }
    }
}

/**
 * The whole throwable under the coroutine and thread it escaped from. [Throwable.stackTraceToString]
 * already walks the `Caused by` chain and everything suppressed onto it, which is the part
 * `UncaughtExceptionsBeforeTest` cannot carry.
 */
internal fun describeLeak(
    threadName: String,
    context: CoroutineContext,
    exception: Throwable,
): String =
    buildString {
        appendLine("$LEAK_MARKER uncaught in ${context[CoroutineName]?.name ?: "an unnamed coroutine"} on thread $threadName")
        appendLine("$LEAK_MARKER job: ${context[Job] ?: "none"}")
        exception
            .stackTraceToString()
            .trimEnd()
            .lineSequence()
            .forEach { appendLine("$LEAK_MARKER $it") }
    }
