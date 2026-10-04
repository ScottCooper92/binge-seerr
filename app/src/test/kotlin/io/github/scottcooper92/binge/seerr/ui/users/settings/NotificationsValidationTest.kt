package io.github.scottcooper92.binge.seerr.ui.users.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsValidationTest {
    private fun withDiscordId(id: String) =
        NotificationSettings().update(NotificationAgent.Discord) { it.copy(fields = mapOf(AgentField.DiscordId to id)) }

    @Test
    fun `a blank or numeric discord id has no issue`() {
        assertTrue(withDiscordId("").issues().isEmpty())
        assertTrue(withDiscordId("123456789012345678").issues().isEmpty())
    }

    @Test
    fun `a pasted username is an invalid discord id in the discord section`() {
        val issue = withDiscordId("ann#1234").issues().single()

        assertEquals(NotificationAgent.Discord.sectionId, issue.section)
        assertEquals(AgentField.DiscordId.fieldId, issue.field)
        assertEquals(EditorIssueKind.Invalid, issue.kind)
    }

    @Test
    fun `no issues exactly when the form is valid`() {
        listOf("", " ", "123", "abc", "12 34", "ann#1234").map(::withDiscordId).forEach { draft ->
            assertEquals("$draft", draft.valid, draft.issues().isEmpty())
        }
    }
}
