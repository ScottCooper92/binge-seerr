package io.github.scottcooper92.binge.seerr.ui.tv.issues

import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.issues.IssueListScope

/** Closing, reopening and deleting from the row: a manager's, or the reporter's on their own issue — the page's rule. */
internal fun IssueItem.canBeActedOn(scope: IssueListScope): Boolean =
    scope.permissions.canManageIssues ||
        (scope.permissions.canCreateIssues && reportedById != null && reportedById == scope.currentUserId)
