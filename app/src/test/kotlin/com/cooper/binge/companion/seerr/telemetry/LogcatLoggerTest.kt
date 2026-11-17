package com.cooper.binge.companion.seerr.telemetry

import com.cooper.homelab.core.diagnostics.Redactor
import com.cooper.homelab.core.diagnostics.RingLog
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.foundation.LogField
import com.cooper.homelab.core.foundation.Logger
import com.cooper.homelab.core.foundation.ServerFailure
import com.cooper.homelab.core.foundation.error
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/** What reaches Logcat: the constant event, the fields' kept values, and never an exception's message. */
class LogcatLoggerTest {
    @Test
    fun `an entry is the event followed by each field`() {
        val line =
            logcatLine(
                "analytics backend failed to react to consent",
                listOf(LogField.Flag("granted", true), LogField.Status("status", 503), LogField.Count("tries", 2)),
                null,
            )

        assertEquals("analytics backend failed to react to consent granted=true status=503 tries=2", line)
    }

    @Test
    fun `a throwable is its class name and never its message`() {
        val line = logcatLine("event", emptyList(), IllegalStateException("http://secret.example/?key=abc"))

        assertEquals("event error=java.lang.IllegalStateException", line)
        assertFalse(line.contains("secret"))
    }

    @Test
    fun `a sealed case is its simple class name and a path is only its path`() {
        val line =
            logcatLine(
                "event",
                listOf(LogField.Case("failure", ServerFailure.NotFound), LogField.Path("https://host.example:5055/api/v1/user?token=x")),
                null,
            )

        assertEquals("event failure=NotFound path=/api/v1/user", line)
    }

    @Test
    fun `a numeric path segment is an id and a fragment is dropped`() {
        val line = logcatLine("event", listOf(LogField.Path("https://host.example/api/v1/user/42/settings/main?a=1#frag")), null)

        assertEquals("event path=/api/v1/user/{id}/settings/main", line)
    }

    @Test
    fun `through the ring, the kept error text reaches the Logcat line and the message never does`() {
        val lines = mutableListOf<String>()
        val ring =
            RingLog(
                clock = Clock { Instant.fromEpochMilliseconds(0) },
                redactor = Redactor(),
                serverOrder = { emptyList() },
                ownPackages = listOf("com.cooper.binge."),
                forward = Logger { _, _, event, fields, error -> lines += logcatLine(event, fields, error) },
            )

        ring.error("tag", "the notification poll threw and will retry", error = IllegalStateException("http://secret.example/?key=abc"))

        val line = lines.single()
        assertTrue(line, line.startsWith("the notification poll threw and will retry error=java.lang.IllegalStateException"))
        assertFalse(line.contains("secret"))
    }
}
