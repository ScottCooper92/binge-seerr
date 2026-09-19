package io.github.scottcooper92.binge.seerr.ui.tv.issues

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.issues.IssueComment
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetail
import io.github.scottcooper92.binge.seerr.ui.issues.IssueDetailUiState
import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.issues.IssueStatus
import io.github.scottcooper92.binge.seerr.ui.requests.IssueType
import io.github.scottcooper92.binge.seerr.ui.requests.RequestMediaType

/**
 * A fixed, far-in-the-past render instant, exactly as `TvIssuesScreenshotTest` anchors its rows: a
 * relative-date string that drifted a day stale on every CI run after the day it was recorded.
 */
private const val NOW_MILLIS = 1_770_000_000_000L
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
        createdAtMillis = NOW_MILLIS - 2 * DAY_MILLIS,
        updatedAtMillis = NOW_MILLIS - DAY_MILLIS,
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
    createdAtMillis = NOW_MILLIS - daysAgo * DAY_MILLIS,
    isMine = false,
)

private val ShortThread =
    listOf(
        comment(101, "ana", isAdmin = false, message = "Also happens on episode 5.", daysAgo = 1),
        comment(102, "mod", isAdmin = true, message = "Thanks — looking into it now.", daysAgo = 0),
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
 * The television issue detail page: an empty thread, a short one, and a long one that exercises the
 * scroll the focus test drives.
 */
class TvIssueDetailScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun EmptyThread() {
        TvIssueDetailScreen(state = IssueDetailUiState.Ready(detail(emptyList())), actions = actions(), now = NOW_MILLIS)
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun ShortThread() {
        TvIssueDetailScreen(state = IssueDetailUiState.Ready(detail(ShortThread)), actions = actions(), now = NOW_MILLIS)
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun LongThread() {
        TvIssueDetailScreen(state = IssueDetailUiState.Ready(detail(LongThread)), actions = actions(), now = NOW_MILLIS)
    }
}
