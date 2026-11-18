package com.cooper.binge.companion.seerr.di

import com.cooper.homelab.core.diagnostics.Redactor
import com.cooper.homelab.core.diagnostics.RingLog
import com.cooper.homelab.core.foundation.Clock
import com.cooper.homelab.core.foundation.error
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class LoggingModuleTest {
    private val ring =
        RingLog(
            Clock { Instant.fromEpochMilliseconds(0) },
            Redactor(),
            serverOrder = { emptyList() },
            ownPackages = OWN_PACKAGES,
        )

    private fun errorWithFrames(vararg frames: String): Throwable =
        IllegalStateException("a message that is never kept").apply {
            stackTrace = frames.map { StackTraceElement(it, "run", "Source.kt", 1) }.toTypedArray()
        }

    @Test
    fun `a frame of the shared library is kept beside the app's own and a foreign frame is not`() {
        val thrown =
            errorWithFrames(
                "com.cooper.homelab.core.Connection",
                "com.cooper.binge.companion.seerr.Foo",
                "okhttp3.Call",
            )

        ring.error("tag", "the call threw", error = thrown)

        val text = ring.render()
        assertTrue(text, text.contains("com.cooper.homelab.core.Connection.run"))
        assertTrue(text, text.contains("com.cooper.binge.companion.seerr.Foo.run"))
        assertFalse(text, text.contains("okhttp3"))
        assertFalse(text, text.contains("never kept"))
    }

    @Test
    fun `the kept prefixes are exactly the app and the shared library`() {
        assertEquals(listOf("com.cooper.binge.", "com.cooper.homelab."), OWN_PACKAGES)
    }
}
