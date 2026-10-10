package io.github.scottcooper92.binge.seerr.ui.tv.issues

import io.github.scottcooper92.binge.seerr.R
import io.github.scottcooper92.binge.seerr.seerr.SeerrError
import io.github.scottcooper92.binge.seerr.ui.issues.IssueListEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/** The line the TV issues board shows for an action's outcome (#1054). */
class IssueListEventMessageTest {
    @Test
    fun `each outcome has its own line`() {
        assertEquals(R.string.issue_resolved, IssueListEvent.Resolved.messageRes())
        assertEquals(R.string.issue_reopened, IssueListEvent.Reopened.messageRes())
        assertEquals(R.string.tv_issue_deleted, IssueListEvent.Deleted.messageRes())
    }

    @Test
    fun `a rejected session says so, and any other failure says the action failed`() {
        assertEquals(R.string.hub_unauthorized_body, IssueListEvent.Failed(SeerrError.Unauthorized).messageRes())
        assertEquals(R.string.tv_issue_action_failed, IssueListEvent.Failed(SeerrError.Forbidden).messageRes())
        assertEquals(R.string.tv_issue_action_failed, IssueListEvent.Failed(SeerrError.NotFound).messageRes())
    }
}
