package io.github.scottcooper92.binge.seerr.ui.issues

import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType

/** No-op actions for a test that renders [IssueDetailScreen] and drives nothing on it. */
internal fun noIssueDetailActions(): IssueDetailActions =
    IssueDetailActions(
        onBack = {},
        onRetry = {},
        onDraftChange = {},
        onPostComment = {},
        onRetryOutbox = {},
        onEditOutbox = { _, _ -> },
        onDropOutbox = {},
        onEditComment = { _, _ -> },
        onDeleteComment = {},
        onToggleStatus = {},
        onDeleteIssue = {},
    )

/**
 * An issue in its common shape: the report every issue opens with, one reply, and a manager who may
 * comment and resolve, so the pinned bar shows.
 */
internal fun commonIssueDetail(): IssueDetail =
    IssueDetail(
        item =
            IssueItem(
                id = 41,
                tmdbId = 1396,
                mediaType = RequestMediaType.Tv,
                title = "Breaking Bad",
                posterUrl = null,
                year = "2008",
                type = IssueType.Video,
                status = IssueStatus.Open,
                reportedBy = "Ada",
                reportedById = 7,
                commentCount = 1,
                createdAtMillis = null,
                updatedAtMillis = null,
                problem = "Video freezes around 12 minutes in",
                problemSeason = 1,
                problemEpisode = 3,
            ),
        report =
            IssueComment(
                id = 100,
                author = "Ada",
                authorId = 7,
                isAdmin = false,
                message = "The video freezes around the 12 minute mark, every time.",
                createdAtMillis = null,
                isMine = false,
            ),
        comments =
            listOf(
                IssueComment(
                    id = 101,
                    author = "Grace",
                    authorId = 9,
                    isAdmin = true,
                    message = "Can confirm. Looking into it.",
                    createdAtMillis = null,
                    isMine = true,
                ),
            ),
        canComment = true,
        canManage = true,
        canResolve = true,
        canDelete = true,
        webUrl = "https://seerr.example/issues/41",
        mediaServerUrl = null,
        serviceUrl = null,
        currentUserName = "Grace",
    )
