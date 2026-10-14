package com.cooper.binge.companion.seerr.ui.issues

import com.cooper.binge.companion.seerr.seerr.SeerrPermissions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server's delete rule, as the detail page, the TV sheet and the list's delete all read it (#1010, #1151). */
class IssueRulesTest {
    private val reporter = SeerrPermissions(canCreateIssues = true)
    private val manager = SeerrPermissions(canManageIssues = true)

    @Test
    fun `a reporter may delete their own issue only while nobody has replied`() {
        assertTrue(mayDeleteIssue(reporter, isReporter = true, commentCount = 1))
        assertFalse(mayDeleteIssue(reporter, isReporter = true, commentCount = 2))
    }

    @Test
    fun `a reporter may not delete someone else's issue`() {
        assertFalse(mayDeleteIssue(reporter, isReporter = false, commentCount = 1))
    }

    @Test
    fun `a manager may delete any issue, replied to or not`() {
        assertTrue(mayDeleteIssue(manager, isReporter = false, commentCount = 5))
    }
}
