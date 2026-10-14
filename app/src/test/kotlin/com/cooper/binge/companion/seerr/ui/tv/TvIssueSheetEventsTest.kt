package com.cooper.binge.companion.seerr.ui.tv

import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which of the issue sheet's answers move the issue between the board's rows, so the board re-reads its counts. */
class TvIssueSheetEventsTest {
    @Test
    fun `a resolve, a reopen and a delete change the issue`() {
        assertTrue(IssueDetailEvent.IssueResolved.changesTheIssue())
        assertTrue(IssueDetailEvent.IssueReopened.changesTheIssue())
        assertTrue(IssueDetailEvent.IssueDeleted.changesTheIssue())
    }

    @Test
    fun `a deleted comment or a failure leaves it where it was`() {
        assertFalse(IssueDetailEvent.CommentDeleted.changesTheIssue())
        assertFalse(IssueDetailEvent.Failed(SeerrError.Unreachable).changesTheIssue())
    }
}
