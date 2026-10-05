package io.github.scottcooper92.binge.seerr.ui.settings.server

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.ui.settings.ServiceType
import io.github.scottcooper92.binge.seerr.ui.users.settings.EditorIssueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverrideRuleValidationTest {
    private val onInstance = OverrideRuleForm(serviceType = ServiceType.Radarr, serviceId = 1)
    private val complete = onInstance.copy(genres = "16", profileId = 4)

    /** Each condition kind on its own, as the field holds it. */
    private val singleConditions =
        listOf(
            onInstance.copy(userIds = setOf(1)),
            onInstance.copy(genres = "16"),
            onInstance.copy(languages = "ja"),
            onInstance.copy(keywords = "210024"),
        )

    /** Each override kind on its own. */
    private val singleOverrides =
        listOf(
            onInstance.copy(profileId = 4),
            onInstance.copy(rootFolder = "/anime"),
            onInstance.copy(tagIds = setOf(2)),
        )

    @Test
    fun `a rule with an instance, a condition and an override has no issue`() {
        assertTrue(complete.issues().isEmpty())
        assertTrue(complete.copy(userIds = setOf(1), languages = "en", rootFolder = "/movies", tagIds = setOf(1)).issues().isEmpty())
    }

    @Test
    fun `any one condition kind is enough`() {
        singleConditions.forEach { draft ->
            assertTrue("$draft", draft.copy(profileId = 4).issues().isEmpty())
        }
    }

    @Test
    fun `any one override kind is enough`() {
        singleOverrides.forEach { draft ->
            assertTrue("$draft", draft.copy(genres = "16").issues().isEmpty())
        }
    }

    @Test
    fun `a rule with no instance is missing its instance`() {
        val issue = complete.copy(serviceType = null, serviceId = null).issues().single()

        assertEquals(OverrideRuleSections.INSTANCE, issue.section)
        assertEquals(OverrideRuleFields.INSTANCE, issue.field)
        assertEquals(EditorIssueKind.Missing, issue.kind)
    }

    @Test
    fun `a rule with no condition says so on the conditions section`() {
        val issue = complete.copy(genres = "").issues().single()

        assertEquals(OverrideRuleSections.CONDITIONS, issue.section)
        assertEquals(OverrideRuleFields.CONDITIONS, issue.field)
        assertEquals(EditorIssueKind.Missing, issue.kind)
        assertEquals(R.string.server_settings_rule_needs_condition, issue.messageRes)
    }

    @Test
    fun `a condition the server would never be sent does not count`() {
        val issue = complete.copy(genres = " , abc").issues().single()

        assertEquals(OverrideRuleSections.CONDITIONS, issue.section)
    }

    @Test
    fun `a rule with no override says so on the overrides section`() {
        val issue = complete.copy(profileId = null).issues().single()

        assertEquals(OverrideRuleSections.OVERRIDES, issue.section)
        assertEquals(OverrideRuleFields.OVERRIDES, issue.field)
        assertEquals(EditorIssueKind.Missing, issue.kind)
        assertEquals(R.string.server_settings_rule_needs_override, issue.messageRes)
    }

    @Test
    fun `a rule with neither lists the conditions first, in the order the sections read`() {
        assertEquals(
            listOf(OverrideRuleSections.CONDITIONS, OverrideRuleSections.OVERRIDES),
            onInstance.issues().map { it.section },
        )
        assertEquals(
            listOf(OverrideRuleSections.INSTANCE, OverrideRuleSections.CONDITIONS, OverrideRuleSections.OVERRIDES),
            OverrideRuleForm().issues().map { it.section },
        )
    }

    @Test
    fun `no issues exactly when the form is valid`() {
        val drafts =
            listOf(
                complete,
                onInstance,
                OverrideRuleForm(),
                OverrideRuleForm(serviceId = 1),
                OverrideRuleForm(serviceType = ServiceType.Sonarr),
                complete.copy(genres = ""),
                complete.copy(profileId = null),
                complete.copy(genres = " , abc"),
                complete.copy(serviceId = null),
            ) + singleConditions + singleOverrides +
                singleConditions.map { it.copy(tagIds = setOf(1)) } +
                singleOverrides.map { it.copy(keywords = "9") }

        drafts.forEach { draft ->
            assertEquals("$draft", draft.valid, draft.issues().isEmpty())
        }
    }
}
