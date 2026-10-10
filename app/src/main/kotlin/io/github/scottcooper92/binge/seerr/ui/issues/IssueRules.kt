package io.github.scottcooper92.binge.seerr.ui.issues

import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions

/**
 * Whether the server would let this user delete an issue. A manager may delete any. A reporter may delete their own
 * only while nobody has replied: the report is the first comment, and Seerr refuses a non-manager's delete once there
 * is a second (`server/routes/issue.ts`, #1010). One rule for the detail page, the TV sheet and the list's delete, so
 * they cannot drift apart (#1151).
 */
internal fun mayDeleteIssue(
    permissions: SeerrPermissions,
    isReporter: Boolean,
    commentCount: Int,
): Boolean = permissions.canManageIssues || (permissions.canCreateIssues && isReporter && commentCount <= 1)

/** [mayDeleteIssue] for a row of the list; [IssueItem.commentCount] counts the report too. */
internal fun IssueItem.canBeDeleted(scope: IssueListScope): Boolean =
    mayDeleteIssue(
        permissions = scope.permissions,
        isReporter = reportedById != null && reportedById == scope.currentUserId,
        commentCount = commentCount,
    )
