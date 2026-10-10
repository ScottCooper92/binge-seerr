package io.github.scottcooper92.binge.seerr.ui.tv.issues

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.ui.FIXED_NOW_MILLIS
import io.github.scottcooper92.binge.seerr.ui.issues.IssueCounts
import io.github.scottcooper92.binge.seerr.ui.issues.IssueFilter
import io.github.scottcooper92.binge.seerr.ui.issues.IssueItem
import io.github.scottcooper92.binge.seerr.ui.issues.IssueStatus
import io.github.scottcooper92.binge.seerr.ui.tv.TvLoadPhase
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.issue
import io.github.scottcooper92.binge.seerr.ui.tv.rows

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
        actingIds = emptySet(),
        management =
            TvIssuesGridManagement(
                scope = null,
                actionItem = null,
                onOpenActions = {},
                onDismissActions = {},
                onResolve = {},
                onReopen = {},
                onDelete = {},
            ),
        detailOpen = false,
        onOpenDetail = {},
        onRetryLoad = {},
        onBack = {},
        now = FIXED_NOW_MILLIS,
    )
