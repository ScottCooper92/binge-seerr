package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorIssueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAgentValidationTest {
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
    fun `an agent with what it needs has no issue, and one that is off has none whatever it holds`() {
        assertTrue(email.issues().isEmpty())
        assertTrue(AgentForm(ServerAgent.Email).issues().isEmpty())
    }

    @Test
    fun `an empty agent that is on is missing each required option, in the settings section`() {
        val issues = AgentForm(ServerAgent.Email, enabled = true).issues()

        assertEquals(
            listOf(AgentOption.EmailFrom, AgentOption.EmailSmtpHost, AgentOption.EmailSmtpPort).map { it.fieldId },
            issues.map { it.field },
        )
        assertTrue(issues.all { it.kind == EditorIssueKind.Missing && it.section == AgentSections.SETTINGS })
    }

    @Test
    fun `a port that is not a number is invalid`() {
        val issue = email.copy(options = email.options + (AgentOption.EmailSmtpPort to "smtp")).issues().single()

        assertEquals(EditorIssueKind.Invalid, issue.kind)
        assertEquals(R.string.editor_error_port, issue.messageRes)
    }

    @Test
    fun `required options sit in settings and the rest in more settings`() {
        AgentOption.entries.forEach { option ->
            assertEquals("$option", if (option.required) AgentSections.SETTINGS else AgentSections.MORE_SETTINGS, option.sectionId)
        }
    }

    @Test
    fun `no issues exactly when the form is valid`() {
        val drafts =
            ServerAgent.entries.flatMap { agent ->
                listOf(AgentForm(agent), AgentForm(agent, enabled = true))
            } +
                listOf(
                    email,
                    email.copy(options = email.options + (AgentOption.EmailFrom to " ")),
                    email.copy(options = email.options + (AgentOption.EmailSmtpPort to "x")),
                )

        drafts.forEach { draft -> assertEquals("$draft", draft.valid, draft.issues().isEmpty()) }
    }
}
