package com.cooper.binge.companion.seerr.ui.tv.issues

import com.cooper.binge.companion.seerr.ui.issues.IssueComment
import com.cooper.binge.companion.seerr.ui.issues.IssueDetail
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType

private const val HOUR_MILLIS = 3_600_000L

/** An issue as the sheet shows it: a report, a reply from someone else and one of the user's own, all theirs to act on. */
internal fun sampleIssueSheetState(now: Long = System.currentTimeMillis()): IssueDetailUiState.Ready =
    IssueDetailUiState.Ready(
        detail =
            IssueDetail(
                item =
                    IssueItem(
                        id = 41,
                        tmdbId = 1396,
                        mediaType = RequestMediaType.Tv,
                        title = "Severance",
                        posterUrl = null,
                        year = "2022",
                        type = IssueType.Subtitles,
                        status = IssueStatus.Open,
                        reportedBy = "Ana",
                        reportedById = 3,
                        commentCount = 2,
                        createdAtMillis = now - 2 * HOUR_MILLIS,
                        updatedAtMillis = null,
                        problem = null,
                        problemSeason = 2,
                        problemEpisode = 4,
                    ),
                report =
                    IssueComment(
                        id = 100,
                        author = "Ana",
                        authorId = 3,
                        isAdmin = false,
                        message =
                            "The English subtitles drift out of sync about ten minutes in, and by the end of the episode " +
                                "they run a good five seconds behind the dialogue.",
                        createdAtMillis = now - 2 * HOUR_MILLIS,
                        isMine = false,
                    ),
                comments =
                    listOf(
                        IssueComment(
                            id = 101,
                            author = "Grace",
                            authorId = 9,
                            isAdmin = true,
                            message = "Thanks, I can see it too. Looking for a better subtitle file now.",
                            createdAtMillis = now - HOUR_MILLIS,
                            isMine = false,
                        ),
                        IssueComment(
                            id = 102,
                            author = "Scott",
                            authorId = 7,
                            isAdmin = true,
                            message = "Swapped the subtitles for a synced set. Let me know if it still drifts.",
                            createdAtMillis = now - HOUR_MILLIS / 2,
                            isMine = true,
                        ),
                    ),
                canComment = true,
                canManage = true,
                canResolve = true,
                canDelete = true,
                webUrl = "http://seerr.lan:5055/issues/41",
                mediaServerUrl = null,
                serviceUrl = null,
                currentUserName = "Scott",
                mediaServerName = "Jellyfin",
            ),
        now = now,
    )

/** The sheet's verbs, all doing nothing, for a frame. */
internal val NoIssueSheetActions =
    TvIssueSheetActions(
        onDismiss = {},
        onRetry = {},
        onToggleStatus = {},
        onDeleteIssue = {},
        onDeleteComment = {},
        onOpenInMediaServer = {},
    )
