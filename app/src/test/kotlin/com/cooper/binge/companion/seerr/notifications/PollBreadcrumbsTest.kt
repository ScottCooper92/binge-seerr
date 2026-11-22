package com.cooper.binge.companion.seerr.notifications

import com.cooper.homelab.core.telemetry.CrashBreadcrumbs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CancellationException

/** What the poll's scheduling reports to crash reporting: the app's own failures, by class name, and nothing from the device. */
class PollBreadcrumbsTest {
    private val failures = mutableListOf<Throwable>()

    private val breadcrumbs =
        object : CrashBreadcrumbs {
            override fun log(message: String) = Unit

            override fun key(
                name: String,
                value: String,
            ) = Unit

            override fun nonFatal(
                message: String,
                cause: Throwable,
            ) {
                failures += cause
            }
        }.forPoll()

    @Test
    fun `an illegal argument is ours, and is reported with its class name and never its message`() {
        breadcrumbs.nonFatal("notification poll scheduling failed", IllegalArgumentException("http://secret.example/?key=abc"))

        val reported = failures.single()
        assertTrue(reported.message.orEmpty(), reported.message.orEmpty().contains("IllegalArgumentException"))
        assertFalse(reported.message.orEmpty().contains("secret"))
    }

    @Test
    fun `a device without the platform method is the device's, not ours`() {
        breadcrumbs.nonFatal("notification poll scheduling failed", NoSuchMethodError("JobScheduler.forNamespace"))

        assertEquals(emptyList<Throwable>(), failures)
    }

    @Test
    fun `a cancellation is not reported`() {
        breadcrumbs.nonFatal("notification poll scheduling failed", CancellationException("scope cancelled"))

        assertEquals(emptyList<Throwable>(), failures)
    }
}
