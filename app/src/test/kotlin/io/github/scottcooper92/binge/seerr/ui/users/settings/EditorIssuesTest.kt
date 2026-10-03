package io.github.scottcooper92.binge.seerr.ui.users.settings

import io.github.scottcooper92.binge.seerr.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorIssuesTest {
    private val wrong = invalid("a", "port", R.string.editor_error_port)
    private val absent = missing("a", "name")

    @Test
    fun `an invalid value shows at once and a missing one waits for a failed save`() {
        val issues = listOf(absent, wrong)

        assertEquals(listOf(wrong), issues.visible(submitted = false))
        assertEquals(issues, issues.visible(submitted = true))
    }

    @Test
    fun `a section with something to fix is open whatever the user chose`() {
        assertTrue(sectionExpanded(userChoice = false, defaultExpanded = false, issueCount = 1))
        assertTrue(sectionExpanded(userChoice = null, defaultExpanded = false, issueCount = 2))
    }

    @Test
    fun `without an issue the user's choice beats the default`() {
        assertFalse(sectionExpanded(userChoice = false, defaultExpanded = true, issueCount = 0))
        assertTrue(sectionExpanded(userChoice = true, defaultExpanded = false, issueCount = 0))
    }

    @Test
    fun `without a choice the default decides`() {
        assertTrue(sectionExpanded(userChoice = null, defaultExpanded = true, issueCount = 0))
        assertFalse(sectionExpanded(userChoice = null, defaultExpanded = false, issueCount = 0))
    }
}
