package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorIssueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideRuleValidationTest {
    private val complete = OverrideRuleForm(serviceType = ServiceType.Radarr, serviceId = 1)

    @Test
    fun `a rule on an instance has no issue, with or without conditions and overrides`() {
        assertTrue(complete.issues().isEmpty())
        assertTrue(complete.copy(userIds = setOf(1), genres = "16", profileId = 4).issues().isEmpty())
    }

    @Test
    fun `a rule with no instance is missing its instance`() {
        val issue = OverrideRuleForm().issues().single()

        assertEquals(OverrideRuleSections.INSTANCE, issue.section)
        assertEquals(OverrideRuleFields.INSTANCE, issue.field)
        assertEquals(EditorIssueKind.Missing, issue.kind)
    }

    @Test
    fun `no issues exactly when the form is valid`() {
        val drafts =
            listOf(
                complete,
                OverrideRuleForm(),
                OverrideRuleForm(serviceId = 1),
                OverrideRuleForm(serviceType = ServiceType.Sonarr),
                complete.copy(genres = "x", keywords = "y"),
            )

        drafts.forEach { draft ->
            assertEquals("$draft", draft.valid, draft.issues().isEmpty())
        }
    }
}
