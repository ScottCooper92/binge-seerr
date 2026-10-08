package io.github.scottcooper92.binge.seerr.ui.settings.server

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
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

    @Test
    fun `a password reaches the server as typed, while a token and a host are trimmed`() {
        val form =
            email.copy(
                options =
                    email.options +
                        mapOf(
                            AgentOption.EmailSmtpHost to " smtp.home.lan ",
                            AgentOption.EmailAuthPass to " hunter2 ",
                            AgentOption.EmailPgpPassword to "pgp pass ",
                        ),
            )
        val ntfy = AgentForm(ServerAgent.Ntfy, options = mapOf(AgentOption.NtfyPassword to " p w ", AgentOption.NtfyToken to " tk_1 "))

        val sent = form.toDto().options
        val ntfySent = ntfy.toDto().options

        assertEquals(" hunter2 ", sent.getValue("authPass").jsonPrimitive.content)
        assertEquals("pgp pass ", sent.getValue("pgpPassword").jsonPrimitive.content)
        assertEquals("smtp.home.lan", sent.getValue("smtpHost").jsonPrimitive.content)
        assertEquals(" p w ", ntfySent.getValue("password").jsonPrimitive.content)
        assertEquals("tk_1", ntfySent.getValue("token").jsonPrimitive.content)
    }
}
