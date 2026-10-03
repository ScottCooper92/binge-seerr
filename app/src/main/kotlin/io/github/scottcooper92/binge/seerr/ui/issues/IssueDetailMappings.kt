package io.github.scottcooper92.binge.seerr.ui.issues

import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueCommentDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrIssueDto
import io.github.scottcooper92.binge.seerr.seerr.SeerrPermissions
import io.github.scottcooper92.binge.seerr.seerr.SeerrServerProfile
import io.github.scottcooper92.binge.seerr.seerr.SeerrUserDto
import io.github.scottcooper92.binge.seerr.seerr.displayString
import io.github.scottcooper92.binge.seerr.seerr.isWebUrl
import io.github.scottcooper92.binge.seerr.seerr.toEpochMillisOrNull
import io.github.scottcooper92.binge.seerr.seerr.toPermissions
import io.github.scottcooper92.binge.seerr.ui.requests.mediaServerName

internal const val STATUS_OPEN = "open"
internal const val STATUS_RESOLVED = "resolved"

internal fun SeerrIssueDto.toDetail(
    item: IssueItem,
    user: SeerrUserDto?,
    baseUrl: String,
    profile: SeerrServerProfile,
): IssueDetail {
    val permissions = user.toPermissions()
    val comments = comments.sortedBy { it.id }.map { it.toIssueComment(user?.id) }
    val isReporter = user != null && createdBy?.id == user.id
    return IssueDetail(
        item = item,
        report = comments.firstOrNull(),
        comments = comments.drop(1),
        canComment = permissions.canManageIssues || (permissions.canCreateIssues && isReporter),
        canManage = permissions.canManageIssues,
        canResolve = permissions.canManageIssues || (permissions.canCreateIssues && isReporter),
        canDelete = permissions.canManageIssues || (permissions.canCreateIssues && isReporter),
        webUrl = baseUrl + "issues/" + id,
        mediaServerUrl = media?.mediaUrl?.takeIf { it.isWebUrl() },
        serviceUrl = media?.serviceUrl?.takeIf { it.isWebUrl() },
        serverName = profile.variant.displayName,
        mediaServerName = profile.mediaServerName(),
        currentUserName = user?.displayString(),
    )
}

internal fun SeerrIssueCommentDto.toIssueComment(currentUserId: Int?): IssueComment =
    IssueComment(
        id = id,
        author = user?.displayString(),
        authorId = user?.id,
        isAdmin = SeerrPermissions.fromBits(user?.permissions).isAdmin,
        message = message.orEmpty(),
        createdAtMillis = createdAt?.toEpochMillisOrNull(),
        isMine = user?.id != null && user.id == currentUserId,
    )
