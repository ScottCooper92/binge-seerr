package io.github.scottcooper92.binge.seerr.ui.tv.issues

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.FIXED_NOW_MILLIS
import io.github.scottcooper92.binge.seerr.ui.issues.IssueFilter
import io.github.scottcooper92.binge.seerr.ui.issues.IssueStatus
import io.github.scottcooper92.binge.seerr.ui.tv.NoIssuesActions
import io.github.scottcooper92.binge.seerr.ui.tv.TvLoadPhase
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.issue
import io.github.scottcooper92.binge.seerr.ui.tv.issuesReady
import io.github.scottcooper92.binge.seerr.ui.tv.rows
import kotlinx.coroutines.flow.emptyFlow

private val FixedSampleIssues =
    listOf(
        issue(11, "Severance", IssueStatus.Open, now = FIXED_NOW_MILLIS),
        issue(12, "Slow Horses", IssueStatus.Resolved, now = FIXED_NOW_MILLIS),
    )

/**
 * The television issues board as the design system's immersive hub: the open and resolved issues as rows over the
 * backdrop, the actions sheet open on a card, the empty arm and the failed-load page. Mirrors the states sketched
 * in `TvBoardPreviewData.kt`.
 */
class TvIssuesScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Board() {
        TvIssuesBoard(
            state = issuesReady(),
            rowsFor = { filter ->
                when (filter) {
                    IssueFilter.Open -> rows(listOf(FixedSampleIssues[0]))
                    IssueFilter.Resolved -> rows(listOf(FixedSampleIssues[1]))
                    else -> rows(emptyList())
                }
            },
            events = emptyFlow(),
            actions = NoIssuesActions,
            now = FIXED_NOW_MILLIS,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Sheet() {
        TvIssuesBoard(
            state = issuesReady(actionItem = FixedSampleIssues.first()),
            rowsFor = { filter -> if (filter == IssueFilter.Open) rows(listOf(FixedSampleIssues[0])) else rows(emptyList()) },
            events = emptyFlow(),
            actions = NoIssuesActions,
            now = FIXED_NOW_MILLIS,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Empty() {
        TvIssuesBoard(state = issuesReady(), rowsFor = { rows(emptyList()) }, events = emptyFlow(), actions = NoIssuesActions)
    }

    /** A load that failed, a rejected one included: the page says so, with a retry. */
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun FailedLoad() {
        TvIssuesBoard(
            state = issuesReady(),
            rowsFor = { TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Failed) },
            events = emptyFlow(),
            actions = NoIssuesActions,
        )
    }
}
