package io.github.scottcooper92.binge.seerr.ui.tv.issues

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.issues.IssueStatus
import io.github.scottcooper92.binge.seerr.ui.tv.NoIssuesActions
import io.github.scottcooper92.binge.seerr.ui.tv.issue
import io.github.scottcooper92.binge.seerr.ui.tv.issuesReady
import io.github.scottcooper92.binge.seerr.ui.tv.rows
import kotlinx.coroutines.flow.emptyFlow

/**
 * A fixed, far-in-the-past render instant for the rows below. `SampleIssues`' own dates are `id` days
 * before whichever instant the suite runs at, which the row renders as an absolute calendar date once
 * that gap passes the relative-date window — so the committed baseline drifted a day out of date on every
 * CI run after the day it was recorded. Anchoring both the items and the render instant here, far enough
 * apart that they always land past that window, makes the rendered date text invariant instead.
 */
private const val ISSUES_NOW_MILLIS = 1_770_000_000_000L

private val FixedSampleIssues =
    listOf(
        issue(11, "Severance", IssueStatus.Open, now = ISSUES_NOW_MILLIS),
        issue(12, "Slow Horses", IssueStatus.Resolved, now = ISSUES_NOW_MILLIS),
    )

/**
 * The television issues board: the loaded list and the actions sheet open on a row. Mirrors the states
 * already sketched in `TvBoardPreviews.kt`.
 */
class TvIssuesScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Board() {
        TvIssuesBoard(
            state = issuesReady(),
            rows = rows(FixedSampleIssues),
            events = emptyFlow(),
            actions = NoIssuesActions,
            initialFocusedRowId = 11,
            now = ISSUES_NOW_MILLIS,
        )
    }

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Sheet() {
        TvIssuesBoard(
            state = issuesReady(actionItem = FixedSampleIssues.first()),
            rows = rows(FixedSampleIssues),
            events = emptyFlow(),
            actions = NoIssuesActions,
            now = ISSUES_NOW_MILLIS,
        )
    }
}
