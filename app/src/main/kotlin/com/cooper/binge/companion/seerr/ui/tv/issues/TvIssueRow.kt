package com.cooper.binge.companion.seerr.ui.tv.issues

import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssueListScope

/** Closing, reopening and deleting from the row: a manager's, or the reporter's on their own issue — the page's rule. */
internal fun IssueItem.canBeActedOn(scope: IssueListScope): Boolean =
    scope.permissions.canManageIssues ||
        (scope.permissions.canCreateIssues && reportedById != null && reportedById == scope.currentUserId)
