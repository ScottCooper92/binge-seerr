package com.cooper.binge.companion.seerr.ui.tv.issues

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import com.cooper.binge.companion.seerr.preview.SeerrTvScreenPreviews
import com.cooper.binge.companion.seerr.ui.FIXED_NOW_MILLIS
import com.cooper.binge.companion.seerr.ui.issues.IssueCounts
import com.cooper.binge.companion.seerr.ui.issues.IssueFilter
import com.cooper.binge.companion.seerr.ui.issues.IssueItem
import com.cooper.binge.companion.seerr.ui.issues.IssueStatus
import com.cooper.binge.companion.seerr.ui.tv.TvLoadPhase
import com.cooper.binge.companion.seerr.ui.tv.TvPagedRows
import com.cooper.binge.companion.seerr.ui.tv.issue
import com.cooper.binge.companion.seerr.ui.tv.rows

private val GridIssues =
    listOf("Severance", "Slow Horses", "Heat", "The Bear", "Arrival", "Andor").mapIndexed { index, title ->
        issue(index + 11, title, IssueStatus.Open, now = FIXED_NOW_MILLIS)
    }

private val GridCounts = IssueCounts(total = 24, open = 21, resolved = 3)

/** The see-all grid behind an issue row: every issue of one filter as a paged grid, and its empty, loading and failed pages (#1053). */
class TvIssuesGridScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Rows() = GridFrame(rows(GridIssues))

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Empty() = GridFrame(rows(emptyList()))

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Loading() = GridFrame(TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Loading))

    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Failed() = GridFrame(TvPagedRows(count = 0, at = { null }, refresh = TvLoadPhase.Failed))
}

@Composable
private fun GridFrame(rows: TvPagedRows<IssueItem>) =
    TvIssuesGrid(
        filter = IssueFilter.Open,
        counts = GridCounts,
        rows = rows,
        detailOpen = false,
        onOpenDetail = {},
        onRetryLoad = {},
        onBack = {},
        now = FIXED_NOW_MILLIS,
    )
