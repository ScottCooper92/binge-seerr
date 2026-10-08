package io.github.scottcooper92.binge.seerr.ui.settings.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What ties an agent's options to the port range and to the form's validity. */
class NotificationAgentModelsTest {
    private val email =
        AgentForm(
            ServerAgent.Email,
            enabled = true,
            options =
                mapOf(
                    AgentOption.EmailFrom to "seerr@home.lan",
                    AgentOption.EmailSmtpHost to "smtp.home.lan",
                    AgentOption.EmailSmtpPort to "587",
                ),
        )

    @Test
    fun `a port outside the port range is invalid, and the form will not save it`() {
        assertTrue(email.valid)
        listOf("0", "-1", "65536", "70000").forEach { port ->
            val draft = email.copy(options = email.options + (AgentOption.EmailSmtpPort to port))

            assertFalse(port, AgentOption.EmailSmtpPort.satisfiedBy(port))
            assertFalse(port, draft.valid)
        }
    }

    @Test
    fun `ntfy's priority is a number but not a port, so it is not held to the port range`() {
        assertFalse(AgentOption.NtfyPriority.port)
        assertTrue(AgentOption.NtfyPriority.satisfiedBy("70000"))
    }
}
