package com.cooper.binge.companion.seerr.ui.issues

import com.cooper.binge.companion.seerr.seerr.SeerrPermissions

/**
 * Whether the server would let this user delete an issue. A manager may delete any. A reporter may delete their own
 * only while nobody has replied: the report is the first comment, and Seerr refuses a non-manager's delete once there
 * is a second (`server/routes/issue.ts`, #1010). One rule for the phone's detail page and the TV issue sheet, so they
 * cannot drift apart (#1151).
 */
internal fun mayDeleteIssue(
    permissions: SeerrPermissions,
    isReporter: Boolean,
    commentCount: Int,
): Boolean = permissions.canManageIssues || (permissions.canCreateIssues && isReporter && commentCount <= 1)
