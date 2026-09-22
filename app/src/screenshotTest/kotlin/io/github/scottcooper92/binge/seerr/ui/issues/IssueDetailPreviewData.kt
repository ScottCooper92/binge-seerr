package io.github.scottcooper92.binge.seerr.ui.issues

import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType

private const val ISSUE_ID = 41
private const val TMDB_ID = 1396
private const val REPORT_ID = 100
private const val COMMENT_ID = 101
private const val REPORTER_ID = 7
private const val MANAGER_ID = 9
private const val CREATED_AT_MILLIS = 1_759_000_000_000L
private const val COMMENT_AT_MILLIS = 1_759_100_000_000L

/** A manager's own page: open, with the pinned composer/resolve bar, a report and a reply. */
internal fun openIssueWithActionsDetail(): IssueDetail =
    IssueDetail(
        item = issueItem(status = IssueStatus.Open),
        report =
            IssueComment(
                id = REPORT_ID,
                author = "Ada",
                authorId = REPORTER_ID,
                isAdmin = false,
                message = "The video freezes around the 12 minute mark, every time.",
                createdAtMillis = CREATED_AT_MILLIS,
                isMine = false,
            ),
        comments =
            listOf(
                IssueComment(
                    id = COMMENT_ID,
                    author = "Grace",
                    authorId = MANAGER_ID,
                    isAdmin = true,
                    message = "Can confirm on Chrome and Safari both. Looking into it.",
                    createdAtMillis = COMMENT_AT_MILLIS,
                    isMine = true,
                ),
            ),
        canComment = true,
        canManage = true,
        canResolve = true,
        canDelete = true,
        webUrl = "https://seerr.example/tv/1396",
        mediaServerUrl = "https://jellyfin.example/web/#/details?id=a1",
        serviceUrl = "https://sonarr.example/series/breaking-bad",
        currentUserName = "Grace",
    )

/** A resolved issue for a viewer with nothing to do here — no manage permission, not the reporter. */
internal fun resolvedIssueReadOnlyDetail(): IssueDetail =
    IssueDetail(
        item = issueItem(status = IssueStatus.Resolved),
        report =
            IssueComment(
                id = REPORT_ID,
                author = "Ada",
                authorId = REPORTER_ID,
                isAdmin = false,
                message = "Subtitles are out of sync by about two seconds.",
                createdAtMillis = CREATED_AT_MILLIS,
                isMine = false,
            ),
        comments = emptyList(),
        canComment = false,
        canManage = false,
        canResolve = false,
        canDelete = false,
        webUrl = "https://seerr.example/tv/1396",
        mediaServerUrl = null,
        serviceUrl = null,
        currentUserName = null,
    )

private fun issueItem(status: IssueStatus): IssueItem =
    IssueItem(
        id = ISSUE_ID,
        tmdbId = TMDB_ID,
        mediaType = RequestMediaType.Tv,
        title = "Breaking Bad",
        posterUrl = null,
        year = "2008",
        type = IssueType.Video,
        status = status,
        reportedBy = "Ada",
        reportedById = REPORTER_ID,
        commentCount = 1,
        createdAtMillis = CREATED_AT_MILLIS,
        updatedAtMillis = CREATED_AT_MILLIS,
        problem = "Video freezes around 12 minutes in",
        problemSeason = 1,
        problemEpisode = 3,
    )
