package io.github.scottcooper92.binge.seerr.ui.issues

import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType
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

    @Test
    fun `a row reads the reporter from the scope's user`() {
        val scope = IssueListScope(permissions = reporter, currentUserId = 3)
        val row = row(reportedById = 3, commentCount = 2)

        assertFalse(row.canBeDeleted(scope))
        assertTrue(row.copy(commentCount = 1).canBeDeleted(scope))
        assertFalse(row.copy(reportedById = null, commentCount = 1).canBeDeleted(scope))
    }

    private fun row(
        reportedById: Int?,
        commentCount: Int,
    ) = IssueItem(
        id = 1,
        tmdbId = 1,
        mediaType = RequestMediaType.Tv,
        title = null,
        posterUrl = null,
        year = null,
        type = IssueType.Subtitles,
        status = IssueStatus.Open,
        reportedBy = null,
        reportedById = reportedById,
        commentCount = commentCount,
        createdAtMillis = null,
        updatedAtMillis = null,
        problem = null,
        problemSeason = null,
        problemEpisode = null,
    )
}
