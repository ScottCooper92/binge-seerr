package io.github.scottcooper92.binge.seerr.util

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ServiceLoader

class CoroutineLeakReporterTest {
    @Test
    fun `the report names the coroutine, the job and the thread`() {
        val job = Job()
        val report = describeLeak("Test worker", CoroutineName("hub-poll") + job, IllegalStateException("boom"))

        assertTrue(report, report.contains("hub-poll"))
        assertTrue(report, report.contains("Test worker"))
        assertTrue(report, report.contains(job.toString()))
    }

    /** The point of the reporter: `UncaughtExceptionsBeforeTest` carries neither of these. */
    @Test
    fun `the report carries the cause chain and everything suppressed`() {
        val exception =
            IllegalStateException("outer", IllegalArgumentException("inner")).apply {
                addSuppressed(UnsupportedOperationException("alongside"))
            }

        val report = describeLeak("Test worker", CoroutineName("x"), exception)

        assertTrue(report, report.contains("outer"))
        assertTrue(report, report.contains("Caused by"))
        assertTrue(report, report.contains("inner"))
        assertTrue(report, report.contains("Suppressed"))
        assertTrue(report, report.contains("alongside"))
    }

    /** One grep finds a whole report, however many lines the traces run to. */
    @Test
    fun `every line is marked`() {
        val report = describeLeak("Test worker", CoroutineName("x"), IllegalStateException("boom"))

        assertTrue(report.lineSequence().filter { it.isNotEmpty() }.all { it.startsWith(LEAK_MARKER) })
    }

    @Test
    fun `an unnamed coroutine still reports`() {
        val report = describeLeak("Test worker", Job(), IllegalStateException("boom"))

        assertTrue(report, report.contains("an unnamed coroutine"))
    }

    /**
     * `kotlinx-coroutines-test` registers its own collector as this same service, and
     * `handleUncaughtCoroutineException` stops at the first handler that consumes one — the
     * collector does, by throwing `ExceptionSuccessfullyProcessed`. So the reporter only ever sees
     * a leak while it is asked first, and nothing declares that: the order is the classpath's, this
     * module's own resources ahead of its dependencies'. This is what says so, rather than the
     * reporter going quiet the day that changes.
     */
    @Test
    fun `the reporter is the first handler the service loader yields`() {
        val handlers =
            ServiceLoader
                .load(CoroutineExceptionHandler::class.java, CoroutineExceptionHandler::class.java.classLoader)
                .toList()

        assertTrue("no handler is registered at all", handlers.isNotEmpty())
        assertTrue("the first handler was ${handlers.first()}", handlers.first() is CoroutineLeakReporter)
    }
}
