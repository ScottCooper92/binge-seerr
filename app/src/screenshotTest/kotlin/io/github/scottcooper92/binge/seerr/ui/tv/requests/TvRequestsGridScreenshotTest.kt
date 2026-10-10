package io.github.scottcooper92.binge.seerr.ui.tv.requests

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import io.github.scottcooper92.binge.seerr.preview.SeerrTvScreenPreviews
import io.github.scottcooper92.binge.seerr.seerr.SeerrRequestStatusCode
import io.github.scottcooper92.binge.seerr.ui.FIXED_NOW_MILLIS
import io.github.scottcooper92.binge.seerr.ui.requests.RequestCounts
import io.github.scottcooper92.binge.seerr.ui.requests.RequestFilter
import io.github.scottcooper92.binge.seerr.ui.requests.RequestItem
import io.github.scottcooper92.binge.seerr.ui.tv.TvLoadPhase
import io.github.scottcooper92.binge.seerr.ui.tv.TvPagedRows
import io.github.scottcooper92.binge.seerr.ui.tv.request
import io.github.scottcooper92.binge.seerr.ui.tv.rows

private val GridRequests =
    listOf("Heat", "The Bear", "Dune: Part Two", "Severance", "Slow Horses", "Arrival", "Past Lives", "Andor").mapIndexed { index, title ->
        request(index + 1, title, SeerrRequestStatusCode.Pending, now = FIXED_NOW_MILLIS)
    }

private val GridCounts = RequestCounts(total = 57, pending = 31, approved = 20, processing = 4, available = 2)

/**
 * The see-all grid behind a request row: every request of one filter as a paged grid, and its empty, loading and failed
 * pages. The row's own See-all tile is framed with the board (#1053).
 */
class TvRequestsGridScreenshotTest {
    @PreviewTest
    @SeerrTvScreenPreviews
    @Composable
    fun Rows() = GridFrame(rows(GridRequests))

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
private fun GridFrame(rows: TvPagedRows<RequestItem>) =
    TvRequestsGrid(
        filter = RequestFilter.Pending,
        counts = GridCounts,
        rows = rows,
        detailOpen = false,
        onOpenDetail = {},
        onRetryLoad = {},
        onBack = {},
        now = FIXED_NOW_MILLIS,
    )
