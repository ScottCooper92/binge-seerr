package com.cooper.binge.companion.seerr.telemetry

import com.cooper.homelab.core.diagnostics.LogcatLogger
import com.cooper.homelab.core.diagnostics.Redactor
import com.cooper.homelab.core.diagnostics.RingLog
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.foundation.LogField
import com.cooper.homelab.core.foundation.error
import com.cooper.homelab.core.foundation.warn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/** What the shared Logcat sink writes for this app's entries: the constant event, and nothing the entry's text could leak. */
class LogcatLoggerTest {
    private val lines = mutableListOf<String>()

    private fun ring() =
        RingLog(
            clock = Clock { Instant.fromEpochMilliseconds(0) },
            redactor = Redactor(),
            serverOrder = { emptyList() },
            ownPackages = listOf("com.cooper.binge."),
            forward = LogcatLogger { _, _, line -> lines += line },
        )

    @Test
    fun `an error reaches Logcat as its event and class, and its message never does`() {
        ring().error("tag", "the notification poll threw and will retry", error = IllegalStateException("http://secret.example/?key=abc"))

        val line = lines.single()
        assertTrue(line, line.startsWith("the notification poll threw and will retry error="))
        assertTrue(line, line.contains("IllegalStateException"))
        assertFalse(line, line.contains("secret"))
    }

    @Test
    fun `a path field keeps no host, query or id`() {
        ring().warn("tag", "request failed", listOf(LogField.Path("https://host.example:5055/api/v1/user/42/settings?token=abc#frag")))

        val line = lines.single()
        assertTrue(line, line.contains("/api/v1/user/{id}/settings"))
        assertFalse(line, line.contains("host.example"))
        assertFalse(line, line.contains("token"))
    }
}
