package com.cooper.binge.companion.seerr.ui.tv.issues

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.seerr.SeerrError
import com.cooper.binge.companion.seerr.ui.FIXED_NOW_MILLIS
import com.cooper.binge.companion.seerr.ui.issues.IssueComment
import com.cooper.binge.companion.seerr.ui.issues.IssueDetail
import com.cooper.binge.companion.seerr.ui.issues.IssueDetailUiState
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.requests.IssueType
import com.cooper.binge.companion.seerr.ui.requests.RequestMediaType

private const val DAY_MILLIS = 24 * 3_600_000L

private val Item =
    IssueItem(
        id = 11,
        tmdbId = 1396,
        mediaType = RequestMediaType.Tv,
        title = "Severance",
        posterUrl = null,
        year = "2022",
        type = IssueType.Subtitles,
        status = IssueStatus.Open,
        reportedBy = "ana",
        reportedById = 3,
        commentCount = 2,
        createdAtMillis = FIXED_NOW_MILLIS - 2 * DAY_MILLIS,
        updatedAtMillis = FIXED_NOW_MILLIS - DAY_MILLIS,
        problem = "The subtitles run about two seconds late from episode 4 onward.",
        problemSeason = 1,
        problemEpisode = 4,
    )

private fun comment(
    id: Int,
    author: String,
    isAdmin: Boolean,
    message: String,
    daysAgo: Int,
) = IssueComment(
    id = id,
    author = author,
    authorId = id,
    isAdmin = isAdmin,
    message = message,
    createdAtMillis = FIXED_NOW_MILLIS - daysAgo * DAY_MILLIS,
    isMine = false,
)

private val ShortThread =
    listOf(
        comment(101, "ana", isAdmin = false, message = "Also happens on episode 5.", daysAgo = 1),
        comment(102, "mod", isAdmin = true, message = "Thanks — looking into it now.", daysAgo = 0),
        // User 1 is the server's owner, so the tag reads Owner rather than Admin (#1236).
        comment(1, "scott", isAdmin = true, message = "Fixed in the next release.", daysAgo = 0),
    )

private val LongThread =
    (1..12).map { index ->
        comment(
            id = 200 + index,
            author = if (index % 3 == 0) "mod" else "ana",
            isAdmin = index % 3 == 0,
            message = "Update $index: still checking the subtitle timing on this release.",
            daysAgo = 12 - index,
        )
    }

private fun detail(comments: List<IssueComment>) =
    IssueDetail(
        item = Item,
        report = comment(100, "ana", isAdmin = false, message = Item.problem.orEmpty(), daysAgo = 2),
        comments = comments,
        canComment = true,
        canManage = true,
        canResolve = true,
        canDelete = true,
        webUrl = "https://seerr.example/issues/11",
        mediaServerUrl = null,
        serviceUrl = null,
        currentUserName = "ana",
    )

private fun actions() = TvIssueDetailActions(onBack = {}, onRetry = {})

/**
 * The television issue detail page: an empty thread, a short one with an admin's and the owner's replies tagged by
 * role, and a long one that exercises the scroll the focus test drives; and the pages before it: loading and failed.
 */
class TvIssueDetailScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun EmptyThread() {
        TvIssueDetailScreen(state = IssueDetailUiState.Ready(detail(emptyList())), actions = actions(), now = FIXED_NOW_MILLIS)
    }

    /** Arrival on an issue nobody has commented on: the "no comments" line is the read-out the D-pad lands on. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun EmptyThreadFocused() {
        TvIssueDetailScreen(
            state = IssueDetailUiState.Ready(detail(emptyList())),
            actions = actions(),
            now = FIXED_NOW_MILLIS,
            initiallyFocused = true,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ShortThread() {
        TvIssueDetailScreen(state = IssueDetailUiState.Ready(detail(ShortThread)), actions = actions(), now = FIXED_NOW_MILLIS)
    }

    /** Arrival on a thread: the first comment, the report's own entry, carries the focus fill. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ShortThreadFocused() {
        TvIssueDetailScreen(
            state = IssueDetailUiState.Ready(detail(ShortThread)),
            actions = actions(),
            now = FIXED_NOW_MILLIS,
            initiallyFocused = true,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun LongThread() {
        TvIssueDetailScreen(state = IssueDetailUiState.Ready(detail(LongThread)), actions = actions(), now = FIXED_NOW_MILLIS)
    }

    /**
     * The page before the issue has arrived (#1053). The tapped row standing in (`Seeded`) draws this same plate on a
     * television, and `Seeded` with an error draws [Failed]'s, so neither has a frame of its own.
     */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Loading() {
        TvIssueDetailScreen(state = IssueDetailUiState.Loading, actions = actions(), now = FIXED_NOW_MILLIS)
    }

    /** The issue could not be read: the error page, with its retry. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Failed() {
        TvIssueDetailScreen(state = IssueDetailUiState.Error(SeerrError.Unreachable), actions = actions(), now = FIXED_NOW_MILLIS)
    }
}
